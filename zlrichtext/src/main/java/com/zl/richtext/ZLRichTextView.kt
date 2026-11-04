package com.zl.richtext

import android.content.Context
import android.content.res.Configuration
import android.graphics.RectF
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.util.AttributeSet
import com.zl.richtext.internal.ZLWidgetSpan
import com.zl.richtext.rule.ZLRange
import com.zl.richtext.rule.ZLRichMatch
import com.zl.richtext.rule.ZLRichMatchListener
import com.zl.richtext.rule.ZLRichParser
import com.zl.richtext.rule.ZLRichRule
import com.zl.richtext.rule.ZLRichRuleContext
import com.zl.richtext.rule.ZLRichRuleHandler
import com.zl.richtext.rule.ZLRichSegment
import com.zl.richtext.rule.ZLRuleLink
import com.zl.richtext.style.ZLTextStyle

/** Long press / double tap on the text. */
public fun interface ZLGestureListener {
    public fun onGesture(info: ZLGestureInfo)
}

/** The expand state changed after a tap on the expand / collapse token (or [ZLRichTextView.toggleExpanded]). */
public fun interface ZLExpandListener {
    public fun onExpandChanged(expanded: Boolean)
}

/** Takes over taps on the expand (`wantsExpand == true`) / collapse token. */
public fun interface ZLExpandTokenClickListener {
    public fun onExpandTokenClick(view: ZLRichTextView, wantsExpand: Boolean)
}

/**
 * Rule based rich text view (the counterpart of the iOS `ZLRichLabel`).
 *
 * Every [ZLRichRule] finds content in the text; overlapping matches are resolved by priority;
 * each match is rendered by its handler (styles, replacement text, inline widgets, taps,
 * press highlights).
 *
 * ```kotlin
 * view.style = ZLTextStyle().textSizeSp(15f)
 * view.addRule("mention", "@\\w+", priority = 10) { ctx ->
 *     ctx.addStyle(ZLTextStyle().color(PURPLE))
 *     ctx.onTap = ZLRichMatchListener { match -> openProfile(match.text) }
 * }
 * view.text = "Hi @alice"
 * ```
 *
 * Render cache: handler results are reused while the rule object, the matched text and its
 * position stay the same. Changing [highlightStyle], the tokens or the expand state never runs
 * handlers; [style], configuration changes (dark mode, font scale) and [rebuild] run all of them.
 *
 * Subclasses provide built-in rules through [builtInRules] (that is how [ZLTagTextView] works).
 */
