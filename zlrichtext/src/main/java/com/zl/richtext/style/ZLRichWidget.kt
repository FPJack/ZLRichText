package com.zl.richtext.style

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.view.View
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import kotlin.math.roundToInt

/** Vertical alignment of an inline widget relative to the text. */
public enum class ZLWidgetAlignment {
    /** Bottom on the baseline. */
    BASELINE,

    /** Centre on the centre of the text (default). */
    CENTER,

    /** Top on the ascent. */
    TOP,

    /** Bottom on the descent. */
    BOTTOM,

    /** Top on the baseline. */
    BELOW_BASELINE,
}

/** Where a widget returned by `tagWidgetBuilder` goes relative to the tag text. */
public enum class ZLWidgetPosition { REPLACE, BEFORE, AFTER }

/**
 * Inline content of the text: icons, images, badges (the `NSTextAttachment` / inline view of
 * iOS). The content is a [Drawable]; [ZLRichWidget.view] renders a view into one.
 *
 * * [tintWithTextColor] tints the drawable with the colour of the text at its position, so
 *   icons follow the text colour and the press highlight colour (iOS template images).
 * * Without [onClick] a widget belongs to its rule match / tag: tapping it triggers their tap.
 *   With [onClick] it handles its own taps (checkboxes, follow buttons …), like `tappable: false`
 *   views on iOS.
 * * Live, interactive child views are not supported: a TextView cannot host child views. Change
 *   your state and render again to update the widget (the text reflows automatically).
 */
public class ZLRichWidget @JvmOverloads constructor(
    public val drawable: Drawable,
    /** Width in pixels. */
    public val width: Int,
    /** Height in pixels. */
    public val height: Int,
    public val alignment: ZLWidgetAlignment = ZLWidgetAlignment.CENTER,
    /** Only used by `ZLTagTextView.tagWidgetBuilder`. */
    public val position: ZLWidgetPosition = ZLWidgetPosition.REPLACE,
    public val tintWithTextColor: Boolean = false,
    /** Space before / after the widget, in pixels. */
    public val marginStart: Int = 0,
    public val marginEnd: Int = 0,
    /** Own tap handling; `null` lets the tap reach the rule match / tag. */
    public val onClick: Runnable? = null,
) {
    /** A copy with other values. */
    @JvmOverloads
    public fun copy(
        alignment: ZLWidgetAlignment = this.alignment,
        position: ZLWidgetPosition = this.position,
        marginStart: Int = this.marginStart,
        marginEnd: Int = this.marginEnd,
        onClick: Runnable? = this.onClick,
    ): ZLRichWidget = ZLRichWidget(drawable, width, height, alignment, position, tintWithTextColor, marginStart, marginEnd, onClick)

    public companion object {
        /** A drawable resource of [sizeDp] × [sizeDp]; tinted with the text colour by default. */
        @JvmStatic
        @JvmOverloads
        public fun icon(
            context: Context,
            @DrawableRes id: Int,
            sizeDp: Float = 16f,
            tintWithTextColor: Boolean = true,
            alignment: ZLWidgetAlignment = ZLWidgetAlignment.CENTER,
        ): ZLRichWidget {
            val drawable = requireNotNull(AppCompatResources.getDrawable(context, id)) { "Drawable $id not found" }.mutate()
            val size = (sizeDp * context.resources.displayMetrics.density).roundToInt()
            return ZLRichWidget(drawable, size, size, alignment, tintWithTextColor = tintWithTextColor)
        }

        /** A drawable at [widthPx] × [heightPx] (its intrinsic size when not given). */
        @JvmStatic
        @JvmOverloads
        public fun drawable(
            drawable: Drawable,
            widthPx: Int = drawable.intrinsicWidth,
            heightPx: Int = drawable.intrinsicHeight,
            alignment: ZLWidgetAlignment = ZLWidgetAlignment.CENTER,
        ): ZLRichWidget = ZLRichWidget(drawable, widthPx, heightPx, alignment)

        /**
         * Renders [view] into a bitmap (a snapshot: the view does not stay interactive). The
         * view is measured with its own size unless [widthPx] / [heightPx] are given.
         */
        @JvmStatic
        @JvmOverloads
        public fun view(
            view: View,
            widthPx: Int = 0,
            heightPx: Int = 0,
            alignment: ZLWidgetAlignment = ZLWidgetAlignment.CENTER,
        ): ZLRichWidget {
            fun spec(size: Int) = if (size > 0) {
                View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
            } else {
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            }
            view.measure(spec(widthPx), spec(heightPx))
            val width = view.measuredWidth.coerceAtLeast(1)
            val height = view.measuredHeight.coerceAtLeast(1)
            view.layout(0, 0, width, height)
            val bitmap = createBitmap(width, height)
            view.draw(Canvas(bitmap))
            val drawable = bitmap.toDrawable(view.resources)
            return ZLRichWidget(drawable, width, height, alignment)
        }
    }
}
