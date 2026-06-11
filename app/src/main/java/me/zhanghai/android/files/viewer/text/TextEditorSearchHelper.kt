/*
 * Copyright (c) 2024 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.text

import android.graphics.Typeface
import android.text.Spannable
import android.text.style.BackgroundColorSpan
import android.text.style.StyleSpan

class TextEditorSearchHelper {

    private var matchPositions: List<IntRange> = emptyList()
    private var currentMatchIndex: Int = -1
    private var query: String = ""
    private var caseSensitive: Boolean = false

    val matchCount: Int get() = matchPositions.size
    val currentIndex: Int get() = currentMatchIndex
    val isActive: Boolean get() = query.isNotEmpty()

    /**
     * 在 [text] 中执行搜索。返回找到的匹配数量。
     */
    fun search(text: CharSequence, query: String, caseSensitive: Boolean = false): Int {
        this.query = query
        this.caseSensitive = caseSensitive
        matchPositions = emptyList()
        currentMatchIndex = -1

        if (query.isEmpty() || text.isEmpty()) return 0

        val source = if (caseSensitive) text.toString() else text.toString().lowercase()
        val target = if (caseSensitive) query else query.lowercase()
        val positions = mutableListOf<IntRange>()
        var start = 0
        while (start < source.length) {
            val idx = source.indexOf(target, start)
            if (idx == -1) break
            positions += idx until (idx + target.length)
            start = idx + target.length
        }
        matchPositions = positions
        if (positions.isNotEmpty()) currentMatchIndex = 0
        return positions.size
    }

    /** 返回当前匹配范围，若没有则返回 null。 */
    fun currentMatch(): IntRange? = matchPositions.getOrNull(currentMatchIndex)

    /** 前进到下一个匹配并返回（循环）。 */
    fun nextMatch(): IntRange? {
        if (matchPositions.isEmpty()) return null
        currentMatchIndex = (currentMatchIndex + 1) % matchPositions.size
        return currentMatch()
    }

    /** 后退到上一个匹配并返回（循环）。 */
    fun previousMatch(): IntRange? {
        if (matchPositions.isEmpty()) return null
        currentMatchIndex = if (currentMatchIndex <= 0) matchPositions.lastIndex
            else currentMatchIndex - 1
        return currentMatch()
    }

    /** 清空所有搜索状态。 */
    fun clear() {
        query = ""
        matchPositions = emptyList()
        currentMatchIndex = -1
        caseSensitive = false
    }

    // ────────────────────── 高亮（实例方法，供外部调用） ──────────────────────

    /** 使用当前搜索结果对 [spannable] 应用高亮。 */
    fun applyHighlights(spannable: Spannable) {
        Companion.applyHighlights(spannable, matchPositions, currentMatchIndex)
    }

    /** 移除本搜索器曾添加的所有高亮 span。 */
    fun clearHighlights(spannable: Spannable) {
        Companion.clearHighlights(spannable)
    }

    // ────────────────────── 静态工具方法 ──────────────────────

    companion object {
        /** 半透明浅黄色 — 所有匹配项。 */
        const val HIGHLIGHT_COLOR = 0x40_FFEB3B
        /** 较浓的橙色 — 当前匹配。 */
        const val CURRENT_MATCH_COLOR = 0x60_FF9800

        /** 移除我们之前放置的所有 span。 */
        fun clearHighlights(spannable: Spannable) {
            arrayOf(BackgroundColorSpan::class.java, StyleSpan::class.java).forEach { clazz ->
                spannable.getSpans(0, spannable.length, clazz)
                    .filter { span ->
                        when (span) {
                            is BackgroundColorSpan ->
                                span.backgroundColor == HIGHLIGHT_COLOR ||
                                span.backgroundColor == CURRENT_MATCH_COLOR
                            is StyleSpan -> span.style == Typeface.BOLD
                            else -> false
                        }
                    }
                    .forEach { spannable.removeSpan(it) }
            }
        }

        /** 为所有匹配应用高亮，并强调当前匹配。 */
        fun applyHighlights(
            spannable: Spannable,
            matches: List<IntRange>,
            currentIndex: Int
        ) {
            clearHighlights(spannable)
            matches.forEachIndexed { i, range ->
                val color = if (i == currentIndex) CURRENT_MATCH_COLOR else HIGHLIGHT_COLOR
                // 防止因文本变化导致范围无效
                if (range.first in 0..spannable.length &&
                    range.last + 1 in 0..spannable.length
                ) {
                    spannable.setSpan(
                        BackgroundColorSpan(color),
                        range.first,
                        range.last + 1,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    if (i == currentIndex) {
                        spannable.setSpan(
                            StyleSpan(Typeface.BOLD),
                            range.first,
                            range.last + 1,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                }
            }
        }
    }
}