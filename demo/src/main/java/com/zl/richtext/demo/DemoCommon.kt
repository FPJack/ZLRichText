package com.zl.richtext.demo

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.zl.richtext.ZLRichTextView
import com.zl.richtext.ZLTagListener
import com.zl.richtext.ZLTagTextView
import com.zl.richtext.rule.ZLRichMatch
import com.zl.richtext.style.ZLRichWidget
import com.zl.richtext.style.ZLTextStyle
import com.zl.richtext.style.ZLWidgetAlignment
import com.zl.richtext.style.ZLWidgetPosition
import com.zl.richtext.tag.ZLTagInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Material colours of the demos (the same values as the iOS example). */
object M {
    const val BLUE = 0xFF2196F3.toInt()
    const val DEEP_ORANGE = 0xFFFF5722.toInt()
    const val RED = 0xFFF44336.toInt()
    const val GREEN = 0xFF4CAF50.toInt()
    const val PURPLE = 0xFF9C27B0.toInt()
    const val DEEP_PURPLE = 0xFF673AB7.toInt()
    const val ORANGE = 0xFFFF9800.toInt()
    const val GREY = 0xFF9E9E9E.toInt()
    const val TEAL = 0xFF009688.toInt()
    const val PINK = 0xFFE91E63.toInt()
    const val INDIGO = 0xFF3F51B5.toInt()
    const val BROWN = 0xFF795548.toInt()
    const val AMBER = 0xFFFFC107.toInt()
    const val TEXT = 0xDE000000.toInt()
    const val LIGHT_PURPLE = 0xFFEADDFF.toInt()

    /** [color] with [alpha] (0 – 1). */
    fun alpha(color: Int, alpha: Float): Int = (color and 0x00FFFFFF) or ((alpha * 255).roundToInt() shl 24)
}

/** Plain text style of the demos. */
val BASE_STYLE: ZLTextStyle = ZLTextStyle().textSizeSp(15f).color(M.TEXT)

fun Context.dp(value: Float): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics).roundToInt()

// MARK: - Tap log

class TapRecord(val caseName: String, val info: ZLTagInfo, val action: String) {
    val time: Date = Date()
}

/** Tap history shared by all cases of a page. */
class TapLog {
    var last: TapRecord? = null
        private set
    val history = ArrayList<TapRecord>()
    private val listeners = ArrayList<() -> Unit>()

    fun add(caseName: String, info: ZLTagInfo, action: String = "onTagTap（兜底）") {
        val record = TapRecord(caseName, info, action)
        last = record
        history.add(0, record)
        if (history.size > 20) history.removeAt(history.size - 1)
        Log.d("ZLRichText", "[$caseName] $action -> $info")
        listeners.forEach { it() }
    }

    fun clear() {
        last = null
        history.clear()
        listeners.forEach { it() }
    }

    fun observe(listener: () -> Unit) {
        listeners += listener
        listener()
    }
}

/** A rule match shown in the tap panel. */
fun ruleInfo(match: ZLRichMatch): ZLTagInfo = ZLTagInfo(match.index, "rule:${match.rule.name}", match.text, match.text)

/** Callbacks of one case that report to the tap log. */
class CaseHooks(val caseName: String, private val log: TapLog) {
    /** The default `onTagClickListener` of a case. */
    val onTagClick: ZLTagListener = ZLTagListener { log.add(caseName, it) }

    fun tap(info: ZLTagInfo, action: String) = log.add(caseName, info, action)

    fun ruleTap(match: ZLRichMatch, action: String) = log.add(caseName, ruleInfo(match), action)

    /** A synthetic record (expand, copy …). */
    fun event(name: String, text: String, action: String, index: Int = 0, raw: String = "") =
        log.add(caseName, ZLTagInfo(index, name, text, raw), action)
}

// MARK: - Views

fun Context.label(text: CharSequence, sizeSp: Float = 12f, color: Int = 0x73000000, bold: Boolean = false, mono: Boolean = false) =
    TextView(this).apply {
        this.text = text
        textSize = sizeSp
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        if (mono) typeface = Typeface.MONOSPACE
    }

