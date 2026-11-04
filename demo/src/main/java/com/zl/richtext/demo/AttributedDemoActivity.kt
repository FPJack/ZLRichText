package com.zl.richtext.demo

import android.app.AlertDialog
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.zl.richtext.ZLAttributedTextView
import com.zl.richtext.ZLLink
import com.zl.richtext.ZLLinkListener
import com.zl.richtext.ZLTruncationMode
import com.zl.richtext.detect.ZLDataDetectorType
import com.zl.richtext.style.ZLRichWidget
import com.zl.richtext.style.ZLTextBuilder
import com.zl.richtext.style.ZLTextStyle
import com.zl.richtext.style.ZLWidgetAlignment
import com.zl.richtext.style.zlStyled
import java.text.BreakIterator

/**
 * Every feature of the base `ZLAttributedTextView` (no rules or tags: text and links are set
 * directly). Port of the iOS `AttributedLabelDemoPage`:
 *  1. plain text following the TextView attributes
 *  2. styled runs: colour, font, underline, background, strikethrough, kern, shadow, indent
 *  3. padding + vertical alignment (gravity)
 *  4. line limit and truncation (head / middle / tail, custom token)
 *  5. links: `link` runs, URL links, tap callbacks, custom link styles
 *  6. data detectors (URL / phone / date / address)
 *  7. long press on links
 *  8. larger touch areas (`extendsLinkTouchArea`)
 *  9. inactive link style while a dialog is shown
 * 10. highlighted text colour
 * 11. geometry: `characterIndexAt`, `boundingRectForRange`, measuring
 * 12. accessibility: every link is a node for TalkBack
 * 13. font fitting: `adjustsFontSizeToFit` + `minimumScaleFactor`
 * 14. inline widgets (the `NSTextAttachment` images of iOS)
 */
