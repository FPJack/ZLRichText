package com.zl.richtext.style

import android.content.Context
import android.util.TypedValue
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat

/** A length with a unit, converted to pixels when the text is rendered. */
public data class ZLDimension(val value: Float, val unit: Int) {
    public fun toPx(context: Context): Float = TypedValue.applyDimension(unit, value, context.resources.displayMetrics)

    public companion object {
        @JvmStatic public fun px(value: Float): ZLDimension = ZLDimension(value, TypedValue.COMPLEX_UNIT_PX)
        @JvmStatic public fun dp(value: Float): ZLDimension = ZLDimension(value, TypedValue.COMPLEX_UNIT_DIP)
        @JvmStatic public fun sp(value: Float): ZLDimension = ZLDimension(value, TypedValue.COMPLEX_UNIT_SP)
    }
}

/**
 * A colour: a fixed ARGB value or a colour resource. Resources are resolved when the text is
 * rendered, so day / night colours follow the current configuration.
 */
public sealed class ZLColor {
    public abstract fun resolve(context: Context): Int

    public data class Value(@ColorInt val argb: Int) : ZLColor() {
        override fun resolve(context: Context): Int = argb
    }

    public data class Resource(@ColorRes val id: Int) : ZLColor() {
        override fun resolve(context: Context): Int = ContextCompat.getColor(context, id)
    }

    public companion object {
        @JvmStatic public fun of(@ColorInt argb: Int): ZLColor = Value(argb)
        @JvmStatic public fun res(@ColorRes id: Int): ZLColor = Resource(id)
    }
}

/** Shadow of the glyphs. */
public data class ZLShadow(val color: ZLColor, val radius: ZLDimension, val dx: ZLDimension, val dy: ZLDimension)

/**
 * Rounded background drawn behind a piece of text: capsules for `#topic#`, inline `code`,
 * badges, outlined tags …
 *
 * * The horizontal [padding] and [margin] take up layout space, so the background never
 *   overlaps neighbouring text.
 * * The vertical padding only affects drawing and does not change the line height; add line
 *   spacing when the background is tall.
 * * A background that wraps over several lines is drawn line by line.
 */
public data class ZLTextBackground @JvmOverloads constructor(
    /** Fill colour, `null` for no fill. */
    val fillColor: ZLColor? = null,
    /** Border colour, `null` for no border. */
    val strokeColor: ZLColor? = null,
    val strokeWidth: ZLDimension = ZLDimension.dp(1f),
    /** Corner radius; `null` means half of the height (capsule). */
    val cornerRadius: ZLDimension? = null,
    val paddingHorizontal: ZLDimension = ZLDimension.dp(6f),
    val paddingVertical: ZLDimension = ZLDimension.dp(1f),
    /** Horizontal distance to the neighbouring text. */
    val margin: ZLDimension = ZLDimension.dp(2f),
) {
    public companion object {
        /** Light capsule tinted with [color] (12% alpha), fully rounded ends. */
        @JvmStatic
        public fun capsule(@ColorInt color: Int): ZLTextBackground =
            ZLTextBackground(fillColor = ZLColor.of((color and 0x00FFFFFF) or (0x1F shl 24)))

        /** Inline code: grey fill, hairline border, small corner radius. */
        @JvmField
        public val CODE: ZLTextBackground = ZLTextBackground(
            fillColor = ZLColor.of(0x1F787880),
            strokeColor = ZLColor.of(0x4A3C3C43),
            strokeWidth = ZLDimension.dp(0.5f),
            cornerRadius = ZLDimension.dp(4f),
            paddingHorizontal = ZLDimension.dp(4f),
            margin = ZLDimension.dp(1f),
        )
    }
}

/**
 * Linear gradient filling the glyphs, drawn line by line. Points are unit coordinates inside
 * each line segment of the text: `(0, 0.5) → (1, 0.5)` is left to right.
 */
public data class ZLTextGradient @JvmOverloads constructor(
    val colors: List<ZLColor>,
    val positions: List<Float>? = null,
    val startX: Float = 0f,
    val startY: Float = 0.5f,
    val endX: Float = 1f,
    val endY: Float = 0.5f,
) {
    public companion object {
        /** Left to right, or top to bottom when [vertical]. */
        @JvmStatic
        @JvmOverloads
        public fun of(@ColorInt colors: IntArray, vertical: Boolean = false): ZLTextGradient = ZLTextGradient(
            colors = colors.map { ZLColor.of(it) },
            startX = if (vertical) 0.5f else 0f,
            startY = if (vertical) 0f else 0.5f,
            endX = if (vertical) 0.5f else 1f,
            endY = if (vertical) 1f else 0.5f,
        )
    }
}