fun matchWidth(): LinearLayout.LayoutParams =
    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

fun Context.vertical(vararg views: View, spacing: Int = dp(8f)): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    views.forEachIndexed { i, view ->
        addView(view, matchWidth().apply { if (i > 0) topMargin = spacing })
    }
}

/** White rounded card. */
open class CardView(context: Context) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        val padding = context.dp(12f)
        setPadding(padding, padding, padding, padding)
        background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = context.dp(12f).toFloat()
        }
        elevation = context.dp(1f).toFloat()
    }

    fun add(view: View, top: Int = 0): View {
        addView(view, matchWidth().apply { topMargin = top })
        return view
    }
}

/** One case: title + raw string + rendered result + parse results. */
class DemoCard(context: Context, title: String, source: String) : CardView(context) {
    init {
        tag = title
    }

    private val content = LinearLayout(context).apply { orientation = VERTICAL }
    private val parsedTitle = context.label("")
    private val parsedText = context.label("", sizeSp = 11f, color = 0x8A000000.toInt(), mono = true)

    init {
        add(context.label(title, sizeSp = 15f, color = Color.BLACK, bold = true))
        add(context.label("原始字符串："), context.dp(8f))
        add(context.label(source, sizeSp = 12f, color = Color.BLACK, mono = true).apply {
            setTextIsSelectable(true)
            setPadding(context.dp(8f), context.dp(8f), context.dp(8f), context.dp(8f))
            background = GradientDrawable().apply {
                setColor(0xFFF5F5F5.toInt())
                cornerRadius = context.dp(6f).toFloat()
            }
        }, context.dp(4f))
        add(context.label("渲染效果："), context.dp(8f))
        add(content, context.dp(4f))
        add(parsedTitle, context.dp(8f))
        add(parsedText, context.dp(4f))
        parsedTitle.visibility = GONE
        parsedText.visibility = GONE
    }

    fun setContent(view: View) {
        content.removeAllViews()
        content.addView(view, matchWidth())
    }

    /** `onTagsParsedListener` results, nested tags indented, attributes listed. */
    fun showTags(tags: List<ZLTagInfo>) {
        parsedTitle.text = "onParsed 回调（共 ${tags.size} 个标签）："
        parsedText.text = tags.joinToString("\n") { t ->
            val indent = "    ".repeat(t.depth)
            val attrs = if (t.attributes.isEmpty()) "" else "  attributes=" + t.attributes.toSortedMap().entries.joinToString(" ") { "${it.key}=\"${it.value}\"" }
            val level = if (t.name.startsWith("h")) "  level=${t.level ?: "null"}" else ""
            "$indent#${t.index}  <${t.name}>  text=\"${t.text}\"$level$attrs"
        }
        parsedTitle.visibility = VISIBLE
        parsedText.visibility = if (tags.isEmpty()) GONE else VISIBLE
    }

    /** Effective matches of rule cases. */
    fun showMatches(matches: List<ZLRichMatch>) {
        parsedTitle.text = "规则命中 matches（共 ${matches.size} 个）："
        parsedText.text = matches.joinToString("\n") { "#${it.index}  [${it.rule.name}]  text=\"${it.text}\"  priority=${it.rule.priority}" }
        parsedTitle.visibility = VISIBLE
        parsedText.visibility = if (matches.isEmpty()) GONE else VISIBLE
    }
}

/** A `ZLTagTextView` with the defaults of the iOS `addCase`. */
fun Context.newTagView(): ZLTagTextView = ZLTagTextView(this).apply {
    style = BASE_STYLE
    gravity = Gravity.TOP or Gravity.START
}

fun Context.newRichView(): ZLRichTextView = ZLRichTextView(this).apply {
    style = BASE_STYLE
    gravity = Gravity.TOP or Gravity.START
}

/**
 * A tag case (the iOS `addCase`): default style, `onTagClickListener` → tap log (when
 * [tappable]), `onTagsParsedListener` → card; [configure] runs before the text is set.
 */
