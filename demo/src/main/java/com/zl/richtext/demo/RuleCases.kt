package com.zl.richtext.demo

import android.content.Context
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import com.zl.richtext.ZLTagParsedListener
import com.zl.richtext.ZLTagTextView
import com.zl.richtext.ZLTagWidgetBuilder
import com.zl.richtext.detect.ZLDataDetector
import com.zl.richtext.detect.ZLDataDetectorResult
import com.zl.richtext.detect.ZLDataDetectorType
import com.zl.richtext.rule.ZLRichMatchListener
import com.zl.richtext.rule.ZLRichParser
import com.zl.richtext.rule.ZLRichRule
import com.zl.richtext.style.ZLRichWidget
import com.zl.richtext.style.ZLTextStyle

/** Cases 17 – 23 of the iOS example: custom rules, widget resizing, dark mode, performance. */
object RuleCases {

    fun create(context: Context, log: TapLog): List<View> = with(context) {
        listOf(case17(log), case18(log)) + case19(log) + listOf(case20(log), case21(log), case22(log), case23(log))
    }

    /** 17. 自定义规则（基类 ZLRichTextView）：@提及 / #话题# / 链接（ZLDataDetector） */
    private fun Context.case17(log: TapLog) = ruleCase(
        log, "17. ZLRichTextView 自定义规则：@提及 / #话题# / 链接",
        "Hi @alice and @bob_99, join #Android# talk: https://developer.android.com/kotlin",
    ) { hooks ->
        // @提及
        addRule("mention", "@\\w+", priority = 10) { ctx ->
            ctx.addStyle(ZLTextStyle().color(M.PURPLE).bold())
            ctx.onTap = ZLRichMatchListener { hooks.ruleTap(it, "mention → 打开 ${it.text} 的主页") }
        }
        // #话题#
        addRule("topic", "#[^#\\s]+#", priority = 10) { ctx ->
            ctx.addStyle(ZLTextStyle().color(M.DEEP_ORANGE))
            ctx.highlightStyle = ZLTextStyle().backgroundColor(0xFFFFE0B2.toInt())
            ctx.onTap = ZLRichMatchListener { hooks.ruleTap(it, "topic → 打开话题 ${it.text}") }
        }
        // 链接：ZLDataDetector 也是 ZLPattern，替换展示文字
        addRule(ZLRichRule("link", ZLDataDetector(setOf(ZLDataDetectorType.LINK)), priority = 10) { ctx ->
            ctx.setText("🔗网页链接", ZLTextStyle().color(M.BLUE).underline())
            ctx.onTap = ZLRichMatchListener { match ->
                val url = (match.result.payload as ZLDataDetectorResult).url
                hooks.ruleTap(match, "link → 打开 $url")
            }
        })
    }

    /** 18. 外部解析后替换内容：表情图标 / 模板变量（命名捕获组） */
    private fun Context.case18(log: TapLog) = ruleCase(
        log, "18. ZLRichTextView 外部解析替换：[smile] 表情 / {{变量}}",
        "Hello {{name}} [smile], you have {{count}} new messages [heart][star]. Unknown: {{foo}} [xxx].",
    ) { hooks ->
        val icons = mapOf("smile" to R.drawable.ic_smile, "heart" to R.drawable.ic_heart, "star" to R.drawable.ic_star)
        val colors = mapOf("smile" to M.ORANGE, "heart" to M.RED, "star" to M.AMBER)
        val variables = mapOf("name" to "Alice", "count" to "3")
        // 表情：图标跟随文字颜色，点击图标同样触发规则的 onTap
        addRule("emoji", "\\[(smile|heart|star)\\]", priority = 5) { ctx ->
            val key = ctx.group(1) ?: return@addRule
            ctx.setWidget(DemoWidgets.icon(this@case18, icons.getValue(key), 17f), ZLTextStyle().color(colors.getValue(key)))
            ctx.onTap = ZLRichMatchListener { hooks.ruleTap(it, "emoji → ${it.group(1)}") }
        }
        // 模板变量：命名捕获组取 key，外部查值后写回
        addRule("variable", "\\{\\{(?<key>\\w+)\\}\\}", priority = 5) { ctx ->
            val value = variables[ctx.group("key")]
            if (value != null) {
                ctx.setText(value, ZLTextStyle().bold().color(M.TEAL))
            } else {
                ctx.addStyle(ZLTextStyle().color(M.GREY).strikethrough())
            }
        }
    }

