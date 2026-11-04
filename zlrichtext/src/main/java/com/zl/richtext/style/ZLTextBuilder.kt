package com.zl.richtext.style

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import com.zl.richtext.internal.ZLStyleSpans
import com.zl.richtext.internal.ZLWidgetSpan

/**
 * Builds styled text with [ZLTextStyle]s and inline widgets (the counterpart of building an
 * `NSAttributedString` on iOS).
 *
 * ```kotlin
 * val text = ZLTextBuilder(context)
 *     .append("颜色", ZLTextStyle().color(PINK))
 *     .append("、粗体", ZLTextStyle().bold())
 *     .appendWidget(ZLRichWidget.icon(context, R.drawable.ic_star))
 *     .append("Apple", ZLTextStyle().link("https://apple.com"))   // a link in ZLAttributedTextView
 *     .build()
 * ```
 */
public class ZLTextBuilder(private val context: Context) {
    private val builder = SpannableStringBuilder()

    public val length: Int get() = builder.length

    /** Appends [text] in [style] ([text] keeps its own spans). */
    @JvmOverloads
    public fun append(text: CharSequence, style: ZLTextStyle? = null): ZLTextBuilder {
        val start = builder.length
        builder.append(text)
        if (style != null) ZLStyleSpans.apply(builder, style, start, builder.length, context)
        return this
    }

    /** Appends an inline widget; [style] matters for widgets tinted with the text colour. */
    @JvmOverloads
    public fun appendWidget(widget: ZLRichWidget, style: ZLTextStyle? = null): ZLTextBuilder {
        val start = builder.length
        builder.append(OBJECT_REPLACEMENT)
        if (style != null) ZLStyleSpans.apply(builder, style, start, builder.length, context)
        builder.setSpan(ZLWidgetSpan(widget), start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return this
    }

    /** Applies [style] to `[start, end)` of what was appended so far. */
    public fun style(style: ZLTextStyle, start: Int, end: Int): ZLTextBuilder {
        ZLStyleSpans.apply(builder, style, start, end, context)
        return this
    }

    /** The text built so far (a copy). */
    public fun build(): CharSequence = SpannableStringBuilder(builder)

    private companion object {
        const val OBJECT_REPLACEMENT = "\uFFFC"
    }
}

/** Kotlin DSL: `buildZLText(context) { append("a", style); appendWidget(icon) }`. */
public inline fun buildZLText(context: Context, block: ZLTextBuilder.() -> Unit): CharSequence =
    ZLTextBuilder(context).apply(block).build()

/** [text] in [style]. */
public fun Context.zlStyled(text: CharSequence, style: ZLTextStyle): CharSequence = ZLTextBuilder(this).append(text, style).build()
