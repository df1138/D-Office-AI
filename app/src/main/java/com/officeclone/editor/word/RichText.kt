package com.officeclone.editor.word

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan

class RichText(val ssb: SpannableStringBuilder = SpannableStringBuilder()) {
    fun toggleBold(start: Int, end: Int) = toggleStyle(start, end, Typeface.BOLD)
    fun toggleItalic(start: Int, end: Int) = toggleStyle(start, end, Typeface.ITALIC)
    fun toggleUnderline(start: Int, end: Int) {
        val (from, to) = normalizedRange(start, end) ?: return
        val spans = ssb.getSpans(from, to, UnderlineSpan::class.java).filter {
            ssb.getSpanStart(it) < to && ssb.getSpanEnd(it) > from
        }
        if (spans.isNotEmpty()) spans.forEach(ssb::removeSpan)
        else ssb.setSpan(UnderlineSpan(), from, to, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun toggleStyle(start: Int, end: Int, style: Int) {
        val (from, to) = normalizedRange(start, end) ?: return
        val spans = ssb.getSpans(from, to, StyleSpan::class.java).filter {
            it.style == style && ssb.getSpanStart(it) < to && ssb.getSpanEnd(it) > from
        }
        if (spans.isNotEmpty()) spans.forEach(ssb::removeSpan)
        else ssb.setSpan(StyleSpan(style), from, to, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun normalizedRange(start: Int, end: Int): Pair<Int, Int>? {
        val from = minOf(start, end).coerceIn(0, ssb.length)
        val to = maxOf(start, end).coerceIn(0, ssb.length)
        return if (from < to) from to to else null
    }
}
