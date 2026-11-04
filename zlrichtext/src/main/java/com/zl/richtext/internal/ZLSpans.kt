package com.zl.richtext.internal

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Spannable
import android.text.Spanned
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.AlignmentSpan
import android.text.style.BackgroundColorSpan
import android.text.style.CharacterStyle
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.MetricAffectingSpan
import android.text.style.RelativeSizeSpan
import android.text.style.ReplacementSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.text.style.UpdateAppearance
import android.view.View
import com.zl.richtext.style.ZLRichWidget
import com.zl.richtext.style.ZLTextStyle
import com.zl.richtext.style.ZLWidgetAlignment
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Spans of the library that only carry data for the view (no paint effect). */
internal interface ZLDecorationSpan

/** Typeface keeping the current style (bold / italic). */
internal class ZLTypefaceSpan(private val typeface: Typeface) : MetricAffectingSpan() {
    override fun updateMeasureState(paint: TextPaint) = apply(paint)
    override fun updateDrawState(paint: TextPaint) = apply(paint)

    private fun apply(paint: TextPaint) {
        val style = paint.typeface?.style ?: Typeface.NORMAL
        paint.typeface = Typeface.create(typeface, style)
    }
}

internal class ZLLetterSpacingSpan(private val em: Float) : MetricAffectingSpan() {
    override fun updateMeasureState(paint: TextPaint) {
        paint.letterSpacing = em
    }

    override fun updateDrawState(paint: TextPaint) {
        paint.letterSpacing = em
    }
}

/** Moves the glyphs up by [px] without changing the line height. */
internal class ZLBaselineShiftSpan(val px: Float) : MetricAffectingSpan() {
    override fun updateMeasureState(paint: TextPaint) {
        paint.baselineShift -= px.roundToInt()
    }

    override fun updateDrawState(paint: TextPaint) {
        paint.baselineShift -= px.roundToInt()
    }
}

internal class ZLShadowSpan(
    private val radius: Float,
    private val dx: Float,
    private val dy: Float,
    private val color: Int,
) : CharacterStyle(), UpdateAppearance {
    override fun updateDrawState(paint: TextPaint) {
        paint.setShadowLayer(max(radius, 0.01f), dx, dy, color)
    }
}

/** Capsule background of a range, drawn by the view (each span instance is one capsule). */
internal class ZLBackgroundSpan(
    val fillColor: Int?,
    val strokeColor: Int?,
    val strokeWidth: Float,
    /** `null`: half of the height. */
    val cornerRadius: Float?,
    val paddingHorizontal: Float,
    val paddingVertical: Float,
    val margin: Float,
    /** Press highlight replacing the capsule below it. */
    val isHighlight: Boolean,
) : ZLDecorationSpan

/**
 * Gradient text: transparent in the normal pass, opaque in the mask pass the view draws
 * before filling the gradient.
 */
internal class ZLGradientSpan(
    val colors: IntArray,
    val positions: FloatArray?,
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
) : CharacterStyle(), UpdateAppearance, ZLDecorationSpan {
    override fun updateDrawState(paint: TextPaint) {
        paint.color = if (maskPass) Color.BLACK else Color.TRANSPARENT
    }

    companion object {
        /** Set by the view while drawing the gradient mask (UI thread only). */
        @JvmStatic
        var maskPass: Boolean = false
    }
}

/** Empty space reserved for the horizontal padding of a capsule. */
internal class ZLSpacerSpan(val width: Int) : ReplacementSpan() {
    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int = width

    override fun draw(
        canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint,
    ) = Unit
}

/** An inline widget. */
internal class ZLWidgetSpan(val widget: ZLRichWidget) : ReplacementSpan() {
    private val scratch = TextPaint()

    /** Offsets of the widget top / bottom from the baseline. */
    private fun verticalOffsets(fm: Paint.FontMetricsInt): Pair<Int, Int> {
        val h = widget.height
        val top = when (widget.alignment) {
            ZLWidgetAlignment.BASELINE -> -h
            ZLWidgetAlignment.CENTER -> (fm.ascent + fm.descent) / 2 - h / 2
            ZLWidgetAlignment.TOP -> fm.ascent
            ZLWidgetAlignment.BOTTOM -> fm.descent - h
            ZLWidgetAlignment.BELOW_BASELINE -> 0
        }
        return top to top + h
    }

    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        if (fm != null) {
            val metrics = paint.fontMetricsInt
            val (top, bottom) = verticalOffsets(metrics)
            fm.ascent = min(metrics.ascent, top)
            fm.descent = max(metrics.descent, bottom)
            fm.top = min(metrics.top, fm.ascent)
            fm.bottom = max(metrics.bottom, fm.descent)
            fm.leading = metrics.leading
        }
        return widget.marginStart + widget.width + widget.marginEnd
    }

    override fun draw(
        canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint,
    ) {
        val (offsetTop, _) = verticalOffsets(paint.fontMetricsInt)
        val drawable = widget.drawable
        val left = (x + widget.marginStart).roundToInt()
        val drawTop = y + offsetTop
        drawable.setBounds(left, drawTop, left + widget.width, drawTop + widget.height)
        if (widget.tintWithTextColor) drawable.setTint(textColorAt(text, start, paint))
        drawable.draw(canvas)
    }

    /** Colour of the text at the widget: replacement spans do not receive character styles. */
    private fun textColorAt(text: CharSequence?, start: Int, paint: Paint): Int {
        scratch.set(paint as? TextPaint ?: TextPaint(paint))
        if (text is Spanned) {
            for (span in text.getSpans(start, start + 1, CharacterStyle::class.java)) span.updateDrawState(scratch)
        }
        return scratch.color
    }
}

