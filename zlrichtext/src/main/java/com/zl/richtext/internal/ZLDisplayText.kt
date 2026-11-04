package com.zl.richtext.internal

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import com.zl.richtext.style.ZLTextStyle
import kotlin.math.roundToInt

/** How the content is cut to fit the line limit. */
internal sealed class ZLTruncation {
    /** The whole content. */
    object None : ZLTruncation()

    /** `content[0, cut) + token`. */
    data class Tail(val cut: Int) : ZLTruncation()

    /** `token + content[start, length)`. */
    data class Head(val start: Int) : ZLTruncation()

    /** `content[0, head) + token + content[tail, length)`. */
    data class Middle(val head: Int, val tail: Int) : ZLTruncation()
}

/** A range of the display text and where it comes from. */
internal class ZLDisplayPiece(
    val kind: Kind,
    val displayStart: Int,
    val length: Int,
    /** Content offset for content pieces, the owning content character for spacers. */
    val contentStart: Int,
) {
    enum class Kind { CONTENT, TOKEN, SPACER }

    val displayEnd: Int get() = displayStart + length
}

/** Maps display offsets (what the TextView shows) to content offsets and back. */
internal class ZLDisplayMap(val pieces: List<ZLDisplayPiece>) {

    private fun pieceAt(displayIndex: Int): ZLDisplayPiece? =
        pieces.firstOrNull { displayIndex >= it.displayStart && displayIndex < it.displayEnd }

    /** Content index of a display character; spacers belong to their capsule; `null` for the token. */
    fun contentIndex(displayIndex: Int): Int? {
        val piece = pieceAt(displayIndex) ?: return null
        return when (piece.kind) {
            ZLDisplayPiece.Kind.CONTENT -> piece.contentStart + (displayIndex - piece.displayStart)
            ZLDisplayPiece.Kind.SPACER -> piece.contentStart
            ZLDisplayPiece.Kind.TOKEN -> null
        }
    }

    fun isToken(displayIndex: Int): Boolean = pieceAt(displayIndex)?.kind == ZLDisplayPiece.Kind.TOKEN

    /** Display ranges covering the content range `[start, end)`. */
    fun displayRanges(start: Int, end: Int): List<IntArray> {
        val result = ArrayList<IntArray>()
        for (piece in pieces) {
            if (piece.kind != ZLDisplayPiece.Kind.CONTENT) continue
            val s = maxOf(start, piece.contentStart)
            val e = minOf(end, piece.contentStart + piece.length)
            if (s >= e) continue
            val ds = piece.displayStart + (s - piece.contentStart)
            val de = piece.displayStart + (e - piece.contentStart)
            val last = result.lastOrNull()
            if (last != null && last[1] == ds) last[1] = de else result += intArrayOf(ds, de)
        }
        return result
    }

    /** Display range of the token, `null` when not shown. */
    fun tokenRange(): IntArray? {
        val tokens = pieces.filter { it.kind == ZLDisplayPiece.Kind.TOKEN }
        if (tokens.isEmpty()) return null
        return intArrayOf(tokens.first().displayStart, tokens.last().displayEnd)
    }

    /** Content caret of a display caret (selection ends). */
    fun contentCaret(displayOffset: Int): Int {
        var last = 0
        for (piece in pieces) {
            if (piece.kind != ZLDisplayPiece.Kind.CONTENT) continue
            if (displayOffset <= piece.displayStart) return piece.contentStart
            if (displayOffset <= piece.displayEnd) return piece.contentStart + (displayOffset - piece.displayStart)
            last = piece.contentStart + piece.length
        }
        return last
    }

    /** Display caret of a content caret. */
    fun displayCaret(contentOffset: Int): Int {
        var last = 0
        for (piece in pieces) {
            if (piece.kind != ZLDisplayPiece.Kind.CONTENT) continue
            if (contentOffset <= piece.contentStart + piece.length && contentOffset >= piece.contentStart) {
                return piece.displayStart + (contentOffset - piece.contentStart)
            }
            if (contentOffset > piece.contentStart + piece.length) last = piece.displayEnd
        }
        return last
    }
}

/** The text shown by the TextView and its map. */
internal class ZLDisplayText(val text: SpannableStringBuilder, val map: ZLDisplayMap)

/** Inputs of [ZLDisplayText.build] that do not depend on the truncation. */
internal class ZLDisplayInputs(
    val context: Context,
    val content: CharSequence,
    val token: CharSequence?,
    val baseStyle: ZLTextStyle,
    /** Link ranges (content offsets) and the styles to apply to them. */
    val linkStyles: List<Pair<IntArray, ZLTextStyle>>,
    /** Link ranges that become accessibility link spans. */
    val accessibilityLinks: List<Pair<IntArray, Runnable>>,
    val tokenAction: Runnable?,
    val highlightedTextColor: Int?,
)

internal object ZLDisplayTextBuilder {

    /** Priority of the base style: higher priorities come first, so later styles override it. */
    private const val BASE_PRIORITY = 200
    private const val SPACER_CHAR = "\u2060"