fun Context.tagCase(
    log: TapLog,
    title: String,
    source: String,
    tappable: Boolean = true,
    configure: ZLTagTextView.(CaseHooks) -> Unit = {},
): DemoCard {
    val card = DemoCard(this, title, source)
    val hooks = CaseHooks(title, log)
    val view = newTagView().apply {
        if (tappable) onTagClickListener = hooks.onTagClick
        onTagsParsedListener = com.zl.richtext.ZLTagParsedListener { card.showTags(it) }
        configure(hooks)
        text = source
    }
    card.setContent(view)
    return card
}

/** A rule case on the base `ZLRichTextView` (no tags); the card shows the matches. */
fun Context.ruleCase(log: TapLog, title: String, source: String, configure: ZLRichTextView.(CaseHooks) -> Unit): DemoCard {
    val card = DemoCard(this, title, source)
    val hooks = CaseHooks(title, log)
    val view = newRichView().apply {
        configure(hooks)
        text = source
    }
    card.setContent(view)
    card.showMatches(view.matches)
    return card
}

/** Top panel: everything about the last tap. */
class TapInfoPanel(context: Context, log: TapLog) : LinearLayout(context) {
    private val title = context.label("", sizeSp = 14f)
    private val table = context.label("", sizeSp = 13f, color = Color.BLACK, mono = true)

    init {
        orientation = VERTICAL
        setBackgroundColor(0xFFF3F0FA.toInt())
        val padding = context.dp(12f)
        setPadding(padding, padding, padding, padding)
        // A fixed height: the page below does not jump when the first record appears.
        minimumHeight = context.dp(172f)
        addView(title, matchWidth())
        addView(table, matchWidth().apply { topMargin = context.dp(6f) })
        log.observe { update(log.last) }
    }

    private fun update(record: TapRecord?) {
        if (record == null) {
            title.text = "👆 点击下方任意高亮文字，这里会显示点击得到的所有信息"
            title.setTextColor(0x8A000000.toInt())
            title.setTypeface(null, Typeface.NORMAL)
            table.visibility = GONE
            return
        }
        title.text = "最近点击 · ${record.caseName}\n动作：${record.action}"
        title.setTextColor(Color.BLACK)
        title.setTypeface(null, Typeface.BOLD)
        table.visibility = VISIBLE
        fun q(s: String?) = s?.let { "\"$it\"" } ?: "null"
        val info = record.info
        table.text = listOf(
            "index" to "${info.index}", "name" to q(info.name), "level" to "${info.level ?: "null"}",
            "text" to q(info.text), "raw" to q(info.raw),
        ).joinToString("\n") { (k, v) -> k.padEnd(7) + v }
    }
}

/** Tap history. */
class HistoryCard(context: Context, log: TapLog) : CardView(context) {
    private val title = context.label("", sizeSp = 15f, color = Color.BLACK, bold = true)
    private val content = context.label("", sizeSp = 11f, color = Color.BLACK, mono = true)
    private val format = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    init {
        add(title)
        add(content, context.dp(6f))
        log.observe {
            title.text = "点击历史（${log.history.size}）"
            content.text = if (log.history.isEmpty()) {
                "暂无"
            } else {
                log.history.joinToString("\n") { r ->
                    "${format.format(r.time)}  ${r.caseName.substringBefore(' ')} <${r.info.name}> index=${r.info.index} " +
                        "text=\"${r.info.text}\" action=${r.action}"
                }
            }
        }
    }
}

// MARK: - Controls

/** Single selection segmented buttons (`UISegmentedControl`). */
fun Context.segmented(items: List<String>, selected: Int?, onChange: (Int) -> Unit): MaterialButtonToggleGroup =
    MaterialButtonToggleGroup(this).apply {
        isSingleSelection = true
        items.forEachIndexed { i, item ->
            addView(MaterialButton(this@segmented, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                id = View.generateViewId()
                text = item
                textSize = 12f
                isAllCaps = false
                minWidth = 0
                minimumWidth = 0
                setPadding(dp(8f), 0, dp(8f), 0)
            })
        }
        selected?.let { check(getChildAt(it).id) }
        addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked) onChange(group.indexOfChild(group.findViewById(checkedId)))
        }
    }