/** A link exposed to accessibility services; taps are handled by the view itself. */
internal class ZLLinkSpan(val action: Runnable) : ClickableSpan() {
    override fun onClick(widget: View) = action.run()

    override fun updateDrawState(ds: TextPaint) = Unit
}

/** Link target of [ZLTextStyle.link]. */
internal class ZLLinkMarkerSpan(val url: String) : ZLDecorationSpan

/** Converts [ZLTextStyle] into Android spans. */
internal object ZLStyleSpans {

    /** Spans of [style]; [highlight] marks capsules that replace the one below them. */
    fun create(style: ZLTextStyle, context: Context, highlight: Boolean = false): List<Any> {
        if (style.isEmpty) return emptyList()
        val spans = ArrayList<Any>()
        style.textColor?.let { spans += ForegroundColorSpan(it.resolve(context)) }
        style.backgroundColor?.let { spans += BackgroundColorSpan(it.resolve(context)) }
        style.textSize?.let { spans += AbsoluteSizeSpan(it.toPx(context).roundToInt()) }
        style.relativeSize?.let { spans += RelativeSizeSpan(it) }
        style.typeface?.let { spans += ZLTypefaceSpan(it) }
        val bold = style.bold == true
        val italic = style.italic == true
        if (bold || italic) {
            spans += StyleSpan(
                when {
                    bold && italic -> Typeface.BOLD_ITALIC
                    bold -> Typeface.BOLD
                    else -> Typeface.ITALIC
                },
            )
        }
        if (style.underline == true) spans += UnderlineSpan()
        if (style.strikethrough == true) spans += StrikethroughSpan()
        style.letterSpacing?.let { spans += ZLLetterSpacingSpan(it) }
        style.baselineShift?.let { spans += ZLBaselineShiftSpan(it.toPx(context)) }
        style.shadow?.let {
            spans += ZLShadowSpan(it.radius.toPx(context), it.dx.toPx(context), it.dy.toPx(context), it.color.resolve(context))
        }
        style.background?.let {
            spans += ZLBackgroundSpan(
                fillColor = it.fillColor?.resolve(context),
                strokeColor = it.strokeColor?.resolve(context),
                strokeWidth = it.strokeWidth.toPx(context),
                cornerRadius = it.cornerRadius?.toPx(context),
                paddingHorizontal = it.paddingHorizontal.toPx(context),
                paddingVertical = it.paddingVertical.toPx(context),
                margin = it.margin.toPx(context),
                isHighlight = highlight,
            )
        }
        style.gradient?.let { g ->
            spans += ZLGradientSpan(
                colors = g.colors.map { it.resolve(context) }.let { if (it.size == 1) it + it else it }.toIntArray(),
                positions = g.positions?.toFloatArray(),
                startX = g.startX, startY = g.startY, endX = g.endX, endY = g.endY,
            )
        }
        style.link?.let { spans += ZLLinkMarkerSpan(it) }
        if (style.firstLineIndent != null || style.restLineIndent != null) {
            spans += LeadingMarginSpan.Standard(
                style.firstLineIndent?.toPx(context)?.roundToInt() ?: 0,
                style.restLineIndent?.toPx(context)?.roundToInt() ?: 0,
            )
        }
        style.alignment?.let { spans += AlignmentSpan.Standard(it) }
        return spans
    }

    /** Applies [style] to `[start, end)`; returns the spans so they can be removed again. */
    fun apply(
        text: Spannable,
        style: ZLTextStyle,
        start: Int,
        end: Int,
        context: Context,
        priority: Int = 0,
        highlight: Boolean = false,
    ): List<Any> {
        if (start >= end) return emptyList()
        val flags = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE or ((priority shl Spanned.SPAN_PRIORITY_SHIFT) and Spanned.SPAN_PRIORITY)
        val spans = create(style, context, highlight)
        for (span in spans) text.setSpan(span, start, end, flags)
        return spans
    }
}
