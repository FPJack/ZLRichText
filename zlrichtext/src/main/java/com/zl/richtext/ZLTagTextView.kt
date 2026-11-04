package com.zl.richtext

import android.content.Context
import android.text.SpannableStringBuilder
import android.util.AttributeSet
import android.util.TypedValue
import com.zl.richtext.internal.ZLStyleSpans
import com.zl.richtext.rule.ZLRange
import com.zl.richtext.rule.ZLRichMatch
import com.zl.richtext.rule.ZLRichMatchListener
import com.zl.richtext.rule.ZLRichRule
import com.zl.richtext.rule.ZLRichRuleContext
import com.zl.richtext.style.ZLDimension
import com.zl.richtext.style.ZLRichWidget
import com.zl.richtext.style.ZLTextStyle
import com.zl.richtext.style.ZLWidgetPosition
import com.zl.richtext.tag.ZLTagElement
import com.zl.richtext.tag.ZLTagInfo
import com.zl.richtext.tag.ZLTagMarkupPattern
import com.zl.richtext.tag.ZLTagNode
import com.zl.richtext.tag.ZLTagParser
import com.zl.richtext.tag.ZLTagParserConfiguration

/** Style of a single tag (by index / name / level / text / attributes); `null` adds nothing. */
public fun interface ZLTagStyleBuilder {
    public fun styleFor(info: ZLTagInfo): ZLTextStyle?
}

/**
 * Fully custom content of a tag (may contain widgets, see [ZLRichRuleContext.widgetText]);
 * `null` uses the default rendering. [style] is the resolved style of the tag.
 */
public fun interface ZLTagSpanBuilder {
    public fun spanFor(info: ZLTagInfo, style: ZLTextStyle, ctx: ZLRichRuleContext): CharSequence?
}

/** An inline widget replacing the tag text, or placed before / after it ([ZLRichWidget.position]). */
public fun interface ZLTagWidgetBuilder {
    public fun widgetFor(info: ZLTagInfo, style: ZLTextStyle): ZLRichWidget?
}

/** Tag tap / long press. */
public fun interface ZLTagListener {
    public fun onTag(info: ZLTagInfo)
}

/** All tags after parsing. */
public fun interface ZLTagParsedListener {
    public fun onParsed(tags: List<ZLTagInfo>)
}

/**
 * Tag highlighting view (the counterpart of the iOS `ZLTagRichLabel`): `<h>`, `<h1>`, `<h2>` …
 * by default; any tag name with attributes and nesting with [tagConfiguration]; other delimiters
 * than `<` `>` with [tagOpenDelimiter] / [tagCloseDelimiter]. Styles, press
 * highlights and taps are configured per tag name; external rules work together with the tag
 * rule (overlaps are resolved by priority).
 *
 * ```kotlin
 * tagView.tagStyle = ZLTextStyle().color(BLUE)
 * tagView.tagStyles = mapOf("h2" to ZLTextStyle().color(RED))
 * tagView.setTagClickListener("h1") { openAgreement() }
 * tagView.onTagClickListener = ZLTagListener { info -> log(info) }
 * tagView.text = "I agree to the <h1>User Agreement</h1> and <h2>Privacy Policy</h2>."
 * ```
 *
 * Styles merge in this order (later wins): text appearance → `style` → [tagStyle] →
 * [tagStyles] (by name) → [tagStyleBuilder]. Nested tags start from the outer tag's style.
 */
