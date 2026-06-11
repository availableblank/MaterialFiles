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
import android.util.TypedValue
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

    // ── Logical line mapping (pre‑computed for performance) ───────
    // visualToLogicalLine[i] = logical line number for visual line i,
    // or -1 if this visual line is a wrapped continuation
    private var visualToLogicalLine: IntArray = IntArray(0)
    /** Total number of logical lines (used for gutter width) */
    private var logicalLineCount = 1

    // ── Cached clip rect (avoid allocation per frame) ────────────
    private val clipRect = Rect()

    // ── Colours resolved from theme (refreshed in onAttachedToWindow) ─
    private var lineNumberColor = 0x60757575.toInt()
    private var dividerColor = 0x20757575.toInt()
    private var lastResolvedTextColor = 0

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

        resolveThemeColors()
        syncPaints()
        updateLogicalLineMapping()
        updateGutterWidth()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Re‑resolve theme colours in case the theme changed
        resolveThemeColors()
        syncPaints()
        invalidate()
    }

    // ── Theme colour resolution ───────────────────────────────────

    private fun resolveThemeColors() {
        // Use android:textColorTertiary for line numbers — adapts to light/dark automatically.
        val tv = TypedValue()
        if (context.theme.resolveAttribute(android.R.attr.textColorTertiary, tv, true)) {
            lineNumberColor = tv.data
        } else {
            // Fallback: dim current text color (visible in both modes)
            val c = currentTextColor
            val a = (android.graphics.Color.alpha(c) * 0.5f).toInt().coerceIn(40, 200)
            lineNumberColor = android.graphics.Color.argb(
                a,
                android.graphics.Color.red(c),
                android.graphics.Color.green(c),
                android.graphics.Color.blue(c)
            )
        }

        // Divider: very translucent version of the same colour
        dividerColor = (lineNumberColor and 0x00FFFFFF) or 0x20000000
    }

    /** Called when the EditText's own text colour might have changed. */
    private fun syncPaints() {
        val paint = lineNumberPaint ?: return
        paint.textSize = textSize
        paint.typeface = typeface
        paint.color = lineNumberColor

        val div = dividerPaint ?: return
        div.color = dividerColor
    }

    // ── Logical ↔ visual line mapping ─────────────────────────────

    /**
     * Builds [visualToLogicalLine] so we can quickly tell which
     * visual lines should show a line number and which are soft-wrapped
     * continuations.
     */
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
                // This visual line begins a new logical line
                visualToLogicalLine[i] = logical
                logical++
            } else {
                // Soft‑wrapped continuation → no number
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

        // Reuse cached Rect to avoid per‑frame allocation
        clipRect.setEmpty()
        if (!canvas.getClipBounds(clipRect)) return
        if (clipRect.isEmpty) return

        val firstLine = layout.getLineForVertical(clipRect.top).coerceIn(0, lineCount - 1)
        val lastLine = layout.getLineForVertical(clipRect.bottom).coerceIn(0, lineCount - 1)

        // Only draw line numbers for logical line starts (skip soft‑wrapped continuations)
        for (i in firstLine..lastLine) {
            val logical = mapping.getOrNull(i) ?: continue
            if (logical < 0) continue   // soft‑wrapped continuation
            val baseline = layout.getLineBaseline(i)
            val x = gutterWidth - dividerMargin - gutterTextMargin
            canvas.drawText(logical.toString(), x, baseline.toFloat(), linePaint)
        }

        // Vertical divider
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
        // Defer mapping update to after layout pass
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
        if (color != lastResolvedTextColor) {
            lastResolvedTextColor = color
            // Only fall back to derived color if we couldn't resolve textColorTertiary
            if (lineNumberColor == 0 || lineNumberColor == lastResolvedTextColor) {
                resolveThemeColors()
            }
            syncPaints()
        }
    }
}