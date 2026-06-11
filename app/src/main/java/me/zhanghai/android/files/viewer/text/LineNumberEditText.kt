/*
 * Copyright (c) 2024 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.text

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText

class LineNumberEditText : AppCompatEditText {

    constructor(context: Context) : super(context) { init() }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) { init() }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context, attrs, defStyleAttr
    ) { init() }

    // ── Paints ────────────────────────────────────────────────────

    private var lineNumberPaint: Paint? = null
    private var dividerPaint: Paint? = null

    // ── Hardcoded colours ─────────────────────────────────────────
    private val lineNumberColor = 0xFF808080.toInt()
    private val dividerColor = 0xFF808080.toInt()

    // ── Gutter metrics ────────────────────────────────────────────

    private var gutterTextMargin = 0f
    private var dividerMargin = 0f
    private var minGutterWidth = 0f
    private var gutterWidth = 0
    private var gutterDigitCount = 1

    // ── Base padding ──────────────────────────────────────────────

    private var basePaddingLeft = 0
    private var basePaddingTop = 0
    private var basePaddingRight = 0
    private var basePaddingBottom = 0
    private var applyingPadding = false

    // ── Logical line mapping ──────────────────────────────────────

    private var visualToLogicalLine: IntArray = IntArray(0)
    private var logicalLineCount = 1

    // ── Cached clip rect ──────────────────────────────────────────
    private val clipRect = Rect()

    // ── Init ──────────────────────────────────────────────────────

    private fun init() {
        val density = resources.displayMetrics.density
        gutterTextMargin = (4f * density)
        dividerMargin = (8f * density)
        minGutterWidth = (40f * density)

        lineNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.RIGHT
        }
        dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }

        syncPaints()
        updateLogicalLineMapping()
        updateGutterWidth()
    }

    private fun syncPaints() {
        val paint = lineNumberPaint ?: return
        paint.textSize = textSize
        paint.typeface = typeface
        paint.color = lineNumberColor

        val div = dividerPaint ?: return
        div.color = dividerColor
    }

    // ── Logical ↔ visual line mapping ─────────────────────────────

    private fun updateLogicalLineMapping() {
        val layout = layout
        val text = text
        if (layout == null || text == null) {
            visualToLogicalLine = IntArray(0)
            logicalLineCount = 1
            return
        }

        val lineCount = layout.lineCount
        visualToLogicalLine = IntArray(lineCount)
        var logical = 1

        for (i in 0 until lineCount) {
            val lineStart = layout.getLineStart(i)
            if (i == 0 || (lineStart > 0 && text[lineStart - 1] == '\n')) {
                visualToLogicalLine[i] = logical
                logical++
            } else {
                visualToLogicalLine[i] = -1
            }
        }
        logicalLineCount = logical - 1
    }

    // ── Gutter width ──────────────────────────────────────────────

    private fun updateGutterWidth() {
        val paint = lineNumberPaint ?: return
        val newDigitCount = maxOf(1, logicalLineCount.toString().length)
        if (newDigitCount == gutterDigitCount) return
        gutterDigitCount = newDigitCount
        val textWidth = paint.measureText("0".repeat(gutterDigitCount))
        val newGutterWidth = maxOf(
            minGutterWidth,
            textWidth + gutterTextMargin + dividerMargin
        ).toInt()
        if (newGutterWidth != gutterWidth) {
            gutterWidth = newGutterWidth
            applyPadding()
        }
    }

    // ── Padding ───────────────────────────────────────────────────

    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        basePaddingLeft = left
        basePaddingTop = top
        basePaddingRight = right
        basePaddingBottom = bottom
        applyPadding()
    }

    override fun setPaddingRelative(start: Int, top: Int, end: Int, bottom: Int) {
        basePaddingLeft = start
        basePaddingTop = top
        basePaddingRight = end
        basePaddingBottom = bottom
        applyPadding()
    }

    private fun applyPadding() {
        if (applyingPadding) return
        applyingPadding = true
        super.setPadding(
            basePaddingLeft + gutterWidth,
            basePaddingTop,
            basePaddingRight,
            basePaddingBottom
        )
        applyingPadding = false
    }

    // ── Drawing ───────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        drawGutter(canvas)
        super.onDraw(canvas)
    }

    private fun drawGutter(canvas: Canvas) {
        val linePaint = lineNumberPaint ?: return
        val divPaint = dividerPaint ?: return
        val layout = layout ?: return
        val mapping = visualToLogicalLine
        if (mapping.isEmpty()) return

        val lineCount = layout.lineCount
        if (lineCount == 0) return

        clipRect.setEmpty()
        if (!canvas.getClipBounds(clipRect)) return
        if (clipRect.isEmpty) return

        val firstLine = layout.getLineForVertical(clipRect.top).coerceIn(0, lineCount - 1)
        val lastLine = layout.getLineForVertical(clipRect.bottom).coerceIn(0, lineCount - 1)

        for (i in firstLine..lastLine) {
            val logical = mapping.getOrNull(i) ?: continue
            if (logical < 0) continue
            val baseline = layout.getLineBaseline(i)
            val x = gutterWidth - dividerMargin - gutterTextMargin
            canvas.drawText(logical.toString(), x, baseline.toFloat(), linePaint)
        }

        val dividerX = gutterWidth - dividerMargin + divPaint.strokeWidth / 2f
        canvas.drawLine(
            dividerX,
            clipRect.top.toFloat(),
            dividerX,
            clipRect.bottom.toFloat(),
            divPaint
        )
    }

    // ── Preserve ScrollingChildEditText behaviour ─────────────────

    override fun onPreDraw(): Boolean = true

    // ── Callbacks ─────────────────────────────────────────────────

    override fun onTextChanged(
        text: CharSequence?,
        start: Int,
        lengthBefore: Int,
        lengthAfter: Int
    ) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)
        post {
            updateLogicalLineMapping()
            updateGutterWidth()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateLogicalLineMapping()
        updateGutterWidth()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed) {
            updateLogicalLineMapping()
            updateGutterWidth()
        }
    }

    override fun setTextSize(size: Float) {
        super.setTextSize(size)
        syncPaints()
        updateLogicalLineMapping()
        updateGutterWidth()
    }

    override fun setTextSize(unit: Int, size: Float) {
        super.setTextSize(unit, size)
        syncPaints()
        updateLogicalLineMapping()
        updateGutterWidth()
    }

    override fun setTypeface(tf: Typeface?) {
        super.setTypeface(tf)
        syncPaints()
        updateLogicalLineMapping()
        updateGutterWidth()
    }

    override fun setTextColor(color: Int) {
        super.setTextColor(color)
        // Our colours are hardcoded so no update needed beyond paint sync
        syncPaints()
    }
}