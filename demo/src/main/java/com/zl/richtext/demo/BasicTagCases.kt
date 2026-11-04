package com.zl.richtext.demo

import android.content.Context
import android.view.View
import com.zl.richtext.ZLTagListener
import com.zl.richtext.ZLTagSpanBuilder
import com.zl.richtext.ZLTagStyleBuilder
import com.zl.richtext.ZLTagTextView
import com.zl.richtext.ZLTagWidgetBuilder
import com.zl.richtext.style.ZLTextStyle
import com.zl.richtext.style.ZLWidgetPosition

/** Cases 1 – 16 of the iOS example: tag basics and the Arabic (right-to-left) cases. */
object BasicTagCases {

    fun create(context: Context, log: TapLog): List<View> = with(context) {
        listOf(
            case1(log), case2(log), case3(log), case4(log), case5(log), case6(log), case7(log), case8(log),
            case9(log), case10(log),
            checkboxCase(log, "11. Widget 自带交互（自己的 onClick）",
                "<h0>check</h0> I have read and agree to the <h1>User Agreement</h1> and <h2>Privacy Policy</h2>."),
            // Arabic (RTL)
            case12(log), case13(log), case14(log), case15(log),
            checkboxCase(log, "16. 阿拉伯语 + 勾选框（自己的 onClick）",
                "<h0>check</h0> لقد قرأت وأوافق على <h1>اتفاقية المستخدم</h1> و<h2>سياسة الخصوصية</h2>."),
        )
    }

    /** 1. 按标签名区分样式（tagStyles） */
    private fun Context.case1(log: TapLog) = tagCase(
        log, "1. 按标签名区分样式（h1 / h2）",
        "By logging in or registering an account, you agree to the <h1>User Agreement</h1> and <h2>Privacy Policy</h2>.",
    ) {
        tagStyle = ZLTextStyle().textSizeSp(15f).bold()
        tagStyles = mapOf(
            "h1" to ZLTextStyle().color(M.BLUE).underline(),
            "h2" to ZLTextStyle().color(M.DEEP_ORANGE),
        )
    }

    /** 2. 只用 <h>，按 index 区分样式（tagStyleBuilder） */
    private fun Context.case2(log: TapLog) = tagCase(
        log, "2. 同名标签 <h>，按 index 区分样式", "Fruits: <h>Apple</h>, <h>Banana<h/> and <h>Cherry</h>.",
    ) {
        val colors = intArrayOf(M.RED, M.GREEN, M.PURPLE)
        tagStyleBuilder = ZLTagStyleBuilder { info -> ZLTextStyle().color(colors[info.index % colors.size]).bold() }
    }

    /** 3. 多种标签名 + 两种闭合写法 + 大小写 */
    private fun Context.case3(log: TapLog) = tagCase(
        log, "3. h / h1 / h2 / h3 / H4，</hN> 与 <hN/> 闭合",
        "<h>Default</h>, <h1>Level one<h1/>, <h2>Level two</h2>, <h3>Level three<h3/> and <H4>Upper case</H4>.",
    ) {
        tagStyle = ZLTextStyle().color(M.BLUE)
        tagStyles = mapOf(
            "h1" to ZLTextStyle().bold().underline(),
            "h2" to ZLTextStyle().color(M.GREEN).italic(),
            "h3" to ZLTextStyle().color(M.PURPLE).backgroundColor(0xFFEDE7F6.toInt()),
            "h4" to ZLTextStyle().color(M.RED).strikethrough(),
        )
    }

    /** 4. 按标签名区分点击（tagClickListeners），没有 handler 的走 onTagClickListener 兜底 */
    private fun Context.case4(log: TapLog) = tagCase(
        log, "4. 按标签名区分点击（setTagClickListener）",
        "Read <h1>User Agreement</h1>, <h2>Privacy Policy</h2> and <h3>FAQ</h3> (h3 has no handler → onTagClickListener).",
    ) { hooks ->
        tagStyles = mapOf(
            "h1" to ZLTextStyle().color(M.BLUE),
            "h2" to ZLTextStyle().color(M.DEEP_ORANGE),
            "h3" to ZLTextStyle().color(M.TEAL),
        )
        setTagClickListener("h1") { hooks.tap(it, "h1 handler → 打开用户协议") }
        setTagClickListener("h2") { hooks.tap(it, "h2 handler → 打开隐私政策") }
    }

