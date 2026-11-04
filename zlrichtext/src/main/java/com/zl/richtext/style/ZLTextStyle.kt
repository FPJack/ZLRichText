package com.zl.richtext.style

import android.graphics.Typeface
import android.text.Layout
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes

/**
 * Text style used everywhere in the library (the counterpart of the attribute dictionary of
 * the iOS `ZLTextStyle`). It covers the usual span attributes plus what spans cannot express:
 * capsule [background], [gradient] text, [link] targets.
 *
 * Styles are immutable; every chain method returns a new style:
 *
 * ```kotlin
 * val base = ZLTextStyle().textSizeSp(15f).color(Color.DKGRAY)
 * val title = base.bold().textSizeSp(20f).gradientColors(intArrayOf(PINK, ORANGE))
 * val topic = ZLTextStyle().color(PURPLE).capsule(PURPLE)
 * val merged = base + ZLTextStyle().color(Color.RED)    // values of the right side win
 * ```
 *
 * Java: `new ZLTextStyle().color(Color.RED).bold()`.
 */
public data class ZLTextStyle @JvmOverloads constructor(
    val textColor: ZLColor? = null,
    val backgroundColor: ZLColor? = null,
    val textSize: ZLDimension? = null,
    val relativeSize: Float? = null,
    val bold: Boolean? = null,
    val italic: Boolean? = null,
    val typeface: Typeface? = null,
    val underline: Boolean? = null,
    val strikethrough: Boolean? = null,
    /** Letter spacing in `em` (`kern` on iOS). */
    val letterSpacing: Float? = null,
    /** Moves the glyphs up (positive) without changing the line height. */
    val baselineShift: ZLDimension? = null,
    val shadow: ZLShadow? = null,
    /** Capsule background, drawn by the view. */
    val background: ZLTextBackground? = null,
    /** Gradient fill of the glyphs, drawn by the view (the original colour becomes transparent). */
    val gradient: ZLTextGradient? = null,
    /** Link target; `ZLAttributedTextView` turns it into a tappable [com.zl.richtext.ZLLink]. */
    val link: String? = null,
    /** Indent of the first line of every paragraph in the range. */
    val firstLineIndent: ZLDimension? = null,
    /** Indent of the other lines of every paragraph in the range. */
    val restLineIndent: ZLDimension? = null,
    /** Alignment of the paragraphs in the range. */
    val alignment: Layout.Alignment? = null,
) {
    /** Whether the style sets nothing. */
    val isEmpty: Boolean get() = this == NONE

    /** A style whose non-null values are taken from [other] first. */
    public fun merge(other: ZLTextStyle?): ZLTextStyle {
        if (other == null || other.isEmpty) return this
        if (isEmpty) return other
        return ZLTextStyle(
            textColor = other.textColor ?: textColor,
            backgroundColor = other.backgroundColor ?: backgroundColor,
            textSize = other.textSize ?: textSize,
            relativeSize = other.relativeSize ?: relativeSize,
            bold = other.bold ?: bold,
            italic = other.italic ?: italic,
            typeface = other.typeface ?: typeface,
            underline = other.underline ?: underline,
            strikethrough = other.strikethrough ?: strikethrough,
            letterSpacing = other.letterSpacing ?: letterSpacing,
            baselineShift = other.baselineShift ?: baselineShift,
            shadow = other.shadow ?: shadow,
            background = other.background ?: background,
            gradient = other.gradient ?: gradient,
            link = other.link ?: link,
            firstLineIndent = other.firstLineIndent ?: firstLineIndent,
            restLineIndent = other.restLineIndent ?: restLineIndent,
            alignment = other.alignment ?: alignment,
        )
    }

    /** Same as [merge]. */
    public operator fun plus(other: ZLTextStyle?): ZLTextStyle = merge(other)

    // Colour & decorations

    public fun color(@ColorInt color: Int): ZLTextStyle = copy(textColor = ZLColor.of(color))
    public fun colorRes(@ColorRes id: Int): ZLTextStyle = copy(textColor = ZLColor.res(id))
    public fun backgroundColor(@ColorInt color: Int): ZLTextStyle = copy(backgroundColor = ZLColor.of(color))
    public fun backgroundColorRes(@ColorRes id: Int): ZLTextStyle = copy(backgroundColor = ZLColor.res(id))
    public fun underline(): ZLTextStyle = copy(underline = true)
    public fun strikethrough(): ZLTextStyle = copy(strikethrough = true)

    /** Letter spacing in `em`, e.g. `0.1f`. */
    public fun kern(em: Float): ZLTextStyle = copy(letterSpacing = em)

    /** Moves the glyphs up by [dp] (negative moves down). */
    public fun baselineShift(dp: Float): ZLTextStyle = copy(baselineShift = ZLDimension.dp(dp))

    @JvmOverloads
    public fun shadow(@ColorInt color: Int, radiusDp: Float = 2f, dxDp: Float = 0f, dyDp: Float = 1f): ZLTextStyle =
        copy(shadow = ZLShadow(ZLColor.of(color), ZLDimension.dp(radiusDp), ZLDimension.dp(dxDp), ZLDimension.dp(dyDp)))

    public fun link(url: String): ZLTextStyle = copy(link = url)

    // Font

    public fun textSizeSp(size: Float): ZLTextStyle = copy(textSize = ZLDimension.sp(size))
    public fun textSizeDp(size: Float): ZLTextStyle = copy(textSize = ZLDimension.dp(size))
    public fun textSizePx(size: Float): ZLTextStyle = copy(textSize = ZLDimension.px(size))

    /** Text size relative to the surrounding text, e.g. `0.8f`. */
    public fun relativeSize(proportion: Float): ZLTextStyle = copy(relativeSize = proportion)
    public fun bold(): ZLTextStyle = copy(bold = true)
    public fun italic(): ZLTextStyle = copy(italic = true)
    public fun typeface(typeface: Typeface): ZLTextStyle = copy(typeface = typeface)
    public fun monospace(): ZLTextStyle = copy(typeface = Typeface.MONOSPACE)

    // Decorations drawn by the view

    public fun background(background: ZLTextBackground): ZLTextStyle = copy(background = background)

    /** Light capsule background ([ZLTextBackground.capsule]). */
    public fun capsule(@ColorInt color: Int): ZLTextStyle = copy(background = ZLTextBackground.capsule(color))

    /** Inline code background ([ZLTextBackground.CODE]). */
    public fun codeBackground(): ZLTextStyle = copy(background = ZLTextBackground.CODE)

    public fun gradient(gradient: ZLTextGradient): ZLTextStyle = copy(gradient = gradient)

    /** Left to right gradient (top to bottom when [vertical]). */
    @JvmOverloads
    public fun gradientColors(@ColorInt colors: IntArray, vertical: Boolean = false): ZLTextStyle =
        copy(gradient = ZLTextGradient.of(colors, vertical))

    // Paragraph

    /** Indent of the first line (and of the other lines) of the paragraphs, in dp. */
    @JvmOverloads
    public fun indent(firstLineDp: Float, restLinesDp: Float = 0f): ZLTextStyle =
        copy(firstLineIndent = ZLDimension.dp(firstLineDp), restLineIndent = ZLDimension.dp(restLinesDp))

    public fun alignment(alignment: Layout.Alignment): ZLTextStyle = copy(alignment = alignment)

    public companion object {
        /** The empty style; as a press highlight it means "no highlight". */
        @JvmField
        public val NONE: ZLTextStyle = ZLTextStyle()
    }
}
