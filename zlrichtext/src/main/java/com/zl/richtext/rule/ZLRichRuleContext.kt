package com.zl.richtext.rule

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import com.zl.richtext.internal.ZLStyleSpans
import com.zl.richtext.internal.ZLWidgetSpan
import com.zl.richtext.style.ZLRichWidget
import com.zl.richtext.style.ZLTextStyle

/** A tap / long press on a part of the content of one match (see [ZLRichRuleContext.addLink]). */
public class ZLRuleLink internal constructor(
    /** Range relative to the content written by the handler. */
    public val range: ZLRange,
    public val highlightStyle: ZLTextStyle?,
    public val onTap: ZLRichMatchListener?,
    public val onLongPress: ZLRichMatchListener?,
)

/**
 * What a rule handler receives: the match, and the content to write.
 *
 * ```kotlin
 * ZLRichRule.regex("topic", "#[^#\\s]+#") { ctx ->
 *     ctx.addStyle(ZLTextStyle().color(PURPLE).capsule(PURPLE))
 *     ctx.highlightStyle = ZLTextStyle().background(ZLTextBackground(fillColor = ZLColor.of(DARK_PURPLE)))
 *     ctx.onTap = ZLRichMatchListener { match -> openTopic(match.text) }
 * }
 * ```
 */
public class ZLRichRuleContext internal constructor(
    public val match: ZLRichMatch,
    /** Style of plain text (the view applies it under every match as well). */
    public val baseStyle: ZLTextStyle,
    /** Context of the view, e.g. to resolve resources; handlers run again on configuration changes. */
    public val context: Context,
) {
    /**
     * Display content of the match: initially the matched text. Modify it freely (styles,
     * replacement text, widgets via [widgetText]); empty content hides the match.
     */
    public val content: SpannableStringBuilder = SpannableStringBuilder(match.text)

    /** Makes the whole content tappable (including widgets without their own `onClick`). */
    public var onTap: ZLRichMatchListener? = null

    /** Long press on the content; the view's `onLongPressListener` is not called for it. */
    public var onLongPress: ZLRichMatchListener? = null

    /** Press highlight; `null` uses the view's `highlightStyle`, [ZLTextStyle.NONE] disables it. */
    public var highlightStyle: ZLTextStyle? = null

    private val linkList = ArrayList<ZLRuleLink>()

    /** Links added with [addLink]. */
    public val links: List<ZLRuleLink> get() = linkList

    public val ruleName: String get() = match.rule.name

    public val matchedText: String get() = match.text

    public fun group(index: Int): String? = match.group(index)

    public fun group(name: String): String? = match.group(name)

    /** Replaces the content with [text] in [style]. */
    @JvmOverloads
    public fun setText(text: CharSequence, style: ZLTextStyle? = null) {
        content.clear()
        content.clearSpans()
        content.append(styled(text, style))
    }

    /** Applies [style] to the whole content, or to `[start, end)` of it. */
    @JvmOverloads
    public fun addStyle(style: ZLTextStyle, start: Int = 0, end: Int = content.length) {
        ZLStyleSpans.apply(content, style, start, end, context)
    }

    /** Replaces the content with an inline widget. */
    @JvmOverloads
    public fun setWidget(widget: ZLRichWidget, style: ZLTextStyle? = null) {
        setText(widgetText(widget, style))
    }

    /** Text holding an inline widget, to combine with other content: `ctx.content.append(ctx.widgetText(icon))`. */
    @JvmOverloads
    public fun widgetText(widget: ZLRichWidget, style: ZLTextStyle? = null): CharSequence =
        SpannableStringBuilder(OBJECT_REPLACEMENT).apply {
            if (style != null) ZLStyleSpans.apply(this, style, 0, length, context)
            setSpan(ZLWidgetSpan(widget), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

    /** [text] with [style] applied. */
    @JvmOverloads
    public fun styled(text: CharSequence, style: ZLTextStyle? = null): CharSequence =
        SpannableStringBuilder(text).apply { if (style != null) ZLStyleSpans.apply(this, style, 0, length, context) }

    /**
     * Gives a part of the content (range relative to [content]) its own tap / long press /
     * press highlight. Links may nest; the link added last wins where they overlap. Parts not
     * covered by any link use [onTap] / [onLongPress].
     */
    @JvmOverloads
    public fun addLink(
        start: Int,
        end: Int,
        highlightStyle: ZLTextStyle? = null,
        onTap: ZLRichMatchListener? = null,
        onLongPress: ZLRichMatchListener? = null,
    ) {
        if (start >= end || (onTap == null && onLongPress == null)) return
        linkList += ZLRuleLink(ZLRange(start, end), highlightStyle, onTap, onLongPress)
    }

    internal companion object {
        const val OBJECT_REPLACEMENT: String = "\uFFFC"
    }
}