    /** 5. 按标签名区分按下高亮（tagHighlightStyles） */
    private fun Context.case5(log: TapLog) = tagCase(
        log, "5. 按标签名区分按下高亮（按住查看）",
        "Press and hold: <h1>Yellow bg</h1> / <h2>Red text</h2> / <h3>No highlight</h3> / <h>Default grey</h>.",
    ) {
        tagStyle = ZLTextStyle().color(M.BLUE)
        tagHighlightStyle = ZLTextStyle().backgroundColor(0xFFE0E0E0.toInt()) // 默认（<h>）
        tagHighlightStyles = mapOf(
            "h1" to ZLTextStyle().backgroundColor(0xFFFFF59D.toInt()),
            "h2" to ZLTextStyle().color(M.RED).underline(),
            "h3" to ZLTextStyle.NONE, // 不高亮
        )
    }

    /** 6. 容错：[] 原样展示 / 开闭不匹配 / 未闭合 / 嵌套 / 非 h 标签 */
    private fun Context.case6(log: TapLog) = tagCase(
        log, "6. 容错：[] 原样 / 不匹配 / 未闭合 / 嵌套",
        "Brackets kept: <h1>Terms[1]</h1>; mismatched: <h1>X</h2>; unclosed: <h2>Y; nested: <h1>a<h2>b</h2>c</h1>; not h: <hx>no</hx>.",
    ) {
        tagStyles = mapOf(
            "h1" to ZLTextStyle().color(M.TEAL).bold(),
            "h2" to ZLTextStyle().color(M.PINK).bold(),
        )
    }

    /** 7. tagSpanBuilder：按标签名加图标 */
    private fun Context.case7(log: TapLog) = tagCase(
        log, "7. tagSpanBuilder 按标签名加图标",
        "Please read <h1>User Agreement</h1> and <h2>Privacy Policy</h2> carefully, or <h>skip</h>.",
    ) {
        val icons = mapOf("h1" to R.drawable.ic_doc, "h2" to R.drawable.ic_shield)
        tagStyle = ZLTextStyle().color(M.INDIGO)
        tagSpanBuilder = ZLTagSpanBuilder { info, style, ctx ->
            // 返回 null 使用默认样式；图标跟随文字颜色，点击图标同样触发标签点击
            val icon = icons[info.name] ?: return@ZLTagSpanBuilder null
            android.text.SpannableStringBuilder()
                .append(ctx.widgetText(DemoWidgets.icon(this@case7, icon, 16f), style))
                .append(ctx.styled("《${info.text}》", style))
        }
    }

    /** 8. 无点击事件 */
    private fun Context.case8(log: TapLog) = tagCase(
        log, "8. 无点击（无 onTagClickListener / tag 点击）", "Plain text. <h1>Not clickable</h1> and <h2>neither</h2>.", tappable = false,
    ) {
        tagStyles = mapOf("h1" to ZLTextStyle().color(M.BROWN), "h2" to ZLTextStyle().color(M.GREY))
    }

    /** 9. 多行 + maxLines 省略 */
    private fun Context.case9(log: TapLog) = tagCase(
        log, "9. maxLines = 2 + 省略号",
        "This is a very long text to demonstrate the maxLines and overflow parameters. <h1>First</h1> link, some more words, " +
            "<h2>Second</h2> link, and even more words that will be cut off here <h3>Hidden</h3>.",
    ) {
        maxLines = 2
        tagStyle = ZLTextStyle().color(M.BLUE)
    }

    /** 10. tagWidgetBuilder：按标签名展示不同的行内 Widget */
    private fun Context.case10(log: TapLog) = tagCase(
        log, "10. tagWidgetBuilder 按标签名自定义 Widget",
        "Replace: <h1>Follow</h1>  Before: <h2>Hot topic</h2>  After: <h3>Alice</h3>  Default: <h>Plain</h>",
    ) {
        tagStyle = ZLTextStyle().color(M.BLUE)
        tagWidgetBuilder = ZLTagWidgetBuilder { info, _ ->
            when (info.name) {
                "h1" -> DemoWidgets.button(this@case10, "+ ${info.text} #${info.index}") // 替换文字
                "h2" -> DemoWidgets.redDot(this@case10, ZLWidgetPosition.BEFORE)        // 文字前
                "h3" -> DemoWidgets.vipBadge(this@case10, ZLWidgetPosition.AFTER)        // 文字后
                else -> null                                                             // 默认文字
            }
        }
    }