    fun build(inputs: ZLDisplayInputs, truncation: ZLTruncation, fontScale: Float): ZLDisplayText {
        val content = inputs.content
        val token = inputs.token ?: ""
        val slices: List<Pair<CharSequence, Int>> = when (truncation) {
            ZLTruncation.None -> listOf(content to 0)
            is ZLTruncation.Tail -> listOf(content.subSequence(0, truncation.cut) to 0, token to -1)
            is ZLTruncation.Head -> listOf(token to -1, content.subSequence(truncation.start, content.length) to truncation.start)
            is ZLTruncation.Middle -> listOf(
                content.subSequence(0, truncation.head) to 0,
                token to -1,
                content.subSequence(truncation.tail, content.length) to truncation.tail,
            )
        }
        val result = SpannableStringBuilder()
        val pieces = ArrayList<ZLDisplayPiece>()
        for ((slice, contentStart) in slices) {
            if (slice.isEmpty()) continue
            if (contentStart < 0) {
                pieces += ZLDisplayPiece(ZLDisplayPiece.Kind.TOKEN, result.length, slice.length, -1)
                result.append(slice)
                continue
            }
            appendWithSpacers(result, slice, contentStart, pieces, inputs.context)
        }
        val map = ZLDisplayMap(pieces)
        decorate(result, map, inputs, fontScale)
        return ZLDisplayText(result, map)
    }

    /** Appends a content slice, inserting spacers that reserve the capsule padding. */
    private fun appendWithSpacers(
        result: SpannableStringBuilder,
        slice: CharSequence,
        contentStart: Int,
        pieces: MutableList<ZLDisplayPiece>,
        context: Context,
    ) {
        // Spacer positions inside the slice: (position, width, owning content index, isLeading).
        data class Spacer(val position: Int, val width: Int, val owner: Int, val leading: Boolean)

        val spacers = ArrayList<Spacer>()
        if (slice is Spanned) {
            for (span in slice.getSpans(0, slice.length, ZLBackgroundSpan::class.java)) {
                if (span.isHighlight) continue
                val s = slice.getSpanStart(span)
                val e = slice.getSpanEnd(span)
                if (s >= e) continue
                spacers += Spacer(s, (span.paddingHorizontal + span.margin).roundToInt(), contentStart + s, true)
                spacers += Spacer(e, (span.paddingHorizontal + span.margin).roundToInt(), contentStart + e - 1, false)
            }
        }
        // Trailing spacers before leading ones at the same position: `#a##b#` keeps two capsules.
        spacers.sortWith(compareBy<Spacer> { it.position }.thenBy { it.leading })
        val builder = SpannableStringBuilder(slice)
        // Insert from the end so earlier positions stay valid; spans are EXCLUSIVE_EXCLUSIVE, so
        // text inserted at their edges stays outside of them.
        for (spacer in spacers.asReversed()) {
            builder.insert(spacer.position, SPACER_CHAR)
            builder.setSpan(ZLSpacerSpan(spacer.width), spacer.position, spacer.position + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val base = result.length
        var cursor = 0
        var inserted = 0
        for (spacer in spacers) {
            if (spacer.position > cursor) {
                pieces += ZLDisplayPiece(ZLDisplayPiece.Kind.CONTENT, base + cursor + inserted, spacer.position - cursor, contentStart + cursor)
            }
            pieces += ZLDisplayPiece(ZLDisplayPiece.Kind.SPACER, base + spacer.position + inserted, 1, spacer.owner)
            inserted++
            cursor = spacer.position
        }
        if (cursor < slice.length) {
            pieces += ZLDisplayPiece(ZLDisplayPiece.Kind.CONTENT, base + cursor + inserted, slice.length - cursor, contentStart + cursor)
        }
        result.append(builder)
    }

    private fun decorate(text: SpannableStringBuilder, map: ZLDisplayMap, inputs: ZLDisplayInputs, fontScale: Float) {
        val context = inputs.context
        val flags = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        // Base style under everything.
        ZLStyleSpans.apply(text, inputs.baseStyle, 0, text.length, context, priority = BASE_PRIORITY)
        // Link styles (default / inactive), on top of the content styles.
        for ((range, style) in inputs.linkStyles) {
            for (r in map.displayRanges(range[0], range[1])) ZLStyleSpans.apply(text, style, r[0], r[1], context)
        }
        // Links for accessibility services (TalkBack activates them through ClickableSpan).
        for ((range, action) in inputs.accessibilityLinks) {
            for (r in map.displayRanges(range[0], range[1])) text.setSpan(ZLLinkSpan(action), r[0], r[1], flags)
        }
        val tokenAction = inputs.tokenAction
        val tokenRange = map.tokenRange()
        if (tokenAction != null && tokenRange != null) {
            text.setSpan(ZLLinkSpan(tokenAction), tokenRange[0], tokenRange[1], flags)
        }
        inputs.highlightedTextColor?.let { text.setSpan(ForegroundColorSpan(it), 0, text.length, flags) }
        // Font fitting: applied last, so it scales every absolute size.
        if (fontScale < 1f) text.setSpan(RelativeSizeSpan(fontScale), 0, text.length, flags)
    }
}
