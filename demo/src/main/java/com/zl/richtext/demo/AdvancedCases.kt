package com.zl.richtext.demo

import android.animation.ValueAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.transition.TransitionManager
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.TextView
import com.zl.richtext.ZLExpandListener
import com.zl.richtext.ZLBracketTextView
import com.zl.richtext.ZLExpandTokenClickListener
import com.zl.richtext.ZLGestureListener
import com.zl.richtext.ZLRichTextView
import com.zl.richtext.ZLSelectionListener
import com.zl.richtext.ZLCopyListener
import com.zl.richtext.ZLTagListener
import com.zl.richtext.ZLTagParsedListener
import com.zl.richtext.ZLTagSpanBuilder
import com.zl.richtext.ZLTagStyleBuilder
import com.zl.richtext.ZLTagTextView
import com.zl.richtext.ZLTagWidgetBuilder
import com.zl.richtext.rule.ZLRichMatchListener
import com.zl.richtext.style.ZLColor
import com.zl.richtext.style.ZLDimension
import com.zl.richtext.style.ZLRichWidget
import com.zl.richtext.style.ZLTextBackground
import com.zl.richtext.style.ZLTextStyle
import com.zl.richtext.style.zlStyled
import com.zl.richtext.tag.ZLTagInfo
import com.zl.richtext.tag.ZLTagParserConfiguration

/**
 * Cases 24 – 33 of the iOS example: expand / collapse, long press / double tap, selection,
 * generic tags, capsules and gradients, padding, widgets following the view size, style chains,
 * custom tag delimiters, the `[ ]` tag subclass.
 */
object AdvancedCases {

    fun create(context: Context, log: TapLog): List<View> = with(context) {
        case24(log) + listOf(case24e(log), case25(log), case26(log), case27(log), case28(log), case28b(log), case29(log), case30(log), case31(log)) +
            case32(log) + case33(log)
    }

    private const val LONG = "<h1>ZLRichText</h1> 支持「展开 / 收起」：内容超过 maxLines 行时，最后一行末尾显示可点击的「… 全文」，" +
        "点击后展开全部内容，末尾出现「收起」。适用于动态、评论、商品详情等场景。" +
        "展开状态可以保存在数据里，列表复用时直接设置 isExpanded，不会触发 onExpandChangeListener。" +
        "<h2>#标签#</h2> 的点击和按下高亮不受影响。"

    private fun Context.token(text: String, color: Int) = zlStyled(text, ZLTextStyle().color(color).bold())

    /** Animates the height change of the page (`UIView.animate` + `layoutIfNeeded` on iOS). */
    private fun View.animateLayout() {
        (rootView as? ViewGroup)?.let { TransitionManager.beginDelayedTransition(it) }
    }

    // MARK: - 24. 展开 / 收起

    private fun Context.case24(log: TapLog): List<View> {
        val fourLines = "<h1>collapseThreshold = 5</h1>：maxLines 仍为 3，但内容不超过 5 行时完整显示、" +
            "不出现「全文」，避免只折叠掉一两行。这段文字在手机宽度下大约 4 行，所以完整显示。"
        return listOf(
            Triple("24. 展开 / 收起（收起时 3 行）", LONG, 0),
            Triple("24b. 内容不足 3 行：不显示「全文」", "<h1>短内容</h1> 不会被截断，也不显示按钮。", 0),
            Triple("24c. collapseThreshold = 5：不超过 5 行完整显示", fourLines, 5),
            Triple("24d. collapseThreshold = 5：超过 5 行才折叠为 3 行", LONG + LONG, 5),
        ).map { (title, source, threshold) ->
            tagCase(log, title, source) { hooks ->
                maxLines = 3
                collapseThreshold = threshold
                tagStyle = ZLTextStyle().color(M.BLUE)
                tagHighlightStyle = ZLTextStyle().backgroundColor(0x14000000)
                expandToken = token("… 全文", M.DEEP_ORANGE)
                collapseToken = token(" 收起", M.DEEP_ORANGE)
                onExpandChangeListener = ZLExpandListener { expanded ->
                    animateLayout()
                    hooks.event("expand", if (expanded) "全文" else "收起", "onExpandChanged($expanded)")
                }
            }
        }
    }