    /** 11 / 16. 勾选框自己处理点击：状态变化后重新渲染标签 */
    private fun Context.checkboxCase(log: TapLog, title: String, source: String): View {
        var agreed = false
        lateinit var view: ZLTagTextView
        val card = tagCase(log, title, source) { hooks ->
            view = this
            tagStyle = ZLTextStyle().color(M.BLUE)
            tagWidgetBuilder = ZLTagWidgetBuilder { info, _ ->
                if (info.name != "h0") return@ZLTagWidgetBuilder null
                DemoWidgets.checkbox(this@checkboxCase, agreed) {
                    agreed = !agreed
                    hooks.event("checkbox", if (agreed) "checked" else "unchecked", "勾选框自己的 onClick")
                    view.setNeedsRebuild(ZLTagTextView.TAG_RULE_NAME)
                }
            }
        }
        return card
    }

    // MARK: - 阿拉伯语（RTL）

    /** 12. 阿拉伯语：按标签名区分样式与点击 */
    private fun Context.case12(log: TapLog) = tagCase(
        log, "12. 阿拉伯语：按标签名区分样式与点击",
        "بتسجيل الدخول أو إنشاء حساب، فإنك توافق على <h1>اتفاقية المستخدم</h1> و<h2>سياسة الخصوصية</h2>.",
    ) { hooks ->
        tagStyle = ZLTextStyle().bold()
        tagStyles = mapOf("h1" to ZLTextStyle().color(M.BLUE).underline(), "h2" to ZLTextStyle().color(M.DEEP_ORANGE))
        setTagClickListeners(
            mapOf(
                "h1" to ZLTagListener { hooks.tap(it, "h1 handler → اتفاقية المستخدم") },
                "h2" to ZLTagListener { hooks.tap(it, "h2 handler → سياسة الخصوصية") },
            ),
        )
    }

    /** 13. 阿拉伯语 + 英文混排（双向文本） */
    private fun Context.case13(log: TapLog) = tagCase(
        log, "13. 阿拉伯语 + 英文混排",
        "اقرأ <h1>Terms of Service</h1> ثم <h2>الأسئلة الشائعة</h2> وتواصل مع <h3>Support Team</h3> أو <h>نحن</h>.",
    ) {
        tagStyle = ZLTextStyle().color(M.BLUE)
        tagStyles = mapOf(
            "h1" to ZLTextStyle().bold().underline(),
            "h2" to ZLTextStyle().backgroundColor(0xFFE0E0E0.toInt()),
            "h3" to ZLTextStyle().color(M.GREEN),
        )
    }

    /** 14. 阿拉伯语多行 + maxLines 省略 */
    private fun Context.case14(log: TapLog) = tagCase(
        log, "14. 阿拉伯语 maxLines = 2 + 省略号",
        "هذا نص طويل جدًا لإظهار خاصية الحد الأقصى لعدد الأسطر. <h1>الرابط الأول</h1> بعض الكلمات الإضافية، " +
            "<h2>الرابط الثاني</h2> والمزيد من الكلمات التي سيتم قطعها هنا <h3>مخفي</h3>.",
    ) {
        maxLines = 2
        tagStyle = ZLTextStyle().color(M.BLUE)
    }

    /** 15. 阿拉伯语 + 按标签名自定义 Widget / 图标 */
    private fun Context.case15(log: TapLog) = tagCase(
        log, "15. 阿拉伯语 + 自定义 Widget / 图标",
        "تابع: <h1>متابعة</h1>  جديد: <h2>موضوع رائج</h2>  مميز: <h3>علي</h3>  اقرأ <h4>الاتفاقية</h4>.",
    ) {
        tagStyle = ZLTextStyle().color(M.BLUE)
        tagSpanBuilder = ZLTagSpanBuilder { info, style, ctx ->
            if (info.name != "h4") return@ZLTagSpanBuilder null
            android.text.SpannableStringBuilder()
                .append(ctx.widgetText(DemoWidgets.icon(this@case15, R.drawable.ic_doc, 16f), style))
                .append(ctx.styled("«${info.text}»", style))
        }
        tagWidgetBuilder = ZLTagWidgetBuilder { info, _ ->
            when (info.name) {
                "h1" -> DemoWidgets.button(this@case15, "+ ${info.text}")
                "h2" -> DemoWidgets.redDot(this@case15, ZLWidgetPosition.BEFORE)
                "h3" -> DemoWidgets.vipBadge(this@case15, ZLWidgetPosition.AFTER)
                else -> null
            }
        }
    }
}
