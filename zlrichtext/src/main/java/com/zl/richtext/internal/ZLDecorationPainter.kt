package com.zl.richtext.internal

import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.text.Layout
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import android.widget.TextView
import kotlin.math.max
import kotlin.math.min

/** Layout queries shared by hit testing, decorations and the public geometry API. */
internal object ZLTextGeometry {

    private val path = Path()
    private val lineArea = Path()
    private val bounds = RectF()

    /**
     * A static layout with the line breaking parameters of [view]. [maxLines] stops line
     * breaking early when only the first lines matter.
     */
    fun layout(view: TextView, text: CharSequence, width: Int, maxLines: Int = Int.MAX_VALUE): StaticLayout {
        val builder = StaticLayout.Builder.obtain(text, 0, text.length, view.paint, max(width, 0))
            .setMaxLines(maxLines)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(view.lineSpacingExtra, view.lineSpacingMultiplier)
            .setIncludePad(view.includeFontPadding)
            .setBreakStrategy(view.breakStrategy)
            .setHyphenationFrequency(view.hyphenationFrequency)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) builder.setJustificationMode(view.justificationMode)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) builder.setUseLineSpacingFromFallbacks(view.isFallbackLineSpacing)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setTextDirection(view.textDirectionHeuristic)
        return builder.build()
    }

    /** Bounds of `[start, end)` on one line (union of the bidi runs), in layout coordinates. */
    fun horizontalBounds(layout: Layout, start: Int, end: Int): RectF? {
        if (start >= end) return null
        path.reset()
        layout.getSelectionPath(start, end, path)
        // When `end` is the end of a wrapped line it belongs to the next line, and the selection
        // path adds a fill to the view edge plus an empty rect at the start of the next line:
        // keep only the glyph area of this line.
        val line = layout.getLineForOffset(start)
        lineArea.reset()
        lineArea.addRect(
            layout.getLineLeft(line), layout.getLineTop(line).toFloat(),
            layout.getLineRight(line), layout.getLineBottom(line).toFloat(),
            Path.Direction.CW,
        )
        path.op(lineArea, Path.Op.INTERSECT)
        path.computeBounds(bounds, true)
        return if (bounds.width() <= 0f) null else RectF(bounds)
    }

    /** The character under `(x, y)` (layout coordinates), `null` when the point is not on a glyph. */
    fun characterAt(layout: Layout, x: Float, y: Float): Int? {
        if (y < 0 || y > layout.height) return null
        val line = layout.getLineForVertical(y.toInt())
        val top = layout.getLineTop(line)
        // The extra line spacing below a line is not "on the text".
        val bottom = layout.getLineBottom(line) - if (line < layout.lineCount - 1) layout.spacingAdd.toInt() else 0
        if (y < top || y > bottom) return null
        val offset = layout.getOffsetForHorizontal(line, x)
        val lineStart = layout.getLineStart(line)
        val lineEnd = layout.getLineEnd(line)
        for (candidate in intArrayOf(offset, offset - 1, offset + 1)) {
            if (candidate < lineStart || candidate >= lineEnd) continue
            val rect = horizontalBounds(layout, candidate, candidate + 1) ?: continue
            if (x >= rect.left - 0.5f && x <= rect.right + 0.5f) return candidate
        }
        return null
    }

    /** Glyph area of `[start, end)` per line: text ascent / descent, not the full line height. */
    fun lineRects(layout: Layout, text: Spanned, start: Int, end: Int, basePaint: TextPaint, scratch: TextPaint): List<RectF> {
        if (start >= end) return emptyList()
        val result = ArrayList<RectF>()
        val first = layout.getLineForOffset(start)
        val last = layout.getLineForOffset(max(start, end - 1))
        for (line in first..last) {
            val s = max(start, layout.getLineStart(line))
            val e = min(end, layout.getLineEnd(line))
            val horizontal = horizontalBounds(layout, s, e) ?: continue
            // Font metrics of the text at the start of the range on this line.
            scratch.set(basePaint)
            for (span in text.getSpans(s, s + 1, MetricAffectingSpan::class.java)) span.updateMeasureState(scratch)
            val metrics = scratch.fontMetrics
            val baseline = layout.getLineBaseline(line) + scratch.baselineShift
            result += RectF(horizontal.left, baseline + metrics.ascent, horizontal.right, baseline + metrics.descent)
        }
        return result
    }
}