public open class ZLTagTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle,
) : ZLRichTextView(context, attrs, defStyleAttr) {

    /** Style of all tags. */
    public var tagStyle: ZLTextStyle = ZLTextStyle.NONE
        set(value) {
            field = value
            setNeedsTagRebuild()
        }

    /** Style per lower-case tag name. */
    public var tagStyles: Map<String, ZLTextStyle> = emptyMap()
        set(value) {
            field = value.mapKeys { it.key.lowercase() }
            setNeedsTagRebuild()
        }

    /** Style of a single tag, merged last. */
    public var tagStyleBuilder: ZLTagStyleBuilder? = null
        set(value) {
            field = value
            setNeedsTagRebuild()
        }

    /** Fully custom content. Priority: tagSpanBuilder > tagWidgetBuilder > default text. */
    public var tagSpanBuilder: ZLTagSpanBuilder? = null
        set(value) {
            field = value
            setNeedsTagRebuild()
        }

    /** Inline widgets for tags. */
    public var tagWidgetBuilder: ZLTagWidgetBuilder? = null
        set(value) {
            field = value
            setNeedsTagRebuild()
        }

    /** Press highlight of tags; the same as [highlightStyle]. */
    public var tagHighlightStyle: ZLTextStyle
        get() = highlightStyle
        set(value) {
            highlightStyle = value
        }

    /** Press highlight per tag name; [ZLTextStyle.NONE] disables the highlight of a name. */
    public var tagHighlightStyles: Map<String, ZLTextStyle> = emptyMap()
        set(value) {
            field = value.mapKeys { it.key.lowercase() }
            setNeedsTagRebuild()
        }

    private val tagClickListeners = HashMap<String, ZLTagListener>()

    /** Tap handler of one tag name; tags without a handler call [onTagClickListener]. */
    public fun setTagClickListener(name: String, listener: ZLTagListener?) {
        if (listener == null) tagClickListeners.remove(name.lowercase()) else tagClickListeners[name.lowercase()] = listener
        setNeedsTagRebuild()
    }

    /** Replaces all tap handlers per tag name. */
    public fun setTagClickListeners(listeners: Map<String, ZLTagListener>) {
        tagClickListeners.clear()
        listeners.forEach { (name, listener) -> tagClickListeners[name.lowercase()] = listener }
        setNeedsTagRebuild()
    }

    /** Fallback tap of tags without their own handler; tags handled by neither are not tappable. */
    public var onTagClickListener: ZLTagListener? = null
        set(value) {
            field = value
            setNeedsTagRebuild()
        }

    /** Long press on a tag (other positions call `onLongPressListener`). */
    public var onTagLongClickListener: ZLTagListener? = null
        set(value) {
            field = value
            setNeedsTagRebuild()
        }

    /** All tags after parsing (posted after the layout pass, only when they change). */
    public var onTagsParsedListener: ZLTagParsedListener? = null

    /** Priority of the tag rule against other rules. */
    public var tagPriority: Int = 0
        set(value) {
            if (field == value) return
            field = value
            resetTagRule()
        }

    /** `false` turns tag parsing off (only rules are used). */
    public var parsesTags: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            setNeedsReparse()
        }

    /** Recognised tag names, nesting, self-closing tags, delimiters. Default `<h>` / `<hN>` only. */
    public var tagConfiguration: ZLTagParserConfiguration = ZLTagParserConfiguration.H_TAGS
        set(value) {
            if (field == value) return
            field = value
            pattern = null
            resetTagRule()
        }

    /**
     * Opening delimiter of tags, default `<` (several characters allowed, e.g. `{{`); the other
     * parsing rules stay the same. With `[` and `]` the view reads `[h1]text[/h1]` and
     * `[h1]text[h1/]`, and `<h1>` is shown as typed. Shortcut of `tagConfiguration.openDelimiter`.
     */
    public var tagOpenDelimiter: String
        get() = tagConfiguration.openDelimiter
        set(value) {
            tagConfiguration = tagConfiguration.copy(openDelimiter = value)
        }

    /** Closing delimiter of tags, default `>`: `]`, `】`, `}`, `}}` … */
    public var tagCloseDelimiter: String
        get() = tagConfiguration.closeDelimiter
        set(value) {
            tagConfiguration = tagConfiguration.copy(closeDelimiter = value)
        }

    /**
     * Delimiters used when XML does not set `zl_tagOpenDelimiter` / `zl_tagCloseDelimiter`.
     * Subclasses override them to change the default, see [ZLBracketTextView]. Read during
     * construction: return constants.
     */
    protected open val defaultTagOpenDelimiter: String get() = "<"
    protected open val defaultTagCloseDelimiter: String get() = ">"

    /** Tags of the current text (outer tags first). */
    public val tags: List<ZLTagInfo>
        get() {
            matches // parses when needed
            return parsedTags
        }

    private var pattern: ZLTagMarkupPattern? = null
    private var rule: ZLRichRule? = null
    private var parsedTags: List<ZLTagInfo> = emptyList()
    private var rootByStart: Map<Int, ZLTagElement> = emptyMap()
    private var infoByElement: Map<ZLTagElement, ZLTagInfo> = emptyMap()
    private var rootIndexByStart: Map<Int, Int> = emptyMap()
    private var reportedTags: List<ZLTagInfo>? = null

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.ZLTagTextView, defStyleAttr, 0)
        try {
            var style = ZLTextStyle.NONE
            if (a.hasValue(R.styleable.ZLTagTextView_zl_tagColor)) style = style.color(a.getColor(R.styleable.ZLTagTextView_zl_tagColor, 0))
            if (a.hasValue(R.styleable.ZLTagTextView_zl_tagBackgroundColor)) {
                style = style.backgroundColor(a.getColor(R.styleable.ZLTagTextView_zl_tagBackgroundColor, 0))
            }
            if (a.getBoolean(R.styleable.ZLTagTextView_zl_tagUnderline, false)) style = style.underline()
            if (a.getBoolean(R.styleable.ZLTagTextView_zl_tagBold, false)) style = style.bold()
            if (a.hasValue(R.styleable.ZLTagTextView_zl_tagTextSize)) {
                style = style.copy(textSize = ZLDimension(a.getDimension(R.styleable.ZLTagTextView_zl_tagTextSize, 0f), TypedValue.COMPLEX_UNIT_PX))
            }
            tagStyle = style
            var configuration = if (a.getBoolean(R.styleable.ZLTagTextView_zl_anyTags, false)) {
                ZLTagParserConfiguration.ANY_TAGS
            } else {
                ZLTagParserConfiguration.H_TAGS
            }
            a.getString(R.styleable.ZLTagTextView_zl_tagNamePattern)?.let { configuration = configuration.copy(namePattern = it) }
            if (a.hasValue(R.styleable.ZLTagTextView_zl_allowsNestedTags)) {
                configuration = configuration.copy(allowsNesting = a.getBoolean(R.styleable.ZLTagTextView_zl_allowsNestedTags, false))
            }
            if (a.hasValue(R.styleable.ZLTagTextView_zl_allowsSelfClosingTags)) {
                configuration = configuration.copy(allowsSelfClosing = a.getBoolean(R.styleable.ZLTagTextView_zl_allowsSelfClosingTags, false))
            }
            configuration = configuration.copy(
                openDelimiter = a.getString(R.styleable.ZLTagTextView_zl_tagOpenDelimiter) ?: defaultTagOpenDelimiter,
                closeDelimiter = a.getString(R.styleable.ZLTagTextView_zl_tagCloseDelimiter) ?: defaultTagCloseDelimiter,
            )
            tagConfiguration = configuration
            tagPriority = a.getInt(R.styleable.ZLTagTextView_zl_tagPriority, 0)
            parsesTags = a.getBoolean(R.styleable.ZLTagTextView_zl_parsesTags, true)
        } finally {
            a.recycle()
        }
    }

    // MARK: Rule

    /** Name of the built-in tag rule. */
    public companion object {
        public const val TAG_RULE_NAME: String = "h-tag"
    }

    private val tagRule: ZLRichRule
        get() = rule ?: ZLRichRule(
            name = TAG_RULE_NAME,
            pattern = pattern ?: ZLTagMarkupPattern(tagConfiguration).also { pattern = it },
            priority = tagPriority,
            handler = ::renderTag,
        ).also { rule = it }

    override val builtInRules: List<ZLRichRule> get() = if (parsesTags) listOf(tagRule) else emptyList()

    private fun resetTagRule() {
        rule = null
        setNeedsReparse()
    }

    /** Tag render configuration changed: only tags render again, external rule matches are reused. */
    private fun setNeedsTagRebuild() = setNeedsRebuild(TAG_RULE_NAME)

    override fun didParse() {
        super.didParse()
        val infos = ArrayList<ZLTagInfo>()
        val byElement = HashMap<ZLTagElement, ZLTagInfo>()
        val roots = HashMap<Int, ZLTagElement>()
        val rootIndexes = HashMap<Int, Int>()
        val currentRule = rule
        val currentPattern = pattern
        val tagMatches = if (currentRule == null) emptyList() else matches.filter { it.rule === currentRule }
        if (currentPattern != null && tagMatches.isNotEmpty()) {
            val source = tagMatches.first().source
            val documentRoots = currentPattern.document(source).roots.associateBy { it.range.start }
            for (match in tagMatches) {
                val root = documentRoots[match.range.start] ?: continue
                roots[match.range.start] = root
                rootIndexes[match.range.start] = infos.size
                for (element in root.allElements) {
                    val info = ZLTagParser.makeInfo(element, source, infos.size, element.parent?.let { byElement[it] })
                    byElement[element] = info
                    infos += info
                }
            }
        }
        // Tag indexes changed (tags added / removed before): cached results hold outdated infos.
        if (rootIndexByStart.any { (start, index) -> rootIndexes[start]?.let { it != index } == true }) {
            invalidateRenderCache(TAG_RULE_NAME)
        }
        parsedTags = infos
        infoByElement = byElement
        rootByStart = roots
        rootIndexByStart = rootIndexes
        if (onTagsParsedListener != null) post(::reportTags)
    }

    /** Reports the tags when they changed (a parent updating its UI in the callback must not loop). */
    private fun reportTags() {
        val tags = parsedTags
        val reported = reportedTags
        if (reported != null && reported.size == tags.size && reported.indices.all { sameTag(reported[it], tags[it]) }) return
        reportedTags = tags
        onTagsParsedListener?.onParsed(tags)
    }

    private fun sameTag(a: ZLTagInfo, b: ZLTagInfo) =
        a.index == b.index && a.name == b.name && a.text == b.text && a.raw == b.raw && a.depth == b.depth && a.attributes == b.attributes

    private fun renderTag(ctx: ZLRichRuleContext) {
        val root = rootByStart[ctx.match.range.start] ?: return
        val source = ctx.match.source
        val output = SpannableStringBuilder()
        val links = ArrayList<Pair<ZLRange, ZLTagInfo>>()
        append(root, ctx.baseStyle, source, output, links, ctx)
        ctx.setText(output)
        // Outer tags first, inner tags last: inner tags win where they overlap.
        for ((range, info) in links) {
            ctx.addLink(
                range.start, range.end,
                highlightStyle = tagHighlightStyles[info.name],
                onTap = if (isTappable(info)) ZLRichMatchListener { dispatchTap(info) } else null,
                onLongPress = onTagLongClickListener?.let { ZLRichMatchListener { _: ZLRichMatch -> onTagLongClickListener?.onTag(info) } },
            )
        }
    }

    private fun append(
        element: ZLTagElement,
        inherited: ZLTextStyle,
        source: String,
        output: SpannableStringBuilder,
        links: MutableList<Pair<ZLRange, ZLTagInfo>>,
        ctx: ZLRichRuleContext,
    ) {
        val info = infoByElement[element]
        if (info == null) {
            output.append(source, element.range.start, element.range.end)
            return
        }
        // The tag's own style; nested tags continue from the resolved style of their parent.
        val own = tagStyle + tagStyles[info.name] + tagStyleBuilder?.styleFor(info)
        val style = inherited + own
        val start = output.length
        val slot = links.size
        links += ZLRange(start, start) to info

        val custom = tagSpanBuilder?.spanFor(info, style, ctx)
        if (custom != null) {
            output.append(custom)
        } else {
            val widget = tagWidgetBuilder?.widgetFor(info, style)
            val span = widget?.let { ctx.widgetText(it) }
            if (span != null && widget.position == ZLWidgetPosition.REPLACE) {
                output.append(span)
            } else {
                if (span != null && widget.position == ZLWidgetPosition.BEFORE) output.append(span)
                for (child in element.children) {
                    when (child) {
                        is ZLTagNode.Text -> output.append(source, child.range.start, child.range.end)
                        is ZLTagNode.Element -> append(child.element, style, source, output, links, ctx)
                    }
                }
                if (span != null && widget.position == ZLWidgetPosition.AFTER) output.append(span)
            }
        }
        // Applied after the inner tags but with a higher span priority (= lower precedence) the
        // shallower the tag is, so inner tags override the attributes they set themselves.
        ZLStyleSpans.apply(output, own, start, output.length, ctx.context, priority = (100 - info.depth).coerceAtLeast(1))
        links[slot] = ZLRange(start, output.length) to info
    }

    private fun isTappable(info: ZLTagInfo) = tagClickListeners.containsKey(info.name) || onTagClickListener != null

    private fun dispatchTap(info: ZLTagInfo) {
        val listener = tagClickListeners[info.name]
        if (listener != null) listener.onTag(info) else onTagClickListener?.onTag(info)
    }
}