    /** 24e. 外部决定是否展开 / 收起：设置 onExpandTokenClickListener 后点击按钮不再自动切换 */
    private fun Context.case24e(log: TapLog): View = tagCase(log, "24e. onExpandTokenClickListener：外部决定是否展开 / 收起", LONG) { hooks ->
        maxLines = 3
        tagStyle = ZLTextStyle().color(M.BLUE)
        expandToken = token("… 全文", M.TEAL)
        collapseToken = token(" 收起", M.TEAL)
        onExpandChangeListener = ZLExpandListener { expanded ->
            animateLayout()
            hooks.event("expand", if (expanded) "全文" else "收起", "onExpandChanged($expanded)")
        }
        onExpandTokenClickListener = ZLExpandTokenClickListener { view, wantsExpand ->
            val text = if (wantsExpand) "全文" else "收起"
            hooks.event("expandToken", text, "onExpandTokenClick(wantsExpand: $wantsExpand)")
            val items = mutableListOf<Pair<String, () -> Unit>>(
                (if (wantsExpand) "展开（toggleExpanded）" else "收起（toggleExpanded）") to { view.toggleExpanded() },
            )
            if (wantsExpand) items += "跳转详情页（不展开）" to { hooks.event("expandToken", text, "跳转详情页") }
            showActionSheet(if (wantsExpand) "展开全文？" else "收起？", "设置了 onExpandTokenClickListener：由外部决定，不设置时内部自动切换", items)
        }
    }

    // MARK: - 25. 长按 / 双击 / 扩大点击热区