/** Draws capsule backgrounds (below the text) and gradients (on top of it). */
internal class ZLDecorationPainter {
    private val scratch = TextPaint()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun drawBackgrounds(canvas: Canvas, layout: Layout, text: Spanned, paint: TextPaint, clip: RectF) {
        val spans = text.getSpans(0, text.length, ZLBackgroundSpan::class.java)
        if (spans.isEmpty()) return
        val highlights = spans.filter { it.isHighlight }
        for (span in spans) {
            if (span.isHighlight) continue
            val start = text.getSpanStart(span)
            val end = text.getSpanEnd(span)
            // A pressed capsule takes the colours of the highlight over it.
            val style = highlights.lastOrNull { text.getSpanStart(it) <= start && text.getSpanEnd(it) >= end } ?: span
            for (line in ZLTextGeometry.lineRects(layout, text, start, end, paint, scratch)) {
                val rect = RectF(
                    line.left - span.paddingHorizontal,
                    line.top - span.paddingVertical,
                    line.right + span.paddingHorizontal,
                    line.bottom + span.paddingVertical,
                )
                if (!rect.intersect(clip)) continue
                val inset = if (style.strokeColor != null) style.strokeWidth / 2 else 0f
                rect.inset(inset, inset)
                val radius = style.cornerRadius?.let { min(it, rect.height() / 2) } ?: (rect.height() / 2)
                style.fillColor?.let {
                    fill.color = it
                    canvas.drawRoundRect(rect, radius, radius, fill)
                }
                style.strokeColor?.let {
                    if (style.strokeWidth > 0) {
                        stroke.color = it
                        stroke.strokeWidth = style.strokeWidth
                        canvas.drawRoundRect(rect, radius, radius, stroke)
                    }
                }
            }
        }
    }

    /**
     * Gradient text: draws the gradient glyphs opaque into a layer (clipped to their lines),
     * then fills each line segment with its gradient using `SRC_IN`.
     */
    fun drawGradients(canvas: Canvas, layout: Layout, text: Spanned, paint: TextPaint) {
        val spans = text.getSpans(0, text.length, ZLGradientSpan::class.java)
        if (spans.isEmpty()) return
        val segments = spans.map { span ->
            span to ZLTextGeometry.lineRects(layout, text, text.getSpanStart(span), text.getSpanEnd(span), paint, scratch)
        }
        val clipPath = Path()
        val union = RectF()
        for ((_, rects) in segments) for (rect in rects) {
            val padded = RectF(rect.left - 2, rect.top - 2, rect.right + 2, rect.bottom + 2)
            clipPath.addRect(padded, Path.Direction.CW)
            union.union(padded)
        }
        if (union.isEmpty) return
        val save = canvas.saveLayer(union, null)
        canvas.clipPath(clipPath)
        ZLGradientSpan.maskPass = true
        try {
            layout.draw(canvas)
        } finally {
            ZLGradientSpan.maskPass = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            gradientPaint.blendMode = BlendMode.SRC_IN
        } else {
            @Suppress("DEPRECATION")
            gradientPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        }
        for ((span, rects) in segments) for (rect in rects) {
            gradientPaint.shader = LinearGradient(
                rect.left + span.startX * rect.width(), rect.top + span.startY * rect.height(),
                rect.left + span.endX * rect.width(), rect.top + span.endY * rect.height(),
                span.colors, span.positions, Shader.TileMode.CLAMP,
            )
            canvas.drawRect(RectF(rect.left - 2, rect.top - 2, rect.right + 2, rect.bottom + 2), gradientPaint)
        }
        gradientPaint.shader = null
        canvas.restoreToCount(save)
    }
}