class AttributedDemoActivity : DemoActivity() {
    private lateinit var event: TextView
    private lateinit var column: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        title = "ZLAttributedTextView"
        super.onCreate(savedInstanceState)
    }

    override fun createContent(): View {
        event = label("点击 / 长按链接，这里显示回调", sizeSp = 12f, color = Color.BLACK, mono = true).apply {
            setBackgroundColor(0xFFF3EDF7.toInt())
            minHeight = dp(44f)
            setPadding(dp(12f), dp(8f), dp(12f), dp(8f))
        }
        column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), dp(16f), dp(16f), dp(24f))
        }
        demoPlainText()
        demoStyledRuns()
        demoPaddingAndGravity()
        demoTruncation()
        demoLinks()
        demoDataDetectors()
        demoLongPress()
        demoExtendedTouchArea()
        demoInactiveLinks()
        demoHighlighted()
        demoGeometry()
        demoAccessibility()
        demoFontFitting()
        demoInlineWidgets()
        val scroll = ScrollView(this).apply {
            isFocusableInTouchMode = true
            descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS
            addView(column)
        }
        return vertical(event, scroll, spacing = 0).apply {
            (scroll.layoutParams as LinearLayout.LayoutParams).apply {
                height = 0
                weight = 1f
            }
        }
    }

    private fun log(text: String) {
        event.text = text
        android.util.Log.d("ZLRichText", "[ZLAttributedTextView] $text")
    }

    /** A card: title, grey detail, content views. */
    private fun addCard(title: String, detail: String, vararg views: View) {
        val card = CardView(this)
        card.add(label(title, sizeSp = 15f, color = Color.BLACK, bold = true))
        card.add(label(detail, sizeSp = 12f, color = 0x80000000.toInt()), dp(6f))
        views.forEach { card.add(it, dp(8f)) }
        column.addView(card, matchWidth().apply { if (column.childCount > 0) topMargin = dp(16f) })
    }

    private fun newLabel(lines: Int = 0): ZLAttributedTextView = ZLAttributedTextView(this).apply {
        textSize = 15f
        setTextColor(0xFF212121.toInt())
        if (lines > 0) maxLines = lines
        onLinkClickListener = ZLLinkListener { view, link -> onLinkClick(view, link) }
        onLinkLongClickListener = ZLLinkListener { view, link ->
            log("onLinkLongClickListener「${view.contentText.subSequence(link.start, link.end)}」")
        }
    }

    /** The counterpart of the iOS delegate callbacks. */
    private fun onLinkClick(view: ZLAttributedTextView, link: ZLLink) {
        val text = view.contentText.subSequence(link.start, link.end)
        val result = link.result
        val type = when (result?.type) {
            ZLDataDetectorType.PHONE_NUMBER -> "电话 ${result.phoneNumber}"
            ZLDataDetectorType.DATE -> "日期 ${result.date}"
            ZLDataDetectorType.ADDRESS -> "地址"
            ZLDataDetectorType.EMAIL -> "邮箱"
            ZLDataDetectorType.LINK -> "链接 ${result.url}"
            null -> link.url?.let { "URL $it" } ?: "链接"
        }
        log("onLinkClickListener：$type「$text」")
    }

    private fun ZLAttributedTextView.range(text: String): Pair<Int, Int> {
        val start = contentText.indexOf(text)
        return start to start + text.length
    }

    // MARK: - 1. 纯文本

    private fun demoPlainText() {
        val view = newLabel().apply {
            text = "通过 text 设置纯文本：使用 TextView 的 textSize、textColor、gravity、typeface。之后修改这些属性，文字会自动按新属性重新排版。"
        }
        addCard(
            "1. 纯文本 text", "view.text = \"...\"；修改 textSize / textColor / gravity 后自动更新",
            segmented(listOf("15", "18 粗体", "13"), 0) {
                view.textSize = listOf(15f, 18f, 13f)[it]
                view.setTypeface(null, if (it == 1) Typeface.BOLD else Typeface.NORMAL)
            },
            segmented(listOf("深灰", "紫色", "橙色"), 0) { view.setTextColor(listOf(0xFF212121.toInt(), M.DEEP_PURPLE, M.DEEP_ORANGE)[it]) },
            segmented(listOf("左", "中", "右"), 0) { view.gravity = listOf(Gravity.START, Gravity.CENTER_HORIZONTAL, Gravity.END)[it] },
            view,
        )
    }

    // MARK: - 2. 富文本

    private fun demoStyledRuns() {
        val text = ZLTextBuilder(this)
            .append("颜色", ZLTextStyle().color(M.PINK)).append("、")
            .append("粗体", ZLTextStyle().bold()).append("、")
            .append("大字号", ZLTextStyle().textSizeSp(22f)).append("、")
            .append("下划线", ZLTextStyle().underline()).append("、")
            .append("背景色", ZLTextStyle().backgroundColor(M.alpha(M.AMBER, 0.4f))).append("、")
            .append("删除线", ZLTextStyle().strikethrough()).append("、")
            .append("红色删除线", ZLTextStyle().strikethrough().color(M.RED)).append("、")
            .append("字间距 kern", ZLTextStyle().kern(0.25f)).append("、")
            .append("阴影", ZLTextStyle().color(M.BLUE).shadow(0x55000000, 2f, 0f, 1f))
            .append("（文字阴影也可以用 TextView 的 shadowColor 作用于整段）。\n")
            .append(
                "第二段：首行缩进 30dp，长文字折行后可以看到 lineSpacingExtra 的行距效果，未设置字体颜色的部分使用 TextView 的 textSize / textColor。",
                ZLTextStyle().indent(30f),
            )
            .build()
        val view = newLabel().apply {
            setLineSpacing(dp(4f).toFloat(), 1f)
            this.text = text
        }
        addCard("2. 富文本属性", "ZLTextBuilder + ZLTextStyle 生成带样式的文字；段落缩进用 indent()，行距用 TextView 的 lineSpacingExtra", view)
    }

    // MARK: - 3. 内边距 + 垂直对齐

    private fun demoPaddingAndGravity() {
        val view = newLabel().apply {
            text = "padding 内边距：背景铺满整个 view，文字在内边距以内排版。view 固定高度 120dp，gravity 决定文字在垂直方向的位置。"
            background = GradientDrawable().apply {
                setColor(M.LIGHT_PURPLE)
                cornerRadius = dp(12f).toFloat()
            }
            setPadding(dp(16f), dp(12f), dp(16f), dp(12f))
            gravity = Gravity.TOP
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(120f))
        }
        val paddings = listOf(0 to 0, 16 to 12, 32 to 24)
        addCard(
            "3. 内边距 padding + 垂直对齐 gravity", "测量结果包含内边距；点击、截断、字符定位都按内边距偏移",
            segmented(listOf("0", "12 / 16", "24 / 32"), 1) {
                val (h, v) = paddings[it]
                view.setPadding(dp(h.toFloat()), dp(v.toFloat()), dp(h.toFloat()), dp(v.toFloat()))
            },
            segmented(listOf("top", "center", "bottom"), 0) { view.gravity = listOf(Gravity.TOP, Gravity.CENTER_VERTICAL, Gravity.BOTTOM)[it] },
            FrameLayout(this).apply { addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(120f))) },
        )
    }

    // MARK: - 4. 行数与截断

    private fun demoTruncation() {
        val view = newLabel(lines = 2).apply {
            text = "maxLines 限制行数，超出时按 truncationMode 截断：尾部（默认）、头部或中间。" +
                "truncationToken 可以自定义截断符，例如「…更多」。这段文字足够长，会被截断。"
        }
        addCard(
            "4. 行数与截断", "多行时只截断最后一行（尾部）；单行时头 / 中 / 尾都可用（也可以用 android:ellipsize）",
            segmented(listOf("1 行", "2 行", "不限"), 1) { view.maxLines = listOf(1, 2, Int.MAX_VALUE)[it] },
            segmented(listOf("尾部", "头部", "中间"), 0) { view.truncationMode = ZLTruncationMode.entries[it] },
            segmented(listOf("…", "…更多"), 0) {
                view.truncationToken = if (it == 0) null else zlStyled("…更多", ZLTextStyle().color(M.BLUE))
            },
            view,
        )
    }

    // MARK: - 5. 链接

    private fun demoLinks() {
        val text = ZLTextBuilder(this)
            .append("1) link 样式：")
            .append("Apple 官网", ZLTextStyle().link("https://www.apple.com"))
            .append("。2) addLink(url, start, end)：GitHub。3) addLink(start, end) { }：点我计数。4) 自定义样式的 ZLLink：橙色胶囊，按下变深。")
            .build()
        var count = 0
        val view = newLabel().apply {
            // 默认链接样式 / 按下样式
            linkStyle = ZLTextStyle().color(M.BLUE).underline()
            activeLinkStyle = ZLTextStyle().color(M.alpha(M.BLUE, 0.4f))
            this.text = text
            val (gs, ge) = range("GitHub")
            addLink("https://github.com/FPJack/ZLRichLabel", gs, ge)
            val (cs, ce) = range("点我计数")
            addLink(cs, ce) { _, _ -> log("addLink(start, end) 点击第 ${++count} 次") }
            val (os, oe) = range("橙色胶囊")
            addLink(ZLLink(
                os, oe,
                style = ZLTextStyle().color(Color.WHITE).backgroundColor(M.DEEP_ORANGE),
                activeStyle = ZLTextStyle().backgroundColor(0xFFBF360C.toInt()),
                onClick = { _, link -> log("自定义 ZLLink 点击，range = [${link.start}, ${link.end})") },
            ))
        }
        addCard("5. 链接", "URL 链接回调 onLinkClickListener；设置了 onClick 的链接只回调 onClick。后添加的链接优先响应", view)
    }

    // MARK: - 6. 自动识别

    private fun demoDataDetectors() {
        val view = newLabel().apply {
            dataDetectorTypes = setOf(ZLDataDetectorType.LINK, ZLDataDetectorType.PHONE_NUMBER, ZLDataDetectorType.DATE, ZLDataDetectorType.ADDRESS)
            linkStyle = ZLTextStyle().color(M.TEAL).underline()
            text = "网址 https://developer.android.com，电话 010-12345678，日期 2026年10月1日 下午3点，" +
                "地址 1 Infinite Loop, Cupertino, CA 95014。"
        }
        addCard("6. 自动识别 dataDetectorTypes", "识别网址 / 电话 / 日期 / 地址，点击回调 onLinkClickListener（link.result 为识别结果）", view)
    }

    // MARK: - 7. 长按

    private fun demoLongPress() {
        val view = newLabel().apply {
            text = "长按 这个链接 触发它自己的 onLongClick；长按 另一个链接 回调 onLinkLongClickListener。轻点仍然是点击。"
            val (s1, e1) = range("这个链接")
            addLink(ZLLink(
                s1, e1,
                onClick = { _, _ -> log("点击「这个链接」") },
                onLongClick = { _, _ -> log("onLongClick：长按「这个链接」") },
            ))
            // 第二个链接没有设置 onLongClick：长按回调 onLinkLongClickListener
            val (s2, e2) = range("另一个链接")
            addLink(s2, e2) { _, _ -> log("点击「另一个链接」") }
        }
        addCard("7. 长按链接", "长按只在按在链接上（或设置了长按回调）时生效，时长为系统长按时长", view)
    }

    // MARK: - 8. 扩大点击热区

    private fun demoExtendedTouchArea() {
        val view = newLabel().apply {
            setLineSpacing(dp(14f).toFloat(), 1f)
            text = "行距较大时，按在两行之间的空白处不容易点中链接。打开 extendsLinkTouchArea 后，" +
                "手指没有按在文字上时会向四周最多 15dp 查找 链接 ；按在普通文字上仍然只认这个字。"
            val (s, e) = range("链接 ；")
            addLink(s, e) { _, _ -> log("点中了小链接") }
        }
        addCard(
            "8. 扩大点击热区 extendsLinkTouchArea", "默认关闭；开启后点击小链接上下的空白也能命中",
            switchRow("extendsLinkTouchArea", false) {
                view.extendsLinkTouchArea = it
                log("extendsLinkTouchArea = $it")
            },
            view,
        )
    }

    // MARK: - 9. 失活样式

    private fun demoInactiveLinks() {
        val view = newLabel().apply {
            text = "弹出 dialog 时窗口失去焦点，链接 显示为 inactiveLinkStyle（灰色）。"
            inactiveLinkStyle = ZLTextStyle(underline = false).color(Color.GRAY)
            val (s, e) = range("链接")
            addLink(s, e) { _, _ -> log("点击失活示例链接") }
        }
        addCard(
            "9. 失活样式 inactiveLinkStyle", "为 null 时失活不改变样式；单个链接可用 ZLLink.inactiveStyle",
            textButton("弹出 dialog 查看失活样式") {
                AlertDialog.Builder(this).setTitle("链接变灰了吗？").setMessage("关闭后恢复").setNegativeButton("关闭", null).show()
            },
            view,
        )
    }

    // MARK: - 10. 高亮

    private fun demoHighlighted() {
        val view = newLabel().apply {
            text = zlStyled("isTextHighlighted = true 时，全部文字使用 highlightedTextColor（例如列表项选中时）。", ZLTextStyle().color(M.INDIGO))
            highlightedTextColor = Color.WHITE
        }
        val container = FrameLayout(this).apply {
            setPadding(dp(8f), dp(8f), dp(8f), dp(8f))
            addView(view)
        }
        addCard(
            "10. 高亮 highlightedTextColor", "与 UILabel 的 isHighlighted 一致",
            switchRow("isTextHighlighted", false) {
                view.isTextHighlighted = it
                container.background = if (it) GradientDrawable().apply {
                    setColor(M.INDIGO)
                    cornerRadius = dp(8f).toFloat()
                } else null
            },
            container,
        )
    }

    // MARK: - 11. 尺寸与定位

    /** Draws a rect over its child (the word found by the geometry API). */
    private class MarkerLayout(context: android.content.Context) : FrameLayout(context) {
        var marker: RectF? = null
            set(value) {
                field = value
                invalidate()
            }
        private val fill = Paint().apply { color = M.alpha(M.AMBER, 0.3f) }
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = M.DEEP_ORANGE
            strokeWidth = 2f
        }

        override fun dispatchDraw(canvas: Canvas) {
            super.dispatchDraw(canvas)
            marker?.let {
                canvas.drawRoundRect(it, 6f, 6f, fill)
                canvas.drawRoundRect(it, 6f, 6f, stroke)
            }
        }
    }

    private fun demoGeometry() {
        val marker = MarkerLayout(this)
        val view = newLabel().apply {
            text = "点击任意文字：characterIndexAt(x, y) 得到字符下标，boundingRectForRange(start, end) 框出这个字所在的词。测量时用 measure 计算高度。"
            setPadding(dp(6f), dp(6f), dp(6f), dp(6f))
            background = GradientDrawable().apply { setStroke(1, 0x1A000000) }
            setOnTouchListener { v, e ->
                if (e.actionMasked == MotionEvent.ACTION_UP) {
                    val label = v as ZLAttributedTextView
                    val index = label.characterIndexAt(e.x, e.y)
                    if (index < 0) {
                        marker.marker = null
                        log("characterIndexAt = -1（没有按在文字上）")
                    } else {
                        val text = label.contentText.toString()
                        val words = BreakIterator.getWordInstance().apply { setText(text) }
                        val end = words.following(index)
                        val start = words.previous()
                        marker.marker = label.boundingRectForRange(start, end)
                        log("characterIndexAt = $index，词「${text.substring(start, end)}」rect = ${marker.marker?.let { "(%.0f, %.0f, %.0f, %.0f)".format(it.left, it.top, it.right, it.bottom) }}")
                    }
                }
                true
            }
        }
        marker.addView(view)
        // Measuring: the height for a width
        val measured = newLabel().apply {
            text = "这个 view 由 measure(EXACTLY width, UNSPECIFIED) 计算高度（包含内边距），与 sizeThatFits 对应。"
            setBackgroundColor(0xFFF3EDF7.toInt())
            setPadding(dp(8f), dp(8f), dp(8f), dp(8f))
        }
        val result = label("", sizeSp = 11f, mono = true)
        measured.post {
            measured.measure(View.MeasureSpec.makeMeasureSpec(measured.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            result.text = "measure → ${measured.measuredWidth} × ${measured.measuredHeight} px，行数 ${measured.fullLineCount}"
        }
        addCard("11. 尺寸与定位", "measure 结果按「内容 + 宽度」缓存；characterIndexAt / boundingRectForRange 用于定位", marker, measured, result)
    }

    // MARK: - 12. 无障碍

    private fun demoAccessibility() {
        val view = newLabel().apply {
            text = "开启 TalkBack 后，这段文字是一个元素，用户协议 和 隐私政策 分别是可以单独聚焦、双击触发的链接。"
            val (s1, e1) = range("用户协议")
            addLink(s1, e1) { _, _ -> log("打开用户协议") }
            val (s2, e2) = range("隐私政策")
            addLink(s2, e2) { _, _ -> log("打开隐私政策") }
        }
        addCard(
            "12. 无障碍 TalkBack", "每个可点击的链接都以 ClickableSpan 暴露给 TalkBack，可以单独聚焦、双击触发",
            textButton("打印暴露给 TalkBack 的链接") {
                val spans = (view.text as android.text.Spanned).getSpans(0, view.text.length, android.text.style.ClickableSpan::class.java)
                log("${spans.size} 个链接：" + spans.joinToString(" | ") {
                    (view.text as android.text.Spanned).let { t -> t.subSequence(t.getSpanStart(it), t.getSpanEnd(it)) }
                })
            },
            view,
        )
    }

    // MARK: - 13. 字号自适应

    private fun demoFontFitting() {
        val single = newLabel(lines = 1).apply {
            text = "单行标题：宽度不够时整体缩小字号，链接 仍可点击"
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            adjustsFontSizeToFit = true
            minimumScaleFactor = 0.5f
            setBackgroundColor(0xFFF3EDF7.toInt())
            val (s, e) = range("链接")
            addLink(s, e) { view, _ -> log("缩小后点击链接，scale = %.2f".format(view.currentFontScale)) }
        }
        val multi = newLabel(lines = 2).apply {
            text = "多行（maxLines = 2）：超过 2 行时缩小字号，尽量在 2 行内显示完整；" +
                "缩到 minimumScaleFactor 仍放不下时按尾部截断。这段文字比较长，用来观察缩放效果。"
            textSize = 18f
            adjustsFontSizeToFit = true
            minimumScaleFactor = 0.5f
            setBackgroundColor(0xFFF3EDF7.toInt())
        }
        val fixed = newLabel().apply {
            text = "不限行数 + 固定高度 60dp：文字高度超过 view 高度时缩小字号。富文本中的各段字体按同一比例缩小。"
            textSize = 18f
            gravity = Gravity.TOP
            adjustsFontSizeToFit = true
            minimumScaleFactor = 0.4f
            setBackgroundColor(0xFFF3EDF7.toInt())
        }
        val labels = listOf(single, multi, fixed)
        val info = label("", sizeSp = 11f, color = Color.BLACK, mono = true)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(single, matchWidth())
            addView(multi, matchWidth().apply { topMargin = dp(8f) })
            addView(fixed, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60f)).apply { topMargin = dp(8f) })
        }
        fun refresh() = container.post {
            info.text = labels.mapIndexed { i, l ->
                "view${i + 1}: adjusts = ${l.adjustsFontSizeToFit}, min = %.1f, scale = %.2f".format(l.minimumScaleFactor, l.currentFontScale)
            }.joinToString("\n")
        }
        container.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> refresh() }
        addCard(
            "13. 字号自适应 adjustsFontSizeToFit", "单行按宽度、多行按行数与高度缩小字体，最小到 minimumScaleFactor（0 时不缩小）。切换宽度 / 开关 / 最小比例观察 scale",
            segmented(listOf("宽", "中", "窄"), 0) {
                container.setPadding(0, 0, dp(listOf(0f, 40f, 80f)[it]), 0)
            },
            switchRow("adjustsFontSizeToFit", true) { on ->
                labels.forEach { it.adjustsFontSizeToFit = on }
                refresh()
            },
            segmented(listOf("min 0.3", "0.5", "0.8", "0（不缩）"), 1) { index ->
                labels.forEach { it.minimumScaleFactor = listOf(0.3f, 0.5f, 0.8f, 0f)[index] }
                refresh()
            },
            container,
            info,
        )
    }

    // MARK: - 14. 行内 Widget

    /** Colourful badge drawable (keeps its own colours). */
    private fun badge(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 11f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(Color.WHITE)
        includeFontPadding = false
        setPadding(dp(7f), dp(3f), dp(7f), dp(3f))
        background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(M.DEEP_PURPLE, M.BLUE)).apply {
            cornerRadius = dp(10f).toFloat()
        }
    }

    private fun demoInlineWidgets() {
        val tapCount = label("点击 ❤️ 图标链接：0 次", sizeSp = 12f, color = Color.BLACK, mono = true)
        var taps = 0
        val view = newLabel()

        fun build(size: Float) {
            val heartStart: Int
            val text = ZLTextBuilder(this).apply {
                append("图标跟随文字颜色 ")
                appendWidget(DemoWidgets.icon(this@AttributedDemoActivity, R.drawable.ic_star, size), ZLTextStyle().color(M.DEEP_ORANGE))
                append("，彩色徽章按原色 ")
                val badgeView = badge("VIP")
                val widget = ZLRichWidget.view(badgeView)
                val height = dp(size)
                appendWidget(ZLRichWidget(widget.drawable, widget.width * height / widget.height, height, ZLWidgetAlignment.CENTER))
                append("，图标也可以作为链接：")
                heartStart = length
                appendWidget(DemoWidgets.icon(this@AttributedDemoActivity, R.drawable.ic_heart, size), ZLTextStyle().color(M.RED))
                append("（按下变浅）。图标变大时行高跟着变高，文字按图标宽度重新换行。")
            }.build()
            view.text = text
            view.addLink(ZLLink(
                heartStart, heartStart + 1,
                style = ZLTextStyle.NONE,
                activeStyle = ZLTextStyle().color(M.alpha(M.RED, 0.35f)),
                onClick = { _, _ ->
                    taps++
                    tapCount.text = "点击 ❤️ 图标链接：$taps 次"
                    log("点击图标链接 ❤️（第 $taps 次）")
                },
            ))
        }
        build(18f)

        // 截断符中的图标
        val truncated = newLabel(lines = 1).apply {
            truncationToken = ZLTextBuilder(this@AttributedDemoActivity)
                .append("… ", ZLTextStyle().color(M.BLUE))
                .appendWidget(DemoWidgets.icon(this@AttributedDemoActivity, R.drawable.ic_expand_circle, 16f), ZLTextStyle().color(M.BLUE))
                .build()
            text = "单行截断：截断符 truncationToken 中也可以放图标，这段文字足够长会被截断。"
        }
        addCard(
            "14. 行内 Widget（图标 / 图片）", "图标按 ZLRichWidget 的尺寸占位，tintWithTextColor 的图标按文字颜色着色（含按下颜色）；切换图标大小观察行高与换行",
            segmented(listOf("14", "18", "28", "40"), 1) { build(listOf(14f, 18f, 28f, 40f)[it]) },
            view,
            tapCount,
            truncated,
        )
    }
}