    private fun Context.copy(text: String) {
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(null, text))
    }

    /** 25. 长按菜单（复制 / 举报）、按命中区分的长按、双击、扩大点击热区 */
    private fun Context.case25(log: TapLog): View {
        val title = "25. 长按菜单 / 双击 / 扩大点击热区"
        val source = "Long press blank text to copy or report. Long press <h1>User Agreement</h1> or @alice " +
            "for their own menus, double tap to like ❤️. Tap slightly below @bob: extendsLinkTouchArea expands the touch area by up to 15dp."
        val hooks = CaseHooks(title, log)
        val card = DemoCard(this, title, source)
        val overlay = FrameLayout(this)
        fun copied(text: String, info: ZLTagInfo) {
            copy(text)
            hooks.tap(info, "已复制：${text.take(40)}${if (text.length > 40) "…" else ""}")
        }
        val view = newTagView().apply {
            setLineSpacing(dp(10f).toFloat(), 1f)
            tagStyle = ZLTextStyle().color(M.BLUE)
            tagHighlightStyle = ZLTextStyle().backgroundColor(0x14000000)
            extendsLinkTouchArea = true
            onTagClickListener = hooks.onTagClick
            onTagsParsedListener = com.zl.richtext.ZLTagParsedListener { card.showTags(it) }
            // 规则命中自己的长按：只回调它，不再回调 onLongPressListener
            addRule("mention", "@\\w+") { ctx ->
                ctx.addStyle(ZLTextStyle().color(M.PURPLE).bold())
                ctx.onTap = ZLRichMatchListener { hooks.ruleTap(it, "mention onTap → 打开 ${it.text} 的主页") }
                ctx.onLongPress = ZLRichMatchListener { match ->
                    val info = ruleInfo(match)
                    showActionSheet(match.text, items = listOf(
                        "复制 ${match.text}" to { copied(match.text, info) },
                        "举报 ${match.text}" to { hooks.tap(info, "ctx.onLongPress → 举报 ${match.text}") },
                    ))
                }
            }
            // 标签的长按
            onTagLongClickListener = ZLTagListener { info ->
                showActionSheet("<${info.name}> ${info.text}", items = listOf("复制「${info.text}」" to { copied(info.text, info) }))
            }
            // 其他位置的长按：复制原文 / 展示文字、举报
            onLongPressListener = ZLGestureListener { gesture ->
                val info = ZLTagInfo(gesture.characterIndex, "long-press", gesture.displayText, gesture.rawText)
                showActionSheet("长按位置字符下标：${gesture.characterIndex}", items = listOf(
                    "复制原文（rawText）" to { copied(gesture.rawText, info) },
                    "复制展示文字（displayText）" to { copied(gesture.displayText, info) },
                    "举报" to { hooks.tap(info, "onLongPress → 举报") },
                ))
            }
            // 双击：设置后单击链接会等双击判定失败才触发（约 300ms）
            onDoubleTapListener = ZLGestureListener { gesture ->
                val info = ZLTagInfo(gesture.characterIndex, "double-tap", gesture.matchDisplayText ?: "", gesture.match?.text ?: "")
                hooks.tap(info, "onDoubleTap → 点赞 ❤️（位置 ${gesture.x.toInt()}, ${gesture.y.toInt()}）")
                showHeart(overlay, gesture.x, gesture.y)
            }
            text = source
        }
        overlay.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        card.setContent(overlay)
        return card
    }

    /** ❤️ that grows, moves up and fades out at the double tap position. */
    private fun Context.showHeart(overlay: FrameLayout, x: Float, y: Float) {
        val heart = TextView(this).apply {
            text = "❤️"
            textSize = 28f
        }
        overlay.addView(heart, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        heart.post {
            heart.x = x - heart.width / 2f
            heart.y = y - heart.height / 2f
            heart.animate().scaleX(1.6f).scaleY(1.6f).translationYBy(-dp(12f).toFloat()).alpha(0f).setDuration(600)
                .withEndAction { overlay.removeView(heart) }.start()
        }
    }

    // MARK: - 26. 选中复制

    /** 26. 选中复制：长按选词、拖柄调整范围，系统菜单「复制」+「复制原文」 */
    private fun Context.case26(log: TapLog): View {
        val title = "26. 选中复制（textIsSelectable）"
        val source = "长按选中一个词，拖动两端的拖柄调整范围 [smile]，菜单里：" +
            "「复制」得到展示文字，「复制原文」得到 @alice 和 [smile] 的原始写法。" +
            "点击 <h1>用户协议</h1> 仍然可以跳转。"
        val status = label("长按文字开始选中", sizeSp = 11f, color = 0x99000000.toInt(), mono = true)
        lateinit var view: ZLTagTextView
        val card = tagCase(log, title, source) { hooks ->
            view = this
            setTextIsSelectable(true)
            tagStyle = ZLTextStyle().color(M.BLUE)
            // 表情：选中后「复制」得到空（图标），「复制原文」得到 [smile]
            addRule("emoji", "\\[smile\\]") { ctx ->
                ctx.setWidget(DemoWidgets.icon(this@case26, R.drawable.ic_smile, 17f), ZLTextStyle().color(M.ORANGE))
            }
            // @提及：展示为 👤alice，原文为 @alice
            addRule("mention", "@\\w+") { ctx -> ctx.setText("👤" + ctx.matchedText.drop(1), ZLTextStyle().color(M.PURPLE)) }
            onSelectionChangeListener = ZLSelectionListener { range ->
                status.text = if (range == null) {
                    "未选中"
                } else {
                    "selectedRange   = [${range.start}, ${range.end})\n" +
                        "selectedText    = \"${view.selectedText ?: ""}\"\n" +
                        "selectedRawText = \"${view.selectedRawText ?: ""}\""
                }
            }
            onCopyListener = ZLCopyListener { text, isRaw ->
                hooks.event(if (isRaw) "copy-raw" else "copy", text, if (isRaw) "onCopy → 复制原文" else "onCopy → 复制展示文字")
            }
        }
        (view.parent as ViewGroup).removeView(view)
        card.setContent(vertical(view, status))
        return card
    }

    // MARK: - 27. 通用标签

    /** 27. 通用标签：任意标签名、标签属性、嵌套、自闭合 */
    private fun Context.case27(log: TapLog): View {
        val title = "27. 通用标签（任意标签名 / 属性 / 嵌套）"
        val source = "支持 <b>粗体</b>、<i>斜体</i>、<u>下划线</u>、<color value=\"#E91E63\">任意颜色</color>、" +
            "<size value=\"20\">字号</size>。可以嵌套：<b>粗体里 <color value=\"#2196F3\">蓝色 <u>加下划线</u></color></b>。" +
            "链接 <a href=\"https://github.com\">GitHub</a>，图标 <icon name=\"star.fill\" color=\"orange\"/>，" +
            "换行<br/>容错：<b>未闭合的 <i>标签</b> 原样显示，多余的 </u> 也原样显示。"
        val icons = mapOf("star.fill" to R.drawable.ic_star, "heart.fill" to R.drawable.ic_heart, "bell.fill" to R.drawable.ic_bell)
        return tagCase(log, title, source) { hooks ->
            // 识别任意标签名，支持嵌套和自闭合（默认只识别 <h> / <hN>）
            tagConfiguration = ZLTagParserConfiguration.ANY_TAGS
            tagStyles = mapOf(
                "b" to ZLTextStyle().bold(),
                "i" to ZLTextStyle().italic(),
                "u" to ZLTextStyle().underline(),
                "a" to ZLTextStyle().color(M.BLUE).underline(),
            )
            // 按属性设置样式：<color value="…">、<size value="…">
            tagStyleBuilder = ZLTagStyleBuilder { info ->
                when (info.name) {
                    "color" -> info.colorAttribute("value")?.let { ZLTextStyle().color(it) }
                    "size" -> info.attribute("value")?.toFloatOrNull()?.let { ZLTextStyle().textSizeSp(it) }
                    else -> null
                }
            }
            // 自闭合标签：<br/> 换行
            tagSpanBuilder = ZLTagSpanBuilder { info, _, _ -> if (info.name == "br") "\n" else null }
            // 自闭合标签：<icon name="…" color="…"/>
            tagWidgetBuilder = ZLTagWidgetBuilder { info, _ ->
                if (info.name != "icon") return@ZLTagWidgetBuilder null
                DemoWidgets.icon(this@case27, icons[info.attribute("name")] ?: R.drawable.ic_star, 16f, info.colorAttribute("color") ?: M.GREY)
            }
            // <a> 读取 href；其他标签走 onTagClickListener（内层标签优先响应点击）
            setTagClickListener("a") { hooks.tap(it, "打开 ${it.attribute("href")}") }
            onTagClickListener = ZLTagListener { info ->
                val path = generateSequence(info) { it.parent }.map { "<${it.name}>" }.toList().asReversed().joinToString(" › ")
                hooks.tap(info, "onTagClick  $path")
            }
            tagHighlightStyles = mapOf("br" to ZLTextStyle.NONE, "icon" to ZLTextStyle.NONE)
        }
    }

    // MARK: - 28. 胶囊背景 / 渐变文字

    private fun Context.dpValue(value: Float) = ZLDimension.dp(value)

    /** 28. 胶囊背景（圆角背景 / 边框 / 内边距）与渐变文字，由 view 自己绘制 */
    private fun Context.case28(log: TapLog): View {
        val source = "<grad>Android 富文本</grad> <new>NEW</new>\n" +
            "话题：#Android开发# #Kotlin##Compose# 点击胶囊有按下效果。" +
            "行内代码 `view.text = value` 和 `rebuild()`。" +
            "<tag>只有边框</tag> <tag>Tag</tag> " +
            "<rainbow>竖向渐变 + 跨行的长胶囊也能正确绘制，每一行单独一段</rainbow>，" +
            "<pill>很长的胶囊文字在折行时会分成两段分别绘制</pill>。"
        return tagCase(log, "28. 胶囊背景 / 渐变文字", source) { hooks ->
            // 胶囊的垂直内边距不占行高，加一点行距避免上下行重叠
            setLineSpacing(dp(6f).toFloat(), 1f)
            tagConfiguration = ZLTagParserConfiguration.ANY_TAGS
            tagStyles = mapOf(
                // 渐变标题
                "grad" to ZLTextStyle().textSizeSp(22f).bold().gradientColors(intArrayOf(M.PINK, M.ORANGE, M.DEEP_PURPLE)),
                // 实心小徽章
                "new" to ZLTextStyle().textSizeSp(11f).bold().color(Color.WHITE).baselineShift(3f).background(
                    ZLTextBackground(
                        fillColor = ZLColor.of(M.DEEP_ORANGE),
                        cornerRadius = dpValue(4f),
                        paddingHorizontal = dpValue(4f),
                        paddingVertical = dpValue(2f),
                    ),
                ),
                // 只有边框
                "tag" to ZLTextStyle().color(M.TEAL).textSizeSp(13f).background(ZLTextBackground(strokeColor = ZLColor.of(M.TEAL))),
                // 竖向渐变
                "rainbow" to ZLTextStyle().bold().gradientColors(intArrayOf(M.BLUE, M.GREEN), vertical = true),
                // 跨行的胶囊
                "pill" to ZLTextStyle().color(M.INDIGO).capsule(M.INDIGO),
            )
            // 按下高亮：换成更深的胶囊
            tagHighlightStyles = mapOf(
                "tag" to ZLTextStyle().background(ZLTextBackground(fillColor = ZLColor.of(M.alpha(M.TEAL, 0.15f)), strokeColor = ZLColor.of(M.TEAL))),
                "pill" to ZLTextStyle().background(ZLTextBackground(fillColor = ZLColor.of(M.alpha(M.INDIGO, 0.3f)))),
            )
            // #话题#：胶囊 + 点击
            addRule("topic", "#[^#\\s]+#") { ctx ->
                ctx.addStyle(ZLTextStyle().color(M.PURPLE).capsule(M.PURPLE))
                ctx.highlightStyle = ZLTextStyle().background(ZLTextBackground(fillColor = ZLColor.of(M.alpha(M.PURPLE, 0.3f))))
                ctx.onTap = ZLRichMatchListener { hooks.ruleTap(it, "点击话题") }
            }
            // `代码`：去掉反引号，等宽字体 + 灰色背景 + 细边框
            addRule("code", "`([^`]+)`") { ctx ->
                ctx.setText(ctx.group(1) ?: "", ZLTextStyle().monospace().textSizeSp(13f).color(M.DEEP_ORANGE).codeBackground())
            }
        }
    }

    /** 28b. 从右到左（阿拉伯语 / 希伯来语）与双向混排下的胶囊 */
    private fun Context.case28b(log: TapLog): View = ruleCase(
        log, "28b. RTL / 双向混排的胶囊",
        "مرحبا #سويفت# لغة برمجة قوية\nשלום #סוויפט# היא שפה\nكبسولتان متجاورتان: #iOS##سويفت# معا\nتعلم #Kotlin# اليوم\nLearn #سويفت# today",
    ) { hooks ->
        setLineSpacing(dp(8f).toFloat(), 1f)
        addRule("topic", "#[^#\\s]+#") { ctx ->
            ctx.addStyle(ZLTextStyle().color(M.PURPLE).capsule(M.PURPLE))
            ctx.highlightStyle = ZLTextStyle().background(ZLTextBackground(fillColor = ZLColor.of(M.alpha(M.PURPLE, 0.3f))))
            ctx.onTap = ZLRichMatchListener { hooks.ruleTap(it, "点击 RTL 胶囊") }
        }
    }

    // MARK: - 29. 内边距

    /** 29. 内边距：文字、点击热区、行内 Widget、截断与「展开」都在内边距以内，自动高度包含内边距 */
    private fun Context.case29(log: TapLog): View {
        val title = "29. 内边距 padding"
        val source = "设置 <h1>padding</h1> 后，背景铺满整个 view，文字在内边距以内排版 <h2>View</h2>。" +
            "点击热区、行内 Widget、截断与「展开」都跟着偏移，自动高度会加上内边距。切换上面的内边距可以看到文字重新折行。@alice"
        val hooks = CaseHooks(title, log)
        val paddings = listOf(0 to 0, 8 to 8, 16 to 12, 24 to 24)
        val view = newTagView().apply {
            maxLines = 3
            background = GradientDrawable().apply {
                setColor(M.LIGHT_PURPLE)
                cornerRadius = dp(12f).toFloat()
            }
            setPadding(dp(16f), dp(12f), dp(16f), dp(12f))
            tagStyle = ZLTextStyle().color(M.BLUE)
            tagHighlightStyle = ZLTextStyle().backgroundColor(0x14000000)
            onTagClickListener = hooks.onTagClick
            tagWidgetBuilder = ZLTagWidgetBuilder { info, _ ->
                if (info.name == "h2") ZLRichWidget.view(DemoWidgets.pill(this@case29, info.text, M.DEEP_PURPLE, textSizeSp = 11f, bold = true)) else null
            }
            addRule("mention", "@\\w+") { ctx ->
                ctx.addStyle(ZLTextStyle().color(M.PURPLE))
                ctx.onTap = ZLRichMatchListener { hooks.ruleTap(it, "点击 @") }
            }
            expandToken = token("… 全文", M.DEEP_ORANGE)
            collapseToken = token(" 收起", M.DEEP_ORANGE)
            onExpandChangeListener = ZLExpandListener { animateLayout() }
            text = source
        }
        val card = DemoCard(this, title, source)
        card.setContent(vertical(
            label("padding（dp）"),
            segmented(listOf("0", "8", "12 / 16", "24"), 2) {
                val (h, v) = paddings[it]
                view.animateLayout()
                view.setPadding(dp(h.toFloat()), dp(v.toFloat()), dp(h.toFloat()), dp(v.toFloat()))
            },
            view,
        ))
        return card
    }

    // MARK: - 30. 首 / 中 / 尾行内 Widget 跟随 view 尺寸

    /**
     * 30. 开头 / 中间 / 结尾插入行内 Widget，修改宽度、对齐、内边距、行数后，Widget 跟随文字重新排版。
     * 下方实时列出每个 Widget 的位置（boundingRectForRange）
     */
    private fun Context.case30(log: TapLog): View {
        val title = "30. 首 / 中 / 尾行内 Widget 跟随 view 尺寸"
        val source = "[avatar] 开头是头像，中间插入一个 [follow] 按钮和 [badge] 徽章，" +
            "拖动下面的宽度滑块，后面的文字会重新折行，Widget 跟着文字一起移动，结尾是箭头 [arrow]"
        val hooks = CaseHooks(title, log)
        var followed = false
        val status = label("", sizeSp = 10f, color = 0xB3000000.toInt(), mono = true)
        val names = mapOf("avatar" to "头像", "follow" to "按钮", "badge" to "徽章", "arrow" to "箭头")
        lateinit var view: ZLRichTextView

        fun refresh() = view.post {
            val display = view.contentText.toString()
            val lines = mutableListOf("view ${view.width} × ${view.height}")
            var index = 0
            for ((i, c) in display.withIndex()) {
                if (c != '\uFFFC') continue
                val name = names.values.elementAtOrNull(index++) ?: continue
                val rect = view.boundingRectForRange(i, i + 1)
                lines += if (rect == null) "$name  已截断，隐藏" else "$name  x=%5.1f y=%5.1f  ✅ 在文字中排版".format(rect.left, rect.top)
            }
            lines += "结论：Widget 是行内内容，随文字一起排版"
            status.text = lines.joinToString("\n")
        }

        view = newRichView().apply {
            setBackgroundColor(M.LIGHT_PURPLE)
            addRule("view", "\\[(\\w+)\\]") { ctx ->
                val key = ctx.group(1) ?: return@addRule
                val widget = when (key) {
                    "avatar" -> ZLRichWidget.view(TextView(this@case30).apply {
                        text = "Z"
                        textSize = 12f
                        gravity = Gravity.CENTER
                        setTextColor(Color.WHITE)
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(M.DEEP_PURPLE)
                        }
                    }, dp(22f), dp(22f))
                    // 自己处理点击的按钮：标题变化后尺寸变化，文字自动重新排版
                    "follow" -> ZLRichWidget.view(
                        DemoWidgets.pill(this@case30, if (followed) "✓ 已关注" else "+ 关注", if (followed) M.GREY else M.BLUE, paddingH = 8f, paddingV = 2f, radius = 9f),
                    ).copy(onClick = Runnable {
                        followed = !followed
                        hooks.tap(ZLTagInfo(0, "follow", if (followed) "✓ 已关注" else "+ 关注", "[follow]"), "按钮自己处理点击，尺寸变化")
                        view.setNeedsRebuild("view")
                        refresh()
                    })
                    "badge" -> ZLRichWidget.view(DemoWidgets.pill(this@case30, "LV.5", M.AMBER, textSizeSp = 10f, bold = true, paddingH = 5f))
                    "arrow" -> DemoWidgets.icon(this@case30, R.drawable.ic_arrow_circle, 18f, M.TEAL)
                    else -> return@addRule
                }
                ctx.setWidget(widget)
                ctx.onTap = ZLRichMatchListener { hooks.ruleTap(it, "点击 Widget $key") }
            }
            text = source
            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> refresh() }
        }
        val container = FrameLayout(this).apply { addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)) }
        fun setRatio(ratio: Float) {
            view.layoutParams = view.layoutParams.apply { width = (container.width * ratio).toInt().coerceAtLeast(1) }
        }
        val slider = SeekBar(this).apply {
            max = 70
            progress = 70
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) setRatio((30 + progress) / 100f)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
            })
        }
        val card = DemoCard(this, title, source)
        card.setContent(vertical(
            label("宽度（拖动滑块）/ 对齐 / 行数与内边距"),
            slider,
            segmented(listOf("自然", "左", "居中", "右"), 0) {
                view.gravity = Gravity.TOP or listOf(Gravity.START, Gravity.LEFT, Gravity.CENTER_HORIZONTAL, Gravity.RIGHT)[it]
            },
            segmented(listOf("不限行", "2 行", "内边距 0", "内边距 16"), null) {
                when (it) {
                    0 -> view.maxLines = Int.MAX_VALUE
                    1 -> view.maxLines = 2
                    2 -> view.setPadding(0, 0, 0, 0)
                    else -> view.setPadding(dp(16f), dp(8f), dp(16f), dp(8f))
                }
            },
            textButton("▶︎ 动画：宽度 100% → 35% → 100%") {
                val animator = ValueAnimator.ofFloat(1f, 0.35f, 1f).setDuration(2000)
                animator.addUpdateListener {
                    val ratio = it.animatedValue as Float
                    setRatio(ratio)
                    slider.progress = (ratio * 100 - 30).toInt()
                }
                animator.start()
            },
            container,
            status,
        ))
        return card
    }

    // MARK: - 31. ZLTextStyle 链式配置

    /** 31. ZLTextStyle 链式配置：style / tagStyles / 高亮 / 规则 ctx 中都可以链式书写 */
    private fun Context.case31(log: TapLog): View {
        val source = "<title>链式配置样式</title> <new>NEW</new>\n" +
            "正文 ZLTextStyle().textSizeSp(15f).color(…)，<b>加粗</b>、<i>斜体</i>、<u>下划线</u>、<s>删除线</s>、" +
            "<grad>渐变文字</grad>、<shadow>阴影</shadow>、<k>字间距</k>，规则里 #话题# 胶囊和 `code` 行内代码。"
        return tagCase(log, "31. ZLTextStyle 链式配置", source) { hooks ->
            tagConfiguration = ZLTagParserConfiguration.ANY_TAGS
            setLineSpacing(dp(6f).toFloat(), 1f)
            // 正文：链式书写
            style = ZLTextStyle().textSizeSp(15f).color(M.TEXT)
            // 按标签名：值也是链式书写
            tagStyles = mapOf(
                "title" to ZLTextStyle().textSizeSp(20f).bold().gradientColors(intArrayOf(M.PINK, M.ORANGE, M.DEEP_PURPLE)),
                "new" to ZLTextStyle().textSizeSp(11f).bold().color(Color.WHITE).baselineShift(3f)
                    .background(ZLTextBackground(fillColor = ZLColor.of(M.DEEP_ORANGE), cornerRadius = ZLDimension.dp(4f))),
                "b" to ZLTextStyle().bold().color(M.BLUE),
                "i" to ZLTextStyle().italic(),
                "u" to ZLTextStyle().underline().color(M.PINK),
                "s" to ZLTextStyle().strikethrough().color(M.GREY),
                "grad" to ZLTextStyle().bold().gradientColors(intArrayOf(M.BLUE, M.GREEN)),
                "shadow" to ZLTextStyle().color(M.INDIGO).shadow(0x4D000000, radiusDp = 2f, dxDp = 1f, dyDp = 1f),
                "k" to ZLTextStyle().kern(0.25f).color(M.TEAL),
            )
            // 按下高亮
            tagHighlightStyle = ZLTextStyle().backgroundColor(0x14000000)
            // 规则 handler 中：ctx.addStyle / ctx.highlightStyle / ctx.setText
            addRule("topic", "#[^#\\s]+#") { ctx ->
                ctx.addStyle(ZLTextStyle().color(M.PURPLE).capsule(M.PURPLE))
                ctx.highlightStyle = ZLTextStyle().background(ZLTextBackground(fillColor = ZLColor.of(M.alpha(M.PURPLE, 0.3f))))
                ctx.onTap = ZLRichMatchListener { hooks.ruleTap(it, "点击话题") }
            }
            addRule("code", "`([^`]+)`") { ctx ->
                ctx.setText(ctx.group(1) ?: "", ZLTextStyle().monospace().textSizeSp(13f).color(M.DEEP_ORANGE).codeBackground())
            }
        }
    }

    // MARK: - 32. 自定义标签符号

    /** 32. 自定义标签的开始 / 结束符号（tagOpenDelimiter / tagCloseDelimiter），其他解析规则不变 */
    private fun Context.case32(log: TapLog): List<View> {
        val samples = listOf(
            Triple("[", "]", "同意 [h1]用户协议[/h1] 和 [h2]隐私政策[h2/]，[h1]未闭合 原样显示；<h1>尖括号</h1> 不再识别"),
            Triple("【", "】", "同意【h1】用户协议【/h1】和【h2】隐私政策【/h2】，【h3】不匹配【/h1】原样显示"),
            Triple("{", "}", "同意 {h1}用户协议{/h1} 和 {h2}隐私政策{/h2}，{hx}不是标签{/hx}"),
            Triple("{{", "}}", "多字符符号：{{b}}粗体 {{color value=\"#E91E63\"}}嵌套颜色{{/color}}{{/b}}、{{a href=\"https://github.com\"}}GitHub{{/a}}"),
        )
        return samples.mapIndexed { index, (open, close, source) ->
            tagCase(log, "32${"abcd"[index]}. 自定义标签符号 $open $close", source) {
                tagOpenDelimiter = open
                tagCloseDelimiter = close
                if (open == "{{") {
                    // 任意标签 + 属性 + 嵌套，符号换成 {{ }} 后同样可用
                    tagConfiguration = tagConfiguration.copy(namePattern = ZLTagParserConfiguration.ANY_TAG_NAME, allowsNesting = true)
                    tagStyles = mapOf(
                        "b" to ZLTextStyle().bold(),
                        "a" to ZLTextStyle().color(M.BLUE).underline(),
                    )
                    tagStyleBuilder = ZLTagStyleBuilder { info -> info.colorAttribute("value")?.let { ZLTextStyle().color(it) } }
                } else {
                    tagStyles = mapOf(
                        "h1" to ZLTextStyle().color(M.BLUE).underline(),
                        "h2" to ZLTextStyle().color(M.DEEP_ORANGE).underline(),
                    )
                    tagHighlightStyle = ZLTextStyle().backgroundColor(M.alpha(M.BLUE, 0.2f))
                }
            }
        }
    }

    // MARK: - 33. 子类 ZLBracketTextView

    /** 33. 继承 ZLTagTextView 的子类 ZLBracketTextView：默认用 [ ] 包裹标签 */
    private fun Context.case33(log: TapLog): View {
        val title = "33. 子类 ZLBracketTextView（[ ] 标签）"
        val source = "同意 [h1]用户协议[/h1] 和 [h2]隐私政策[h2/]，[h3]未闭合 原样显示；<h1>尖括号</h1> 不再识别"
        val card = DemoCard(this, title, source)
        val hooks = CaseHooks(title, log)
        val view = ZLBracketTextView(this).apply {
            style = BASE_STYLE
            gravity = Gravity.TOP or Gravity.START
            tagStyles = mapOf(
                "h1" to ZLTextStyle().color(M.BLUE).underline(),
                "h2" to ZLTextStyle().color(M.DEEP_ORANGE).underline(),
            )
            tagHighlightStyle = ZLTextStyle().backgroundColor(M.alpha(M.BLUE, 0.2f))
            onTagClickListener = hooks.onTagClick
            onTagsParsedListener = ZLTagParsedListener { card.showTags(it) }
            text = source
        }
        card.setContent(view)
        return card
    }
}