public open class ZLRichTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle,
) : ZLAttributedTextView(context, attrs, defStyleAttr) {

    /** The raw text, parsed by the rules (the same as `setText`). */
    public var rawText: String = ""
        set(value) {
            if (field == value) return
            field = value
            setNeedsReparse()
        }

    /** Style of plain text (merged on top of the TextView's text appearance). */
    public var style: ZLTextStyle = ZLTextStyle.NONE
        set(value) {
            field = value
            setNeedsRebuild()
        }

    /** Default press highlight of tappable content; [ZLTextStyle.NONE] for no highlight. */
    public var highlightStyle: ZLTextStyle = ZLTextStyle.NONE
        set(value) {
            field = value
            activeLinkStyle = value
            setNeedsCompose()
        }

    private val ruleList = ArrayList<ZLRichRule>()

    /** External rules (built-in rules of subclasses come first). */
    public val rules: List<ZLRichRule> get() = ruleList

    /** Built-in rules of subclasses, before [rules] at equal priority. Call [setNeedsReparse] when they change. */
    protected open val builtInRules: List<ZLRichRule> get() = emptyList()

    /** `builtInRules + rules`. */
    public val allRules: List<ZLRichRule> get() = builtInRules + ruleList

    /** Adds a rule; a rule with the same name is replaced. */
    public fun addRule(rule: ZLRichRule) {
        val index = ruleList.indexOfFirst { it.name == rule.name }
        if (index >= 0) ruleList[index] = rule else ruleList += rule
        setNeedsReparse()
    }

    /** Adds a regular expression rule. */
    @JvmOverloads
    public fun addRule(name: String, pattern: String, priority: Int = 0, handler: ZLRichRuleHandler): ZLRichRule =
        ZLRichRule.regex(name, pattern, priority, handler = handler).also(::addRule)

    public fun removeRule(name: String) {
        if (ruleList.removeAll { it.name == name }) setNeedsReparse()
    }

    public fun removeAllRules() {
        ruleList.clear()
        setNeedsReparse()
    }

    // MARK: Expand / collapse

    /**
     * "Expand" token such as `… 全文`: shown instead of the ellipsis when the text has more than
     * `maxLines` lines; tapping it expands the text. Spans it does not set use [style].
     */
    public var expandToken: CharSequence?
        get() = expandTokenText
        set(value) {
            expandTokenText = value
        }

    /** "Collapse" token appended to the expanded text, e.g. ` 收起`; `null`: no collapsing. */
    public var collapseToken: CharSequence?
        get() = collapseTokenText
        set(value) {
            collapseTokenText = value
        }

    /** Only collapse texts with more lines than this (when larger than `maxLines`). */
    public var collapseThreshold: Int
        get() = collapseLineThreshold
        set(value) {
            collapseLineThreshold = value
        }

    /** Expand state. Setting it does not call [onExpandChangeListener] (restore recycled items with it). */
    public var isExpanded: Boolean
        get() = expandedState
        set(value) {
            expandedState = value
        }

    /** Called after the expand / collapse token was tapped (or [toggleExpanded]). */
    public var onExpandChangeListener: ZLExpandListener? = null

    /** Takes over token taps: the view no longer toggles by itself, call [toggleExpanded] to toggle. */
    public var onExpandTokenClickListener: ZLExpandTokenClickListener? = null

    /** Toggles the expand state and calls [onExpandChangeListener], like tapping the token. */
    public fun toggleExpanded() {
        expandedState = !expandedState
        onExpandChangeListener?.onExpandChanged(expandedState)
    }

    /** Tappable area of the expand token (view coordinates), `null` when not shown. */
    public val expandTokenRect: RectF? get() = expandTokenBounds

    override fun onExpandTokenClick(wantsExpand: Boolean) {
        val listener = onExpandTokenClickListener
        if (listener != null) {
            listener.onExpandTokenClick(this, wantsExpand)
        } else if (expandedState != wantsExpand) {
            toggleExpanded()
        }
    }

    // MARK: Gestures

    /** Long press on the text; matches with their own `ctx.onLongPress` take precedence. */
    public var onLongPressListener: ZLGestureListener? = null
        set(value) {
            field = value
            refreshLongClickable()
        }

    /** Double tap. While set, single taps on links wait for the double tap timeout (~300 ms). */
    public var onDoubleTapListener: ZLGestureListener? = null
        set(value) {
            field = value
            refreshLongClickable()
        }

    override val handlesLongPress: Boolean get() = onLongPressListener != null

    override fun onLongPressText(x: Float, y: Float): Boolean {
        val listener = onLongPressListener ?: return false
        listener.onGesture(gestureInfoAt(x, y))
        return true
    }

    override val handlesDoubleTap: Boolean get() = onDoubleTapListener != null

    override fun onDoubleTapText(x: Float, y: Float) {
        onDoubleTapListener?.onGesture(gestureInfoAt(x, y))
    }

    // MARK: Parse & compose state

    private var parsedSegments: List<ZLRichSegment> = emptyList()
    private var parsedMatches: List<ZLRichMatch> = emptyList()
    private var needsParse = true
    private var needsCompose = true
    private val cache = HashMap<Triple<Int, Int, Int>, Rendered>()
    private var pieces: List<Pair<ZLRange, ZLRange>> = emptyList()
    private var pieceIsMatch: List<Boolean> = emptyList()
    private var displayMatches: List<Pair<ZLRange, ZLRichMatch>> = emptyList()

    /** Plain text and matches, in order. */
    public val segments: List<ZLRichSegment>
        get() {
            parseIfNeeded()
            return parsedSegments
        }

    /** Effective matches of all rules (built-in rules included). */
    public val matches: List<ZLRichMatch>
        get() {
            parseIfNeeded()
            return parsedMatches
        }

    private class Rendered(
        val match: ZLRichMatch,
        val content: CharSequence,
        val onTap: ZLRichMatchListener?,
        val onLongPress: ZLRichMatchListener?,
        val highlightStyle: ZLTextStyle?,
        val links: List<ZLRuleLink>,
    ) {
        fun isValidFor(other: ZLRichMatch): Boolean =
            match.rule === other.rule && match.index == other.index && match.range == other.range && match.text == other.text
    }

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.ZLRichTextView, defStyleAttr, 0)
        try {
            var highlight = ZLTextStyle.NONE
            if (a.hasValue(R.styleable.ZLRichTextView_zl_highlightTextColor)) {
                highlight = highlight.color(a.getColor(R.styleable.ZLRichTextView_zl_highlightTextColor, 0))
            }
            if (a.hasValue(R.styleable.ZLRichTextView_zl_highlightBackgroundColor)) {
                highlight = highlight.backgroundColor(a.getColor(R.styleable.ZLRichTextView_zl_highlightBackgroundColor, 0))
            }
            val tokenColor = if (a.hasValue(R.styleable.ZLRichTextView_zl_expandTokenColor)) {
                a.getColor(R.styleable.ZLRichTextView_zl_expandTokenColor, 0)
            } else {
                null
            }
            fun token(index: Int): CharSequence? = a.getText(index)?.let { text -> tokenColor?.let { colored(text, it) } ?: text }
            expandTokenText = token(R.styleable.ZLRichTextView_zl_expandToken)
            collapseTokenText = token(R.styleable.ZLRichTextView_zl_collapseToken)
            collapseLineThreshold = a.getInt(R.styleable.ZLRichTextView_zl_collapseThreshold, 0)
            expandedState = a.getBoolean(R.styleable.ZLRichTextView_zl_expanded, false)
            a.getText(R.styleable.ZLRichTextView_zl_copyRawMenuTitle)?.let { copyRawMenuTitle = it }
            // Rule content carries its own styles: links are not restyled.
            linkStyle = ZLTextStyle.NONE
            inactiveLinkStyle = null
            highlightStyle = highlight
        } finally {
            a.recycle()
        }
        rawText = initialText.toString()
    }

    override fun onTextAssigned(text: CharSequence) {
        rawText = text.toString()
    }

    override val baseTextStyle: ZLTextStyle get() = style

    // MARK: Invalidation

    /** Parses the text again (rules / built-in rule configuration changed). */
    protected fun setNeedsReparse() {
        needsParse = true
        setNeedsCompose()
    }

    /** Runs the handlers again: all of them, or only those of the rule named [ruleName]. */
    @JvmOverloads
    public fun setNeedsRebuild(ruleName: String? = null) {
        invalidateRenderCache(ruleName)
        setNeedsCompose()
    }

    /** Runs every rule handler again. */
    public fun rebuild(): Unit = setNeedsRebuild(null)

    /** Drops cached results without scheduling a layout (subclasses, e.g. in [didParse]). */
    protected fun invalidateRenderCache(ruleName: String?) {
        if (ruleName == null) cache.clear() else cache.values.removeAll { it.match.rule.name == ruleName }
    }

    private fun setNeedsCompose() {
        needsCompose = true
        invalidateDisplay()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        // Dark mode / font scale: handlers resolve colours and sizes again.
        cache.clear()
        needsCompose = true
        super.onConfigurationChanged(newConfig)
    }

    // MARK: Parse

    private fun parseIfNeeded() {
        if (!needsParse) return
        needsParse = false
        needsCompose = true
        parsedMatches = ZLRichParser.resolve(rawText, allRules)
        parsedSegments = ZLRichParser.segmentsOf(rawText, parsedMatches)
        didParse()
    }

    /** Called after parsing ([segments] / [matches] updated). */
    protected open fun didParse() {}

    // MARK: Compose

    override fun onPrepareContent() {
        parseIfNeeded()
        if (!needsCompose) return
        needsCompose = false
        compose()
    }

    private fun compose() {
        val text = SpannableStringBuilder()
        val links = ArrayList<ZLLink>()
        val pieceRanges = ArrayList<Pair<ZLRange, ZLRange>>()
        val pieceMatch = ArrayList<Boolean>()
        val shownMatches = ArrayList<Pair<ZLRange, ZLRichMatch>>()
        val next = HashMap<Triple<Int, Int, Int>, Rendered>()

        for (segment in parsedSegments) {
            val match = segment.match
            if (match == null) {
                val start = text.length
                text.append(segment.text)
                val rawStart = pieceRanges.lastOrNull()?.second?.end ?: 0
                pieceRanges += ZLRange(start, text.length) to ZLRange(rawStart, rawStart + segment.text.length)
                pieceMatch += false
                continue
            }
            val key = Triple(match.range.start, match.range.end, match.index)
            val cached = cache[key]
            val rendered = if (cached != null && cached.isValidFor(match)) {
                statistics.reusedMatches++
                cached
            } else {
                statistics.handlerCalls++
                render(match)
            }
            next[key] = rendered

            val start = text.length
            text.append(rendered.content)
            val range = ZLRange(start, text.length)
            pieceRanges += range to match.range
            pieceMatch += true
            if (!range.isEmpty) shownMatches += range to match
            val highlight = rendered.highlightStyle ?: highlightStyle
            if ((rendered.onTap != null || rendered.onLongPress != null) && !range.isEmpty) {
                links += ZLLink(
                    range.start, range.end,
                    style = ZLTextStyle.NONE,
                    activeStyle = highlight,
                    onClick = rendered.onTap?.let { tap -> ZLLinkListener { _, _ -> tap.onMatch(match) } },
                    onLongClick = rendered.onLongPress?.let { press -> ZLLinkListener { _, _ -> press.onMatch(match) } },
                )
            }
            for (link in rendered.links) {
                if (link.range.end > rendered.content.length) continue
                links += ZLLink(
                    start + link.range.start, start + link.range.end,
                    style = ZLTextStyle.NONE,
                    activeStyle = if (link.onTap == null) ZLTextStyle.NONE else link.highlightStyle ?: highlightStyle,
                    onClick = link.onTap?.let { tap -> ZLLinkListener { _, _ -> tap.onMatch(match) } },
                    onLongClick = link.onLongPress?.let { press -> ZLLinkListener { _, _ -> press.onMatch(match) } },
                )
            }
            // Widgets with their own tap handle it themselves (added last: they win).
            val content = rendered.content
            if (content is Spanned) {
                for (span in content.getSpans(0, content.length, ZLWidgetSpan::class.java)) {
                    val onClick = span.widget.onClick ?: continue
                    links += ZLLink(
                        start + content.getSpanStart(span), start + content.getSpanEnd(span),
                        style = ZLTextStyle.NONE,
                        activeStyle = ZLTextStyle.NONE,
                        onClick = { _, _ -> onClick.run() },
                    )
                }
            }
        }
        cache.clear()
        cache.putAll(next)
        pieces = pieceRanges
        pieceIsMatch = pieceMatch
        displayMatches = shownMatches
        setComposedContent(text, links)
    }

    private fun render(match: ZLRichMatch): Rendered {
        val ctx = ZLRichRuleContext(match, style, context)
        match.rule.handler.render(ctx)
        return Rendered(
            match = match,
            content = SpannableStringBuilder(ctx.content),
            onTap = ctx.onTap,
            onLongPress = ctx.onLongPress,
            highlightStyle = ctx.highlightStyle,
            links = ctx.links.toList(),
        )
    }

    // MARK: Queries

    private fun matchAtContentIndex(index: Int): Pair<ZLRange, ZLRichMatch>? =
        if (index < 0) null else displayMatches.firstOrNull { index in it.first }

    /** The rule match at `(x, y)` (view coordinates). */
    public fun matchAt(x: Float, y: Float): ZLRichMatch? {
        val index = characterIndexAt(x, y)
        if (index >= 0) return matchAtContentIndex(index)?.second
        if (!extendsLinkTouchArea) return null
        return linkAt(x, y)?.let { matchAtContentIndex(it.start)?.second }
    }

    /** Gesture information at `(x, y)`: character, match, raw and display text. */
    public fun gestureInfoAt(x: Float, y: Float): ZLGestureInfo {
        val index = characterIndexAt(x, y)
        val hit = matchAtContentIndex(index)
        val display = contentText.toString()
        return ZLGestureInfo(
            x = x,
            y = y,
            characterIndex = index,
            match = hit?.second,
            matchDisplayText = hit?.first?.let { display.substring(it.start, it.end) },
            rawText = rawText,
            displayText = display,
        )
    }

    /**
     * Raw text of a content range: plain text maps character by character, a partially covered
     * match maps to its whole raw text.
     */
    public fun rawTextForRange(range: ZLRange): String {
        prepareContentForQueries()
        val result = StringBuilder()
        for ((i, piece) in pieces.withIndex()) {
            val (display, raw) = piece
            val s = maxOf(range.start, display.start)
            val e = minOf(range.end, display.end)
            // An empty match strictly inside the range keeps its raw text.
            val inside = display.isEmpty && display.start > range.start && display.start < range.end
            if (e <= s && !inside) continue
            if (pieceIsMatch[i]) {
                result.append(rawText, raw.start, raw.end)
            } else {
                val offset = s - display.start
                result.append(rawText, raw.start + offset, raw.start + offset + (e - s))
            }
        }
        return result.toString()
    }

    override fun rawTextForContentRange(range: ZLRange): String = rawTextForRange(range)

    private fun prepareContentForQueries() = onPrepareContent()
}
