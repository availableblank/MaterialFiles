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

/**
 * An [AppCompatEditText] that draws line numbers in a left-side gutter.
 *
 * It preserves the [ScrollingChildEditText] behavior of preventing unwanted
 * scroll when the IME is toggled (by overriding [onPreDraw]).
 *
 * Line numbers are only drawn for the currently visible lines (determined
 * via the canvas clip bounds), making it efficient even for large files.
 */
class LineNumberEditText : AppCompatEditText {

    // ──────────────────────────────────────────────────────────────────
    // Constructors (mirror ScrollingChildEditText)
    // ──────────────────────────────────────────────────────────────────

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

    // ──────────────────────────────────────────────────────────────────
    // Paints
    // ──────────────────────────────────────────────────────────────────

    private val lineNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
    }

    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    // ──────────────────────────────────────────────────────────────────
    // Gutter metrics (initialised in init())
    // ──────────────────────────────────────────────────────────────────

    /** Space between the right edge of the line number and the divider. */
    private var gutterTextMargin = 0f
    /** Space between the divider and the text content. */
    private var dividerMargin = 0f
    /** Minimum gutter width (avoids a tiny gutter for 1‑line files). */
    private var minGutterWidth = 0f

    /** Current gutter width in pixels (updated when line‑count changes). */
    private var gutterWidth = 0
    /** Number of decimal digits needed for the current line count. */
    private var gutterDigitCount = 1

    // ──────────────────────────────────────────────────────────────────
    // Base padding (user / XML specified, excluding gutter)
    // ──────────────────────────────────────────────────────────────────

    private var basePaddingLeft = 0
    private var basePaddingTop = 0
    private var basePaddingRight = 0
    private var basePaddingBottom = 0

    /** Guard to prevent infinite recursion when applying padding. */
    private var applyingPadding = false

    // ──────────────────────────────────────────────────────────────────
    // Initialisation
    // ──────────────────────────────────────────────────────────────────

    private fun init() {
        val density = resources.displayMetrics.density
        gutterTextMargin = (4f * density)    // 4 dp
        dividerMargin = (8f * density)        // 8 dp
        minGutterWidth = (40f * density)      // 40 dp

        syncLineNumberPaint()
        syncDividerPaint()
        updateGutterWidth()
    }

    // ──────────────────────────────────────────────────────────────────
    // Appearance sync
    // ──────────────────────────────────────────────────────────────────

     private fun syncLineNumberPaint() {
         lineNumberPaint.textSize = textSize
         lineNumberPaint.typeface = typeface
         val color = currentTextColor
         val alpha = (Color.alpha(color) * 0.38f).toInt().coerceIn(0, 255)
         lineNumberPaint.color = Color.argb(
             alpha,
             Color.red(color),
             Color.green(color),
             Color.blue(color)
         )
     }

     private fun syncDividerPaint() {
         val color = currentTextColor
         val alpha = (Color.alpha(color) * 0.12f).toInt().coerceIn(0, 255)
        dividerPaint.color = Color.argb(
             alpha,
             Color.red(color),
             Color.green(color),
             Color.blue(color)
         )
     }

    // ──────────────────────────────────────────────────────────────────
    // Gutter width management
    // ──────────────────────────────────────────────────────────────────

    /**
     * Recalculates the gutter width based on the current line count.
     * If the required digit count hasn't changed the method is a no‑op.
     */
    private fun updateGutterWidth() {
        // When no layout exists yet, lineCount may be 0 → treat as 1 line.
        val lineCount = maxOf(1, lineCount)
        val newDigitCount = lineCount.toString().length
        if (newDigitCount == gutterDigitCount) return

        gutterDigitCount = newDigitCount
        val textWidth = lineNumberPaint.measureText("0".repeat(gutterDigitCount))
        val newGutterWidth = maxOf(
            minGutterWidth,
            textWidth + gutterTextMargin + dividerMargin
        ).toInt()
        if (newGutterWidth != gutterWidth) {
            gutterWidth = newGutterWidth
            applyPadding()
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // Padding (user padding + gutter)
    // ──────────────────────────────────────────────────────────────────

    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        basePaddingLeft = left
        basePaddingTop = top
        basePaddingRight = right
        basePaddingBottom = bottom
        applyPadding()
    }

    override fun setPaddingRelative(start: Int, top: Int, end: Int, bottom: Int) {
        // The gutter is always on the left (visual left) regardless of RTL.
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

    // ──────────────────────────────────────────────────────────────────
    // Drawing
    // ──────────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        // Draw the gutter (line numbers + divider) first, then the text.
        drawGutter(canvas)
        super.onDraw(canvas)
    }

    private fun drawGutter(canvas: Canvas) {
        val layout = layout ?: return
        val lineCount = lineCount
        if (lineCount == 0) return

        // Determine the visible line range from the canvas clip.
        val clip = Rect()
        if (!canvas.getClipBounds(clip)) return
        if (clip.isEmpty) return

        val firstLine = layout.getLineForVertical(clip.top).coerceIn(0, lineCount - 1)
        val lastLine = layout.getLineForVertical(clip.bottom).coerceIn(0, lineCount - 1)

        // ── Line numbers ──────────────────────────────────────────
        for (i in firstLine..lastLine) {
            val baseline = layout.getLineBaseline(i)
            val lineNumber = (i + 1).toString()
            val x = gutterWidth - dividerMargin - gutterTextMargin
            canvas.drawText(lineNumber, x, baseline.toFloat(), lineNumberPaint)
        }

        // ── Vertical divider ──────────────────────────────────────
        val dividerX = gutterWidth - dividerMargin + dividerPaint.strokeWidth / 2f
        canvas.drawLine(
            dividerX,
            clip.top.toFloat(),
            dividerX,
            clip.bottom.toFloat(),
            dividerPaint
        )
    }

    // ──────────────────────────────────────────────────────────────────
    // Preserve ScrollingChildEditText behaviour
    // ──────────────────────────────────────────────────────────────────

    /**
     * Same as [ScrollingChildEditText]: prevent the default
     * [bringPointIntoView] call that causes unwanted scrolling when
     * the soft keyboard is toggled.
     */
    override fun onPreDraw(): Boolean = true

    // ──────────────────────────────────────────────────────────────────
    // Callbacks that may require a gutter update
    // ──────────────────────────────────────────────────────────────────

    override fun onTextChanged(
        text: CharSequence?,
        start: Int,
        lengthBefore: Int,
        lengthAfter: Int
    ) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)
        // Layout may not be updated yet; post to the next frame.
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