/** Label + switch. */
fun Context.switchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(label(title, sizeSp = 12f, color = Color.BLACK, mono = true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(MaterialSwitch(this@switchRow).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, value -> onChange(value) }
        })
    }

fun Context.textButton(title: String, onClick: () -> Unit): MaterialButton =
    MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
        text = title
        isAllCaps = false
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        setOnClickListener { onClick() }
    }

/** An action sheet: [items] plus 取消. */
fun Context.showActionSheet(title: String, message: String? = null, items: List<Pair<String, () -> Unit>>) {
    AlertDialog.Builder(this)
        .setTitle(title)
        .apply { if (message != null) setMessage(message) }
        .setItems(items.map { it.first }.toTypedArray()) { _, which -> items[which].second() }
        .setNegativeButton("取消", null)
        .show()
}

// MARK: - Inline widgets

object DemoWidgets {
    /** A rounded label (the `PaddingLabel` of the iOS example). */
    fun pill(
        context: Context,
        text: String,
        background: Int,
        textSizeSp: Float = 12f,
        bold: Boolean = false,
        paddingH: Float = 6f,
        paddingV: Float = 1f,
        radius: Float = 4f,
    ): TextView = TextView(context).apply {
        this.text = text
        textSize = textSizeSp
        setTextColor(Color.WHITE)
        includeFontPadding = false
        if (bold) setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER
        setPadding(context.dp(paddingH), context.dp(paddingV + 1), context.dp(paddingH), context.dp(paddingV + 1))
        this.background = GradientDrawable().apply {
            setColor(background)
            cornerRadius = context.dp(radius).toFloat()
        }
    }

    /** Button style (replaces the text). */
    fun button(context: Context, title: String): ZLRichWidget =
        ZLRichWidget.view(pill(context, title, M.BLUE, paddingH = 10f, paddingV = 2f, radius = 12f))

    /** Red dot with spacing. */
    fun redDot(context: Context, position: ZLWidgetPosition = ZLWidgetPosition.BEFORE): ZLRichWidget {
        val dot = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(M.RED)
        }
        val size = context.dp(8f)
        return ZLRichWidget(dot, size, size, position = position, marginStart = context.dp(2f), marginEnd = context.dp(2f))
    }

    /** VIP badge with spacing. */
    fun vipBadge(context: Context, position: ZLWidgetPosition = ZLWidgetPosition.AFTER): ZLRichWidget =
        ZLRichWidget.view(pill(context, "VIP", M.AMBER, textSizeSp = 10f, bold = true, paddingH = 4f, paddingV = 0f))
            .copy(position = position, marginStart = context.dp(2f), marginEnd = context.dp(2f))

    /** An icon in a fixed colour (`null`: follows the text colour). */
    fun icon(context: Context, @DrawableRes id: Int, sizeDp: Float = 16f, color: Int? = null, alignment: ZLWidgetAlignment = ZLWidgetAlignment.CENTER): ZLRichWidget {
        if (color == null) return ZLRichWidget.icon(context, id, sizeDp, tintWithTextColor = true, alignment = alignment)
        val drawable = requireNotNull(AppCompatResources.getDrawable(context, id)).mutate().apply { setTint(color) }
        val size = context.dp(sizeDp)
        return ZLRichWidget(drawable, size, size, alignment)
    }

    /** Checkbox handling its own taps (does not trigger the tag tap). */
    fun checkbox(context: Context, checked: Boolean, onToggle: () -> Unit): ZLRichWidget =
        icon(context, if (checked) R.drawable.ic_check_circle else R.drawable.ic_circle, 18f, if (checked) M.GREEN else M.GREY)
            .copy(marginStart = context.dp(2f), marginEnd = context.dp(2f), onClick = Runnable(onToggle))
}
