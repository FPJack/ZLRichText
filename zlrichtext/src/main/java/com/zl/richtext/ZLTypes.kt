package com.zl.richtext

import com.zl.richtext.detect.ZLDataDetectorResult
import com.zl.richtext.rule.ZLRichMatch
import com.zl.richtext.style.ZLTextStyle

/** Tap / long press on a [ZLLink]. */
public fun interface ZLLinkListener {
    public fun onLink(view: ZLAttributedTextView, link: ZLLink)
}

/**
 * A tappable range of a [ZLAttributedTextView] (offsets in the text set with `setText`).
 *
 * ```kotlin
 * view.addLink(ZLLink(5, 9,
 *     style = ZLTextStyle().color(Color.WHITE).backgroundColor(ORANGE),
 *     activeStyle = ZLTextStyle().backgroundColor(BROWN),
 *     onClick = { _, link -> println(link.start) }))
 * ```
 */
public class ZLLink @JvmOverloads constructor(
    public val start: Int,
    public val end: Int,
    /** Link target (`ZLTextStyle.link`, URL spans, detected URLs …). */
    public val url: String? = null,
    /** Detection result of links found by data detectors. */
    public val result: ZLDataDetectorResult? = null,
    /** Style of the range; `null` uses the view's `linkStyle`, [ZLTextStyle.NONE] keeps the text as is. */
    public val style: ZLTextStyle? = null,
    /** Style while pressed; `null` uses the view's `activeLinkStyle`. */
    public val activeStyle: ZLTextStyle? = null,
    /** Style while the window has no focus (a dialog is shown); `null` uses `inactiveLinkStyle`. */
    public val inactiveStyle: ZLTextStyle? = null,
    /** Tap; `null` calls the view's `onLinkClickListener`. */
    public var onClick: ZLLinkListener? = null,
    /** Long press; `null` calls the view's `onLinkLongClickListener`. */
    public var onLongClick: ZLLinkListener? = null,
) {
    init {
        require(start in 0..end) { "Invalid link range [$start, $end)" }
    }

    override fun toString(): String = "ZLLink([$start, $end)${url?.let { ", $it" } ?: ""})"
}

/** How a single line is truncated; multiple lines are always truncated at the tail. */
public enum class ZLTruncationMode { TAIL, HEAD, MIDDLE }

/** Position information of a long press / double tap. */
public class ZLGestureInfo(
    /** Position in the view's coordinate system. */
    public val x: Float,
    public val y: Float,
    /** Character of the content under the gesture, -1 when not on the text. */
    public val characterIndex: Int,
    /** Rule match under the gesture ([ZLRichTextView] only). */
    public val match: ZLRichMatch?,
    /** How the match is displayed (rules may replace the text). */
    public val matchDisplayText: String?,
    /** The raw string. */
    public val rawText: String,
    /** The displayed content (inline widgets are U+FFFC). */
    public val displayText: String,
) {
    override fun toString(): String =
        "ZLGestureInfo(x=$x, y=$y, characterIndex=$characterIndex, match=$match, matchDisplayText=$matchDisplayText)"
}

/** Render / layout counters, useful to observe the caches. Call [reset] before a measurement. */
public class ZLStatistics {
    /** Compositions of the display content. */
    public var renders: Int = 0
        internal set

    /** Rule handlers executed (cache misses). */
    public var handlerCalls: Int = 0
        internal set

    /** Matches whose cached result was reused. */
    public var reusedMatches: Int = 0
        internal set

    /** Text layouts made to measure lines (truncation, font fitting). */
    public var textLayouts: Int = 0
        internal set

    /** Measure passes answered from the layout cache (same content and size). */
    public var layoutCacheHits: Int = 0
        internal set

    public fun reset() {
        renders = 0
        handlerCalls = 0
        reusedMatches = 0
        textLayouts = 0
        layoutCacheHits = 0
    }

    override fun toString(): String =
        "renders=$renders, handlerCalls=$handlerCalls, reusedMatches=$reusedMatches, textLayouts=$textLayouts, layoutCacheHits=$layoutCacheHits"
}