    /** 19. 优先级：手机号规则(20) > <h> 标签(tagPriority) > 数字规则(1) */
    private fun Context.case19(log: TapLog): List<View> {
        val source = "Call <h1>13800138000</h1> or 400-123-4567, order 2024."
        fun ZLTagTextView.configure(hooks: CaseHooks) {
            tagStyle = ZLTextStyle().color(M.BLUE).underline()
            addRule("phone", "1\\d{10}", priority = 20) { ctx ->
                ctx.addStyle(ZLTextStyle().color(M.GREEN).bold())
                ctx.onTap = ZLRichMatchListener { hooks.ruleTap(it, "phone(20) → 拨打 ${it.text}") }
            }
            addRule("number", "\\d+", priority = 1) { ctx -> ctx.addStyle(ZLTextStyle().backgroundColor(0xFFE0E0E0.toInt())) }
        }
        return listOf(
            // h1 与 phone 重叠，phone 优先级更高 → h1 整体作废，<h1> 原样显示
            tagCase(log, "19a. 优先级：phone(20) > h1(0) > number(1)", source) { hooks ->
                tagPriority = 0
                configure(hooks)
            },
            // h1 优先，内部的手机号不再被规则处理
            tagCase(log, "19b. 优先级：h1(100) > phone(20) > number(1)", source) { hooks ->
                tagPriority = 100
                configure(hooks)
            },
        )
    }

    /** 20. 只用自定义规则（基类 ZLRichTextView，不解析 <h> 标签）：简易 Markdown */
    private fun Context.case20(log: TapLog) = ruleCase(
        log, "20. ZLRichTextView 简易 Markdown（<h> 不解析）",
        "<h1>not parsed</h1>, **bold**, ~~strike~~, `code` and **nested `x`**.",
    ) {
        // 长的 **...** 先占位（同优先级位置靠前 / 更长优先），内部的 `x` 不会再被处理
        addRule("bold", "\\*\\*(.+?)\\*\\*") { ctx -> ctx.setText(ctx.group(1) ?: "", ZLTextStyle().bold()) }
        addRule("strike", "~~(.+?)~~") { ctx -> ctx.setText(ctx.group(1) ?: "", ZLTextStyle().strikethrough()) }
        addRule("code", "`([^`]+)`") { ctx ->
            ctx.setText(ctx.group(1) ?: "", ZLTextStyle().monospace().textSizeSp(14f).backgroundColor(0xFFEEEEEE.toInt()).color(M.PINK))
        }
    }

    /** 21. 行内 Widget 尺寸 / 内容变化后，文字自动重新排版 */
    private fun Context.case21(log: TapLog): View {
        var boxWidth = 24f
        var likes = 0
        lateinit var view: ZLTagTextView
        return tagCase(
            log, "21. 行内 Widget 尺寸变化，自动刷新排版",
            "Tap the box <h1>box</h1> to resize it, tap <h2>like</h2> to add likes, the following text reflows automatically.",
        ) { hooks ->
            view = this
            tagStyle = ZLTextStyle().color(M.BLUE)
            tagWidgetBuilder = ZLTagWidgetBuilder { info, _ ->
                when (info.name) {
                    "h1" -> {
                        val box = GradientDrawable().apply {
                            setColor(M.ORANGE)
                            cornerRadius = dp(4f).toFloat()
                        }
                        // 状态保存在外部：点击后修改状态并重新渲染标签，后面的文字自动重新排版
                        ZLRichWidget(box, dp(boxWidth), dp(16f), onClick = Runnable {
                            boxWidth = if (boxWidth >= 120f) 24f else boxWidth + 32f
                            hooks.event("box", "${boxWidth.toInt()}dp", "Widget 尺寸变化 → 重新排版")
                            view.setNeedsRebuild(ZLTagTextView.TAG_RULE_NAME)
                        })
                    }
                    "h2" -> ZLRichWidget.view(DemoWidgets.pill(this@case21, "👍 $likes", M.PINK, radius = 9f))
                        .copy(onClick = Runnable {
                            likes++
                            hooks.event("like", "👍 $likes", "Widget 内容变化 → 重新测量")
                            view.setNeedsRebuild(ZLTagTextView.TAG_RULE_NAME)
                        })
                    else -> null
                }
            }
        }
    }

