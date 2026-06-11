/*
 * Copyright (c) 2024 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.text

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText

class LineNumberEditText : AppCompatEditText {

    // ── Constructors ──────────────────────────────────────────────

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context, attrs, defStyleAttr
    ) {
        init()
    }

    // ── Paints (nullable to survive callbacks during super constructor) ─

    private var lineNumberPaint: Paint? = null
    private var dividerPaint: Paint? = null

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

        syncLineNumberPaint()
        syncDividerPaint()
        updateGutterWidth()
    }

    // ── Appearance sync ───────────────────────────────────────────

    private fun syncLineNumberPaint() {
        val paint = lineNumberPaint ?: return
        paint.textSize = textSize
        paint.typeface = typeface
        val color = currentTextColor
        val alpha = (Color.alpha(color) * 0.38f).toInt().coerceIn(0, 255)
        paint.color = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun syncDividerPaint() {
        val paint = dividerPaint ?: return
        val color = currentTextColor
        val alpha = (Color.alpha(color) * 0.12f).toInt().coerceIn(0, 255)
        paint.color = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    // ── Gutter width ──────────────────────────────────────────────

    private fun updateGutterWidth() {
        val paint = lineNumberPaint ?: return
        val lineCount = maxOf(1, lineCount)
        val newDigitCount = lineCount.toString().length
        if (newDigitCount == gutterDigitCount) return
        gutterDigitCount = newDigitCount
        val textWidth = paint.measureText("0".repeat(gutterDigitCount))
        val newGutterWidth = maxOf(minGutterWidth, textWidth + gutterTextMargin + dividerMargin).toInt()
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
        val lineCount = lineCount
        if (lineCount == 0) return

        val clip = Rect()
        if (!canvas.getClipBounds(clip)) return
        if (clip.isEmpty) return

        val firstLine = layout.getLineForVertical(clip.top).coerceIn(0, lineCount - 1)
        val lastLine = layout.getLineForVertical(clip.bottom).coerceIn(0, lineCount - 1)

        for (i in firstLine..lastLine) {
            val baseline = layout.getLineBaseline(i)
            val lineNumber = (i + 1).toString()
            val x = gutterWidth - dividerMargin - gutterTextMargin
            canvas.drawText(lineNumber, x, baseline.toFloat(), linePaint)
        }

        val dividerX = gutterWidth - dividerMargin + divPaint.strokeWidth / 2f
        canvas.drawLine(dividerX, clip.top.toFloat(), dividerX, clip.bottom.toFloat(), divPaint)
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
        post { updateGutterWidth() }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateGutterWidth()
    }

    override fun setTextSize(size: Float) {
        super.setTextSize(size)
        syncLineNumberPaint()
        updateGutterWidth()
    }

    override fun setTextSize(unit: Int, size: Float) {
        super.setTextSize(unit, size)
        syncLineNumberPaint()
        updateGutterWidth()
    }

    override fun setTypeface(tf: Typeface?) {
        super.setTypeface(tf)
        syncLineNumberPaint()
        updateGutterWidth()
    }

    override fun setTextColor(color: Int) {
        super.setTextColor(color)
        syncLineNumberPaint()
        syncDividerPaint()
    }
}