    /** 22. 深色模式 / 系统字号：外观与字号的覆盖只作用在预览区域 */
    private fun Context.case22(log: TapLog): View {
        val title = "22. 深色模式 / 系统字号"
        val source = "Appearance: {mode}. Read the <h1>User Agreement</h1> <h2>NEW</h2>, " +
            "text, tags and the badge follow dark mode and the system font scale."
        val hooks = CaseHooks(title, log)
        val modes = listOf(null, Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES)
        val scales = listOf(null, 0.85f, 1f, 1.3f, 2f)
        var mode: Int? = null
        var scale: Float? = null
        var followsFontScale = true
        val preview = FrameLayout(this)
        val card = DemoCard(this, title, source)

        fun render() {
            // 预览区域使用覆盖了 uiMode / fontScale 的 Context：colorRes 与 sp 都按它解析
            val config = Configuration(resources.configuration)
            mode?.let { config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or it }
            scale?.let { config.fontScale = it }
            val themed = ContextThemeWrapper(this, R.style.Theme_ZLRichTextDemo).apply { applyOverrideConfiguration(config) }
            val night = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val unit = if (followsFontScale) TypedValue.COMPLEX_UNIT_SP else TypedValue.COMPLEX_UNIT_DIP
            fun size(value: Float) = if (followsFontScale) ZLTextStyle().textSizeSp(value) else ZLTextStyle().textSizeDp(value)
            val label = ZLTagTextView(themed).apply {
                setPadding(dp(10f), dp(10f), dp(10f), dp(10f))
                setBackgroundResource(R.color.demo_preview_background)
                style = size(15f).colorRes(R.color.demo_text)
                tagStyle = ZLTextStyle().colorRes(R.color.demo_link).underline()
                onTagClickListener = hooks.onTagClick
                onTagsParsedListener = ZLTagParsedListener { card.showTags(it) }
                // Widget 按预览的外观与字号创建
                tagWidgetBuilder = ZLTagWidgetBuilder { info, _ ->
                    if (info.name != "h2") return@ZLTagWidgetBuilder null
                    val badge = TextView(themed).apply {
                        text = info.text
                        setTextColor(0xFFFFFFFF.toInt())
                        setTextSize(unit, 10f)
                        setTypeface(typeface, Typeface.BOLD)
                        setPadding(dp(5f), dp(1f), dp(5f), dp(1f))
                        background = GradientDrawable().apply {
                            setColor(themed.getColor(R.color.demo_badge))
                            cornerRadius = dp(4f).toFloat()
                        }
                    }
                    ZLRichWidget.view(badge)
                }
                // 规则 handler 按预览的外观返回不同内容
                addRule("mode", "\\{mode\\}") { ctx ->
                    ctx.setText(if (night) "🌙 Dark" else "☀️ Light", size(15f).bold())
                }
                text = source
            }
            preview.removeAllViews()
            preview.addView(label, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        val content = vertical(
            segmented(listOf("跟随系统", "浅色", "深色"), 0) {
                mode = modes[it]
                render()
            },
            segmented(listOf("系统", "XS", "L", "XXXL", "AX3"), 0) {
                scale = scales[it]
                render()
            },
            label("字号只覆盖预览区域；「系统」跟随 设置 › 显示 › 字体大小", sizeSp = 11f),
            switchRow("跟随系统字号（sp）", true) {
                followsFontScale = it
                render()
            },
            preview,
        )
        render()
        card.setContent(content)
        return card
    }

    /** 23. 性能：命中的渲染结果按命中缓存、排版结果按「内容 + 宽度」缓存 */
    private fun Context.case23(log: TapLog): View {
        val title = "23. 性能：渲染缓存 / 排版缓存"
        val count = 200
        val source = (0 until count).joinToString("") { "@user$it [smile] #topic${it % 10}# " }
        val card = DemoCard(this, title, "@user0 [smile] #topic0# @user1 [smile] #topic1# …（重复 $count 次，共 ${count * 3} 个命中）")
        val hooks = CaseHooks(title, log)
        var mentionPurple = true
        var highlightAmber = true
        var appended = 0
        val stats = label("", sizeSp = 11f, color = 0xB3000000.toInt(), mono = true)
        val view = newRichView().apply {
            maxLines = 4
            highlightStyle = ZLTextStyle().backgroundColor(M.alpha(M.AMBER, 0.4f))
            // 规则对象只创建一次：对象不变，命中的渲染结果就会被缓存复用
            addRule("mention", "@\\w+") { ctx ->
                // handler 依赖外部状态：变化后用 setNeedsRebuild("mention") 只刷新这条规则
                ctx.addStyle(ZLTextStyle().color(if (mentionPurple) M.PURPLE else M.GREEN))
                ctx.onTap = ZLRichMatchListener { hooks.ruleTap(it, "rule onTap") }
            }
            addRule("emoji", "\\[smile\\]") { ctx ->
                ctx.setWidget(DemoWidgets.icon(this@case23, R.drawable.ic_smile, 16f), ZLTextStyle().color(M.ORANGE))
            }
            addRule("topic", "#\\w+#") { ctx -> ctx.addStyle(ZLTextStyle().color(M.BLUE)) }
        }

        /** Runs [action], measures the view synchronously and shows time + statistics. */
        fun measure(name: String, action: () -> Unit) {
            view.statistics.reset()
            val start = System.nanoTime()
            action()
            val width = View.MeasureSpec.makeMeasureSpec(view.width.coerceAtLeast(1), View.MeasureSpec.EXACTLY)
            view.measure(width, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            val ms = (System.nanoTime() - start) / 1e6
            val s = view.statistics
            stats.text = "%s：%.2f ms\n".format(name, ms) +
                "handler 执行 ${s.handlerCalls} 次，复用 ${s.reusedMatches} 个命中\n" +
                "渲染 ${s.renders} 次，文本排版 ${s.textLayouts} 次，排版缓存命中 ${s.layoutCacheHits} 次\n" +
                "当前命中 ${view.matches.size} 个，图标 ${view.matches.count { it.rule.name == "emoji" }} 个"
        }

        val buttons = listOf<Pair<String, () -> Unit>>(
            "改按下高亮" to {
                measure("改按下高亮") {
                    highlightAmber = !highlightAmber
                    view.highlightStyle = ZLTextStyle().backgroundColor(M.alpha(if (highlightAmber) M.AMBER else M.BLUE, 0.4f))
                }
            },
            "末尾追加文字" to {
                appended++
                measure("末尾追加 @new$appended") { view.rawText = view.rawText + "@new$appended " }
            },
            "只刷新 @提及" to {
                measure("只刷新 @提及（setNeedsRebuild(\"mention\")）") {
                    mentionPurple = !mentionPurple
                    view.setNeedsRebuild("mention")
                }
            },
            "rebuild()" to { measure("rebuild() 全部重新执行") { view.rebuild() } },
            "测量 ×100" to {
                measure("同样的宽度测量 ×100") {
                    val width = View.MeasureSpec.makeMeasureSpec(view.width, View.MeasureSpec.EXACTLY)
                    repeat(100) { view.measure(width, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)) }
                }
            },
            "解析 1.5 万命中" to {
                val text = "@user #tag# word ".repeat(5000)
                val rules = listOf(
                    ZLRichRule.regex("mention", "@\\w+") { },
                    ZLRichRule.regex("topic", "#\\w+#") { },
                    ZLRichRule.regex("word", "\\w+", priority = -1) { },
                )
                val start = System.nanoTime()
                val matches = ZLRichParser.resolve(text, rules)
                stats.text = "ZLRichParser.resolve：%.2f ms\n".format((System.nanoTime() - start) / 1e6) +
                    "文本 ${text.length} 字符，3 条规则，生效命中 ${matches.size} 个\n（含正则匹配耗时；view 未参与）"
            },
        )
        val rows = buttons.chunked(3).map { row ->
            LinearLayout(this).apply {
                row.forEach { (name, action) ->
                    addView(textButton(name) { action() }.apply { textSize = 12f }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                }
            }
        }
        card.setContent(vertical(view, *rows.toTypedArray(), stats))
        view.post { measure("首次渲染") { view.text = source } }
        return card
    }
}
