package com.zl.richtext

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.RectF
import android.text.Layout
import android.text.Selection
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.URLSpan
import android.util.AttributeSet
import android.view.ActionMode
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.graphics.withTranslation
import androidx.core.view.ViewCompat
import com.zl.richtext.detect.ZLDataDetector
import com.zl.richtext.detect.ZLDataDetectorType
import com.zl.richtext.internal.ZLDecorationPainter
import com.zl.richtext.internal.ZLDisplayInputs
import com.zl.richtext.internal.ZLDisplayText
import com.zl.richtext.internal.ZLDisplayTextBuilder
import com.zl.richtext.internal.ZLLinkMarkerSpan
import com.zl.richtext.internal.ZLStyleSpans
import com.zl.richtext.internal.ZLTextGeometry
import com.zl.richtext.internal.ZLTruncation
import com.zl.richtext.rule.ZLRange
import com.zl.richtext.style.ZLTextStyle
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Called after the selected text was copied through the menu. */
public fun interface ZLCopyListener {
    public fun onCopy(text: String, isRawText: Boolean)
}

/** Selection changes: content offsets, `null` when nothing is selected. */
public fun interface ZLSelectionListener {
    public fun onSelectionChanged(range: ZLRange?)
}

/**
 * Rich text TextView with links (the counterpart of the iOS `ZLAttributedLabel`).
 *
 * * Text: `setText` with any `CharSequence` (spans are kept); [ZLTextStyle] runs via
 *   [com.zl.richtext.style.ZLTextBuilder].
 * * Links: [addLink], runs with [ZLTextStyle.link] / `URLSpan`, [dataDetectorTypes]; default /
 *   pressed / inactive styles; tap and long press listeners; [extendsLinkTouchArea].
 * * Truncation: `maxLines` with a rich [truncationToken] (may contain inline widgets) and
 *   head / middle / tail [truncationMode].
 * * [adjustsFontSizeToFit] / [minimumScaleFactor], [isTextHighlighted].
 * * Every link is a separate accessibility node.
 *
 * All common properties have XML attributes (`zl_*`, see `attrs.xml`); standard TextView
 * attributes keep working. Several property changes in a row are rendered once, at the next
 * layout pass.
 *
 * Note: `getText()` returns what is displayed (truncated, with tokens); the text that was set
 * is [contentText].
 */
public open class ZLAttributedTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle,
) : AppCompatTextView(context, attrs, defStyleAttr) {

    // TextView calls overridden methods from its constructor, before the fields below exist.
    private var ready = false
    private var internalTextChange = false

    // MARK: Content

    private var content: CharSequence = ""
    private var contentLinks: List<ZLLink> = emptyList()
    private val userLinks = ArrayList<ZLLink>()
    private var collapseLink: ZLLink? = null
    private var version = 0

    /** The text set in XML (available to subclasses in their `init`). */
    protected var initialText: CharSequence = ""
        private set

    /** The text that was set (without truncation and tokens). */
    public val contentText: CharSequence
        get() {
            prepareContent()
            return content
        }

    /** All links: content links (detected, `ZLTextStyle.link`, rule matches) and added links. */
    public val links: List<ZLLink>
        get() {
            prepareContent()
            return contentLinks + userLinks
        }

    // MARK: Link configuration

    /** Style of links without their own style (blue, underlined by default). */
    public var linkStyle: ZLTextStyle = DEFAULT_LINK_STYLE
        set(value) {
            field = value
            invalidateDisplay()
        }

    /** Style of a pressed link without its own `activeStyle`; [ZLTextStyle.NONE] keeps the style. */
    public var activeLinkStyle: ZLTextStyle = DEFAULT_ACTIVE_LINK_STYLE
        set(value) {
            field = value
            invalidateDisplay()
        }

    /** Style of links while the window has no focus (a dialog is shown); `null` keeps the style. */
    public var inactiveLinkStyle: ZLTextStyle? = DEFAULT_INACTIVE_LINK_STYLE
        set(value) {
            field = value
            invalidateDisplay()
        }

    /** Content recognised as links (none by default). */
    public var dataDetectorTypes: Set<ZLDataDetectorType> = emptySet()
        set(value) {
            field = value.toSet()
            if (ready) assignContent(content, detect = true)
        }

    /** When the finger is not on the text, links within 15dp are found as well. */
    public var extendsLinkTouchArea: Boolean = false

    /** Tap on a link without its own `onClick`. */
    public var onLinkClickListener: ZLLinkListener? = null

    /** Long press on a link without its own `onLongClick`. */
    public var onLinkLongClickListener: ZLLinkListener? = null
        set(value) {
            field = value
            refreshLongClickable()
        }

    // MARK: Truncation & font fitting

    private var lineLimit: Int = Int.MAX_VALUE

    /** Truncation of a single line; multiple lines are truncated at the tail. */
    public var truncationMode: ZLTruncationMode = ZLTruncationMode.TAIL
        set(value) {
            field = value
            invalidateDisplay()
        }

    /** Shown where the text is truncated, `…` by default. May contain inline widgets. */
    public var truncationToken: CharSequence? = null
        set(value) {
            field = value
            invalidateDisplay()
        }

    /** Shrinks all fonts by the same factor (down to [minimumScaleFactor]) when the text does not fit. */
    public var adjustsFontSizeToFit: Boolean = false
        set(value) {
            field = value
            invalidateDisplay()
        }

    /** Smallest font scale (0 – 1); 0 disables the shrinking. */
    public var minimumScaleFactor: Float = 0f
        set(value) {
            field = value
            invalidateDisplay()
        }

    /** Draws all text in [highlightedTextColor] (e.g. selected cells). */
    public var isTextHighlighted: Boolean = false
        set(value) {
            field = value
            invalidateDisplay()
        }

    public var highlightedTextColor: Int? = null
        set(value) {
            field = value
            invalidateDisplay()
        }

    // MARK: Selection

    /** Called after copying through the selection menu. */
    public var onCopyListener: ZLCopyListener? = null

    /** Selection changes (content offsets). */
    public var onSelectionChangeListener: ZLSelectionListener? = null

    /** Title of the "copy raw text" menu item. */
    public var copyRawMenuTitle: CharSequence = context.getString(R.string.zl_copy_raw)

    /** Render / layout counters. */
    public val statistics: ZLStatistics = ZLStatistics()

    // MARK: Expand / collapse (exposed by ZLRichTextView)

    protected var expandTokenText: CharSequence? = null
        set(value) {
            field = value
            invalidateDisplay()
        }

    protected var collapseTokenText: CharSequence? = null
        set(value) {
            field = value
            invalidateDisplay()
        }

    protected var collapseLineThreshold: Int = 0
        set(value) {
            field = value
            invalidateDisplay()
        }

    protected var expandedState: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            invalidateDisplay()
        }

    /** The expand (`true`) or collapse (`false`) token was tapped. */
    protected open fun onExpandTokenClick(wantsExpand: Boolean) {}

    // MARK: Display state

    private var display: ZLDisplayText? = null
    private var displayKey: List<Any?>? = null
    private var contentLength = 0

    /** Whether the text was truncated in the last layout. */
    public var isTruncated: Boolean = false
        private set

    /** Font scale chosen by [adjustsFontSizeToFit], 1 when not scaled. */
    public var currentFontScale: Float = 1f
        private set

    /**
     * Number of lines of the content at the current width. With a line limit, lines are only
     * counted up to `max(maxLines, collapseThreshold) + 1`.
     */
    public var fullLineCount: Int = 0
        private set

    private val painter = ZLDecorationPainter()
    private val textArea = RectF()
    private var windowHadFocus = false

    // MARK: Touch state

    private var pressedLink: ZLLink? = null
    private var pressedSpans: List<Pair<Spannable, Any>> = emptyList()
    private var trackingToken = false
    private var longPressFired = false
    private var downX = 0f
    private var downY = 0f
    private var pendingTap: Runnable? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var doubleTapDetector: GestureDetector? = null

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.ZLAttributedTextView, defStyleAttr, 0)
        try {
            val linkColor = a.getColor(R.styleable.ZLAttributedTextView_zl_linkColor, DEFAULT_LINK_COLOR)
            val underline = a.getBoolean(R.styleable.ZLAttributedTextView_zl_linkUnderline, true)
            linkStyle = ZLTextStyle(underline = if (underline) true else null).color(linkColor)
            if (a.hasValue(R.styleable.ZLAttributedTextView_zl_activeLinkColor) ||
                a.hasValue(R.styleable.ZLAttributedTextView_zl_activeLinkBackgroundColor)
            ) {
                var active = ZLTextStyle.NONE
                if (a.hasValue(R.styleable.ZLAttributedTextView_zl_activeLinkColor)) {
                    active = active.color(a.getColor(R.styleable.ZLAttributedTextView_zl_activeLinkColor, 0))
                }
                if (a.hasValue(R.styleable.ZLAttributedTextView_zl_activeLinkBackgroundColor)) {
                    active = active.backgroundColor(a.getColor(R.styleable.ZLAttributedTextView_zl_activeLinkBackgroundColor, 0))
                }
                activeLinkStyle = active
            }
            if (a.hasValue(R.styleable.ZLAttributedTextView_zl_inactiveLinkColor)) {
                inactiveLinkStyle = ZLTextStyle().color(a.getColor(R.styleable.ZLAttributedTextView_zl_inactiveLinkColor, 0))
            }
            dataDetectorTypes = ZLDataDetectorType.fromFlags(a.getInt(R.styleable.ZLAttributedTextView_zl_dataDetectorTypes, 0))
            extendsLinkTouchArea = a.getBoolean(R.styleable.ZLAttributedTextView_zl_extendsLinkTouchArea, false)
            if (a.hasValue(R.styleable.ZLAttributedTextView_zl_truncationMode)) {
                truncationMode = ZLTruncationMode.entries[a.getInt(R.styleable.ZLAttributedTextView_zl_truncationMode, 0)]
            }
            a.getText(R.styleable.ZLAttributedTextView_zl_truncationToken)?.let { token ->
                truncationToken = if (a.hasValue(R.styleable.ZLAttributedTextView_zl_truncationTokenColor)) {
                    colored(token, a.getColor(R.styleable.ZLAttributedTextView_zl_truncationTokenColor, 0))
                } else {
                    token
                }
            }
            adjustsFontSizeToFit = a.getBoolean(R.styleable.ZLAttributedTextView_zl_adjustsFontSizeToFit, false)
            minimumScaleFactor = a.getFloat(R.styleable.ZLAttributedTextView_zl_minimumScaleFactor, 0f)
            if (a.hasValue(R.styleable.ZLAttributedTextView_zl_highlightedTextColor)) {
                highlightedTextColor = a.getColor(R.styleable.ZLAttributedTextView_zl_highlightedTextColor, 0)
            }
            isTextHighlighted = a.getBoolean(R.styleable.ZLAttributedTextView_zl_highlighted, false)
        } finally {
            a.recycle()
        }

        // Adopt what the TextView constructor applied from XML, then take over.
        lineLimit = super.getMaxLines().takeIf { it > 0 } ?: Int.MAX_VALUE
        when (super.getEllipsize()) {
            TextUtils.TruncateAt.START -> truncationMode = ZLTruncationMode.HEAD
            TextUtils.TruncateAt.MIDDLE -> truncationMode = ZLTruncationMode.MIDDLE
            else -> Unit
        }
        super.setMaxLines(Int.MAX_VALUE)
        super.setEllipsize(null)
        initialText = super.getText()?.toString() ?: ""
        customSelectionActionModeCallback = SelectionMenu()
        ViewCompat.enableAccessibleClickableSpanSupport(this)
        ready = true
        // Not onTextAssigned(): subclass fields do not exist yet. Subclasses read initialText.
        assignContent(initialText, detect = true)
    }

    // MARK: Text

    override fun setText(text: CharSequence?, type: BufferType?) {
        // TextView sets its current text again to change the buffer type
        // (setTextIsSelectable …): that is the display text, not new content.
        if (!ready || internalTextChange || (text is Spanned && text === getText())) {
            super.setText(text, type)
            return
        }
        onTextAssigned(text ?: "")
    }

    /**
     * A text was set from outside (code or XML). The base class shows it as is and detects
     * links; subclasses parse it.
     */
    protected open fun onTextAssigned(text: CharSequence) {
        userLinks.clear()
        assignContent(text, detect = true)
    }

    private fun assignContent(text: CharSequence, detect: Boolean) {
        content = text
        contentLinks = if (detect) detectLinks(text) else emptyList()
        invalidateDisplay()
    }

    /**
     * Sets content and links produced by a subclass (offsets of [links] refer to [content]). Does
     * not request a layout; call it from [onPrepareContent] or call [invalidateDisplay].
     */
    protected fun setComposedContent(content: CharSequence, links: List<ZLLink>) {
        this.content = content
        contentLinks = links
        markDisplayDirty()
    }

    /** Called before the content is used; subclasses compose lazily here. */
    protected open fun onPrepareContent() {}

    private fun prepareContent() {
        if (ready) onPrepareContent()
    }

    /** Base style under the whole text (ZLRichTextView: `style`). */
    protected open val baseTextStyle: ZLTextStyle get() = ZLTextStyle.NONE

    private fun detectLinks(text: CharSequence): List<ZLLink> {
        val result = ArrayList<ZLLink>()
        if (text is Spanned) {
            for (span in text.getSpans(0, text.length, ZLLinkMarkerSpan::class.java)) {
                result += ZLLink(text.getSpanStart(span), text.getSpanEnd(span), url = span.url)
            }
            for (span in text.getSpans(0, text.length, URLSpan::class.java)) {
                result += ZLLink(text.getSpanStart(span), text.getSpanEnd(span), url = span.url)
            }
        }
        if (dataDetectorTypes.isNotEmpty()) {
            for (match in ZLDataDetector(dataDetectorTypes).detect(text.toString())) {
                if (result.none { it.start < match.range.end && match.range.start < it.end }) {
                    result += ZLLink(match.range.start, match.range.end, url = match.url, result = match)
                }
            }
        }
        result.sortBy { it.start }
        return result
    }

    // MARK: Links

    /** Adds a link (links added later win where they overlap). */
    public fun addLink(link: ZLLink): ZLLink {
        userLinks += link
        refreshLongClickable()
        invalidateDisplay()
        return link
    }

    /** Adds a link with a tap callback, styled with [linkStyle] unless [style] is given. */
    @JvmOverloads
    public fun addLink(start: Int, end: Int, style: ZLTextStyle? = null, onClick: ZLLinkListener): ZLLink =
        addLink(ZLLink(start, end, style = style, onClick = onClick))

    /** Adds a URL link; taps go to [onLinkClickListener]. */
    public fun addLink(url: String, start: Int, end: Int): ZLLink = addLink(ZLLink(start, end, url = url))

    /** Removes the links added with [addLink] (detected / content links stay). */
    public fun removeAllLinks() {
        userLinks.clear()
        invalidateDisplay()
    }

    /** Triggers the tap of [link] like a user tap. */
    public fun performLinkClick(link: ZLLink) {
        val listener = link.onClick ?: onLinkClickListener ?: return
        listener.onLink(this, link)
    }

    private fun isTappable(link: ZLLink) = link.onClick != null || onLinkClickListener != null

    private fun effectiveLinks(): List<ZLLink> {
        val links = contentLinks + userLinks
        val collapse = collapseLink ?: return links
        return links + collapse
    }

    // MARK: Invalidation

    /** Marks the display as outdated and requests a new layout. */
    protected fun invalidateDisplay() {
        if (!ready) return
        markDisplayDirty()
        requestLayout()
        invalidate()
    }

    private fun markDisplayDirty() {
        version++
        displayKey = null
    }

    override fun setMaxLines(maxLines: Int) {
        if (!ready) {
            super.setMaxLines(maxLines)
            return
        }
        lineLimit = if (maxLines > 0) maxLines else Int.MAX_VALUE
        invalidateDisplay()
    }

    override fun getMaxLines(): Int = if (ready) lineLimit else super.getMaxLines()

    override fun setEllipsize(where: TextUtils.TruncateAt?) {
        if (!ready) {
            super.setEllipsize(where)
            return
        }
        truncationMode = when (where) {
            TextUtils.TruncateAt.START -> ZLTruncationMode.HEAD
            TextUtils.TruncateAt.MIDDLE -> ZLTruncationMode.MIDDLE
            else -> ZLTruncationMode.TAIL
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        invalidateDisplay()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) windowHadFocus = true
        if (inactiveLinkStyle != null && effectiveLinks().isNotEmpty()) invalidateDisplay()
    }

    private val isInactive: Boolean get() = windowHadFocus && !hasWindowFocus()

    // MARK: Layout

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (ready) {
            prepareContent()
            val widthMode = MeasureSpec.getMode(widthMeasureSpec)
            val width = if (widthMode == MeasureSpec.UNSPECIFIED) {
                UNBOUNDED_WIDTH
            } else {
                MeasureSpec.getSize(widthMeasureSpec) - compoundPaddingLeft - compoundPaddingRight
            }
            val heightLimit = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
                MeasureSpec.getSize(heightMeasureSpec) - compoundPaddingTop - compoundPaddingBottom
            } else {
                -1
            }
            updateDisplay(max(width, 0), heightLimit)
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun layoutSignature(): List<Any?> = listOf(
        paint.textSize, paint.typeface, paint.letterSpacing, paint.textScaleX, lineSpacingExtra,
        lineSpacingMultiplier, includeFontPadding, breakStrategy, hyphenationFrequency,
    )

    private fun updateDisplay(width: Int, heightLimit: Int) {
        val key = listOf(width, heightLimit, version, layoutSignature())
        if (key == displayKey && display != null) {
            statistics.layoutCacheHits++
            return
        }
        displayKey = key
        val inputs = makeInputs()
        val text = inputs.content
        fun layoutOf(display: ZLDisplayText) = ZLTextGeometry.layout(this, display.text, width).also { statistics.textLayouts++ }

        var limit: Int? = if (expandedState || lineLimit == Int.MAX_VALUE) null else lineLimit

        // Font fitting: the largest scale in [minimumScaleFactor, 1] that fits.
        var scale = 1f
        if (adjustsFontSizeToFit && minimumScaleFactor > 0f && minimumScaleFactor < 1f && text.isNotEmpty()) {
            val lineLimitForFit = limit
            fun fits(s: Float): Boolean {
                val candidate = ZLDisplayTextBuilder.build(inputs, ZLTruncation.None, s)
                if (lineLimitForFit == 1) {
                    statistics.textLayouts++
                    return Layout.getDesiredWidth(candidate.text, paint) <= width + 0.5f
                }
                val layout = layoutOf(candidate)
                return (lineLimitForFit == null || layout.lineCount <= lineLimitForFit) &&
                    (heightLimit < 0 || layout.height <= heightLimit)
            }
            if (!fits(1f)) {
                if (fits(minimumScaleFactor)) {
                    var low = minimumScaleFactor
                    var high = 1f
                    repeat(8) {
                        val mid = (low + high) / 2
                        if (fits(mid)) low = mid else high = mid
                    }
                    scale = low
                } else {
                    scale = minimumScaleFactor
                }
            }
        }

        val full = ZLDisplayTextBuilder.build(inputs, ZLTruncation.None, scale)
        // With a line limit only the first lines matter: stop line breaking early.
        val lineCap = limit?.let { max(it, collapseLineThreshold) + 1 } ?: Int.MAX_VALUE
        val fullLayout = ZLTextGeometry.layout(this, full.text, width, if (heightLimit >= 0) Int.MAX_VALUE else lineCap)
            .also { statistics.textLayouts++ }
        fullLineCount = fullLayout.lineCount
        if (limit != null && collapseLineThreshold > limit && fullLineCount <= collapseLineThreshold) limit = null
        // A fixed height limits the number of lines as well.
        if (heightLimit >= 0 && fullLayout.height > heightLimit) {
            var fitting = 0
            for (line in 0 until fullLayout.lineCount) if (fullLayout.getLineBottom(line) <= heightLimit) fitting++
            fitting = max(fitting, 1)
            limit = min(limit ?: fitting, fitting)
        }

        var result = full
        var truncated = false
        if (limit != null && fullLineCount > limit) {
            truncated = true
            val lines: Int = limit
            fun fits(truncation: ZLTruncation): ZLDisplayText? {
                val candidate = ZLDisplayTextBuilder.build(inputs, truncation, scale)
                return if (layoutOf(candidate).lineCount <= lines) candidate else null
            }
            result = when {
                lines == 1 && truncationMode == ZLTruncationMode.HEAD && expandTokenText == null -> {
                    var low = 0
                    var high = text.length
                    while (low < high) {
                        val mid = (low + high) ushr 1
                        if (fits(ZLTruncation.Head(snap(text, mid, forward = true))) != null) high = mid else low = mid + 1
                    }
                    fits(ZLTruncation.Head(snap(text, low, forward = true))) ?: full
                }
                lines == 1 && truncationMode == ZLTruncationMode.MIDDLE && expandTokenText == null -> {
                    fun middle(keep: Int) = ZLTruncation.Middle(
                        snap(text, (keep + 1) / 2),
                        snap(text, text.length - keep / 2, forward = true),
                    )
                    var low = 0
                    var high = text.length
                    while (low < high) {
                        val mid = (low + high + 1) ushr 1
                        if (fits(middle(mid)) != null) low = mid else high = mid - 1
                    }
                    fits(middle(low)) ?: full
                }
                else -> {
                    // Tail: the longest prefix that fits with the token; it never goes beyond the
                    // end of the last allowed line.
                    var high = full.map.contentCaret(fullLayout.getLineEnd(lines - 1))
                    var low = 0
                    var best: ZLDisplayText? = null
                    while (low <= high) {
                        val mid = (low + high) ushr 1
                        val candidate = fits(ZLTruncation.Tail(trimmedCut(text, mid)))
                        if (candidate != null) {
                            best = candidate
                            low = mid + 1
                        } else {
                            high = mid - 1
                        }
                    }
                    best ?: ZLDisplayTextBuilder.build(inputs, ZLTruncation.Tail(0), scale)
                }
            }
        }
        isTruncated = truncated
        currentFontScale = scale
        statistics.renders++
        applyDisplay(result)
    }

    private fun makeInputs(): ZLDisplayInputs {
        val showsExpand = !expandedState && expandTokenText != null
        var text: CharSequence = content
        contentLength = content.length
        collapseLink = null
        val collapse = collapseTokenText
        if (expandedState && !collapse.isNullOrEmpty()) {
            text = SpannableStringBuilder(content).append(collapse)
            collapseLink = ZLLink(
                content.length, content.length + collapse.length,
                style = ZLTextStyle.NONE,
                activeStyle = ZLTextStyle.NONE,
                onClick = { _, _ -> onExpandTokenClick(false) },
            )
        }
        val links = effectiveLinks()
        val inactive = isInactive
        val linkStyles = ArrayList<Pair<IntArray, ZLTextStyle>>()
        for (link in links) {
            val range = intArrayOf(link.start, min(link.end, text.length))
            val style = link.style ?: linkStyle
            if (!style.isEmpty) linkStyles += range to style
            if (inactive) {
                val inactiveStyle = link.inactiveStyle ?: inactiveLinkStyle
                if (inactiveStyle != null && !inactiveStyle.isEmpty) linkStyles += range to inactiveStyle
            }
        }
        return ZLDisplayInputs(
            context = context,
            content = text,
            token = if (showsExpand) expandTokenText else (truncationToken ?: context.getString(R.string.zl_ellipsis)),
            baseStyle = baseTextStyle,
            linkStyles = linkStyles,
            accessibilityLinks = links.filter { isTappable(it) }.map { intArrayOf(it.start, it.end) to Runnable { performLinkClick(it) } },
            tokenAction = if (showsExpand) Runnable { onExpandTokenClick(true) } else null,
            highlightedTextColor = if (isTextHighlighted) highlightedTextColor else null,
        )
    }

    private fun applyDisplay(display: ZLDisplayText) {
        clearPressed()
        this.display = display
        internalTextChange = true
        try {
            super.setText(display.text, BufferType.SPANNABLE)
        } finally {
            internalTextChange = false
        }
        refreshLongClickable()
    }

    /** Moves an offset out of a surrogate pair. */
    private fun snap(text: CharSequence, offset: Int, forward: Boolean = false): Int {
        if (offset <= 0 || offset >= text.length) return offset.coerceIn(0, text.length)
        return if (Character.isLowSurrogate(text[offset]) && Character.isHighSurrogate(text[offset - 1])) {
            if (forward) offset + 1 else offset - 1
        } else {
            offset
        }
    }

    /** A tail cut without trailing white space. */
    private fun trimmedCut(text: CharSequence, offset: Int): Int {
        var cut = snap(text, offset)
        while (cut > 0 && (text[cut - 1].isWhitespace() || text[cut - 1] == '\u3000')) cut--
        return cut
    }

    // MARK: Geometry (view coordinates)

    private fun toLayoutX(x: Float) = x - totalPaddingLeft + scrollX

    private fun toLayoutY(y: Float) = y - totalPaddingTop + scrollY

    private fun displayIndexAt(x: Float, y: Float): Int? {
        val layout = layout ?: return null
        return ZLTextGeometry.characterAt(layout, toLayoutX(x), toLayoutY(y))
    }

    /** Character of the content under `(x, y)`, -1 when the point is not on the text. */
    public fun characterIndexAt(x: Float, y: Float): Int {
        val index = displayIndexAt(x, y)?.let { display?.map?.contentIndex(it) } ?: return -1
        return if (index < contentLength && contentText.getOrNull(index) != '\n') index else -1
    }

    /** Glyph boxes of a content range (one per line), in view coordinates. */
    public fun rectsForRange(start: Int, end: Int): List<RectF> {
        val layout = layout ?: return emptyList()
        val shown = text as? Spanned ?: return emptyList()
        val map = display?.map ?: return emptyList()
        val dx = (totalPaddingLeft - scrollX).toFloat()
        val dy = (totalPaddingTop - scrollY).toFloat()
        return map.displayRanges(start, end).flatMap { r ->
            ZLTextGeometry.lineRects(layout, shown, r[0], r[1], paint, android.text.TextPaint())
        }.onEach { it.offset(dx, dy) }
    }

    /** Bounding box of a content range in view coordinates. */
    public fun boundingRectForRange(start: Int, end: Int): RectF? {
        val rects = rectsForRange(start, end)
        if (rects.isEmpty()) return null
        return RectF(rects[0]).apply { rects.forEach { union(it) } }
    }

    /** Area of the expand token (view coordinates, slightly enlarged), `null` when not shown. */
    protected val expandTokenBounds: RectF?
        get() {
            if (expandedState || expandTokenText == null || !isTruncated) return null
            val layout = layout ?: return null
            val range = display?.map?.tokenRange() ?: return null
            val first = layout.getLineForOffset(range[0])
            val last = layout.getLineForOffset(max(range[0], range[1] - 1))
            var result: RectF? = null
            for (line in first..last) {
                val s = max(range[0], layout.getLineStart(line))
                val e = min(range[1], layout.getLineEnd(line))
                val rect = ZLTextGeometry.horizontalBounds(layout, s, e) ?: continue
                rect.top = layout.getLineTop(line).toFloat()
                rect.bottom = layout.getLineBottom(line).toFloat()
                if (result == null) result = rect else result.union(rect)
            }
            val density = resources.displayMetrics.density
            return result?.apply {
                offset((totalPaddingLeft - scrollX).toFloat(), (totalPaddingTop - scrollY).toFloat())
                inset(-8 * density, -6 * density)
            }
        }

    /** Content index under `(x, y)` covered by a link (searching up to 15dp around when enabled). */
    private fun linkIndexAt(x: Float, y: Float): Int? {
        val links = effectiveLinks()
        if (links.isEmpty()) return null
        fun covered(index: Int) = links.any { index >= it.start && index < it.end }
        val index = displayIndexAt(x, y)
        if (index != null) {
            val map = display?.map ?: return null
            if (map.isToken(index)) return null
            return map.contentIndex(index)?.takeIf(::covered)
        }
        if (!extendsLinkTouchArea) return null
        val density = resources.displayMetrics.density
        for (radius in floatArrayOf(2.5f, 5f, 7.5f, 12.5f, 15f)) {
            val r = radius * density
            val d = sqrt(2 * r * r) / 2
            val deltas = arrayOf(
                floatArrayOf(0f, -r), floatArrayOf(0f, r), floatArrayOf(-r, 0f), floatArrayOf(r, 0f),
                floatArrayOf(-d, -d), floatArrayOf(-d, d), floatArrayOf(d, d), floatArrayOf(d, -d),
            )
            for (delta in deltas) {
                val near = displayIndexAt(x + delta[0], y + delta[1]) ?: continue
                val map = display?.map ?: continue
                if (map.isToken(near)) continue
                val contentIndex = map.contentIndex(near) ?: continue
                if (covered(contentIndex)) return contentIndex
            }
        }
        return null
    }

    /** The link at `(x, y)` (the link added last wins). */
    public fun linkAt(x: Float, y: Float): ZLLink? {
        val index = linkIndexAt(x, y) ?: return null
        return effectiveLinks().lastOrNull { index >= it.start && index < it.end }
    }

    private fun tapLinkAt(x: Float, y: Float): ZLLink? {
        val index = linkIndexAt(x, y) ?: return null
        return effectiveLinks().lastOrNull { index >= it.start && index < it.end && isTappable(it) }
    }

    /** Long press target at `(x, y)`: the last link there with its own long press, else the topmost link with the view's listener. */
    private fun longPressLinkAt(x: Float, y: Float): Pair<ZLLink, ZLLinkListener>? {
        val index = linkIndexAt(x, y) ?: return null
        val links = effectiveLinks()
        links.lastOrNull { index >= it.start && index < it.end && it.onLongClick != null }?.let { return it to it.onLongClick!! }
        val top = links.lastOrNull { index >= it.start && index < it.end } ?: return null
        return onLinkLongClickListener?.let { top to it }
    }

    // MARK: Gestures (subclass hooks)

    /** Whether a long press on plain text is handled ([onLongPressText]). */
    protected open val handlesLongPress: Boolean get() = false

    /** Long press on plain text (not on a link with its own long press); return `true` when handled. */
    protected open fun onLongPressText(x: Float, y: Float): Boolean = false

    /** Whether double taps are handled ([onDoubleTapText]); single taps on links wait for the double tap timeout. */
    protected open val handlesDoubleTap: Boolean get() = false

    protected open fun onDoubleTapText(x: Float, y: Float) {}

    /** Updates long-press detection after the gesture configuration changed. */
    protected fun refreshLongClickable() {
        if (!ready) return
        val needs = isTextSelectable || handlesLongPress || onLinkLongClickListener != null ||
            effectiveLinks().any { it.onLongClick != null }
        if (needs && !isLongClickable) isLongClickable = true
        if (handlesDoubleTap && doubleTapDetector == null) {
            doubleTapDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    pendingTap = null
                    onDoubleTapText(e.x, e.y)
                    return true
                }

                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    pendingTap?.run()
                    pendingTap = null
                    return true
                }
            }).apply { setIsLongpressEnabled(false) }
        } else if (!handlesDoubleTap) {
            doubleTapDetector = null
        }
    }

    // MARK: Touch

    // super.onTouchEvent() always runs and calls performClick(); links are exposed to accessibility
    // services as ClickableSpans.
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!ready) return super.onTouchEvent(event)
        var tracking = false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                longPressFired = false
                trackingToken = false
                if (!hasSelection()) {
                    trackingToken = expandTokenBounds?.contains(event.x, event.y) == true
                    if (!trackingToken) tapLinkAt(event.x, event.y)?.let { pressLink(it) }
                }
                tracking = trackingToken || pressedLink != null
            }
            MotionEvent.ACTION_MOVE -> {
                val moved = kotlin.math.abs(event.x - downX) > touchSlop || kotlin.math.abs(event.y - downY) > touchSlop
                if (moved) {
                    trackingToken = false
                    if (pressedLink != null && tapLinkAt(event.x, event.y) !== pressedLink) clearPressed()
                }
                tracking = trackingToken || pressedLink != null
            }
            MotionEvent.ACTION_UP -> {
                val link = pressedLink
                if (!longPressFired) {
                    if (trackingToken && expandTokenBounds?.contains(event.x, event.y) == true) {
                        tracking = true
                        onExpandTokenClick(true)
                    } else if (link != null) {
                        tracking = true
                        val action = Runnable { performLinkClick(link) }
                        if (doubleTapDetector != null) pendingTap = action else action.run()
                    }
                }
                trackingToken = false
                clearPressed()
            }
            MotionEvent.ACTION_CANCEL -> {
                trackingToken = false
                clearPressed()
            }
        }
        val doubleTap = doubleTapDetector?.onTouchEvent(event) ?: false
        val handled = super.onTouchEvent(event)
        return tracking || doubleTap || handled || doubleTapDetector != null
    }

    override fun performLongClick(): Boolean {
        if (!ready) return super.performLongClick()
        val target = longPressLinkAt(downX, downY)
        if (target != null) {
            longPressFired = true
            clearPressed()
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            target.second.onLink(this, target.first)
            return true
        }
        if (isTextSelectable) {
            longPressFired = true
            clearPressed()
            return super.performLongClick()
        }
        if (handlesLongPress) {
            longPressFired = true
            clearPressed()
            if (onLongPressText(downX, downY)) {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                return true
            }
        }
        return super.performLongClick()
    }

    private fun pressLink(link: ZLLink) {
        clearPressed()
        pressedLink = link
        val style = link.activeStyle ?: activeLinkStyle
        val shown = text as? Spannable ?: return
        val map = display?.map ?: return
        if (style.isEmpty) return
        val spans = ArrayList<Pair<Spannable, Any>>()
        for (r in map.displayRanges(link.start, link.end)) {
            for (span in ZLStyleSpans.apply(shown, style, r[0], r[1], context, highlight = true)) spans += shown to span
        }
        pressedSpans = spans
    }

    private fun clearPressed() {
        for ((text, span) in pressedSpans) text.removeSpan(span)
        pressedSpans = emptyList()
        pressedLink = null
    }

    // MARK: Drawing

    override fun onDraw(canvas: Canvas) {
        val layout = layout
        val shown = text as? Spanned
        if (!ready || layout == null || shown == null) {
            super.onDraw(canvas)
            return
        }
        val left = totalPaddingLeft.toFloat()
        val top = totalPaddingTop.toFloat()
        // Capsules may extend into the padding but not beyond the view.
        textArea.set(-left + scrollX, -top + scrollY, width - left + scrollX, height - top + scrollY)
        canvas.withTranslation(left, top) { painter.drawBackgrounds(this, layout, shown, paint, textArea) }
        super.onDraw(canvas)
        canvas.withTranslation(left, top) { painter.drawGradients(this, layout, shown, paint) }
    }

    // MARK: Selection

    /** Selected content range, `null` without selection. */
    public val selectedRange: ZLRange?
        get() {
            val map = display?.map ?: return null
            val start = selectionStart
            val end = selectionEnd
            if (start < 0 || end <= start) return null
            val s = map.contentCaret(start).coerceIn(0, contentLength)
            val e = map.contentCaret(end).coerceIn(s, contentLength)
            return if (e > s) ZLRange(s, e) else null
        }

    /** Selected display text (inline widgets removed). */
    public val selectedText: String?
        get() = selectedRange?.let { content.subSequence(it.start, it.end).toString().replace(OBJECT_REPLACEMENT, "") }

    /** Raw text of the selection; [ZLRichTextView] maps rule matches back to their raw text. */
    public val selectedRawText: String?
        get() = selectedRange?.let { rawTextForContentRange(it) ?: selectedText }

    /** Raw text of a content range, `null` when the content is the raw text (no "copy raw" item). */
    protected open fun rawTextForContentRange(range: ZLRange): String? = null

    /** Selects a content range (needs `textIsSelectable`). */
    public fun select(start: Int, end: Int) {
        val shown = text as? Spannable ?: return
        val map = display?.map ?: return
        Selection.setSelection(shown, map.displayCaret(start), map.displayCaret(min(end, contentLength)))
    }

    /** Selects the whole content (without the collapse token). */
    public fun selectAllContent(): Unit = select(0, contentLength)

    /** Removes the selection. */
    public fun clearSelection() {
        val shown = text as? Spannable ?: return
        Selection.setSelection(shown, max(selectionEnd, 0))
    }

    /** Copies the selection; [raw] copies the raw text. */
    @JvmOverloads
    public fun copySelection(raw: Boolean = false) {
        val copied = (if (raw) selectedRawText else selectedText) ?: return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(null, copied))
        onCopyListener?.onCopy(copied, raw)
    }

    override fun onTextContextMenuItem(id: Int): Boolean {
        if (ready && id == android.R.id.copy && selectedRange != null) {
            copySelection(raw = false)
            clearSelection()
            return true
        }
        return super.onTextContextMenuItem(id)
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        if (ready && !internalTextChange && display != null) onSelectionChangeListener?.onSelectionChanged(selectedRange)
    }

    /** Adds "copy raw text" to the selection menu when the content differs from the raw text. */
    private inner class SelectionMenu : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            val range = selectedRange
            if (range != null && rawTextForContentRange(range) != null) {
                menu.add(Menu.NONE, MENU_COPY_RAW, 2, copyRawMenuTitle)
            }
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            if (item.itemId != MENU_COPY_RAW) return false
            copySelection(raw = true)
            mode.finish()
            clearSelection()
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) = Unit
    }

    public companion object {
        private const val UNBOUNDED_WIDTH = 1 shl 20
        private const val MENU_COPY_RAW = 0x5A4C
        internal const val OBJECT_REPLACEMENT = "\uFFFC"
        private const val DEFAULT_LINK_COLOR = 0xFF007AFF.toInt()

        /** Blue, underlined. */
        @JvmField
        public val DEFAULT_LINK_STYLE: ZLTextStyle = ZLTextStyle(underline = true).color(DEFAULT_LINK_COLOR)

        /** Half transparent blue. */
        @JvmField
        public val DEFAULT_ACTIVE_LINK_STYLE: ZLTextStyle = ZLTextStyle().color(0x80007AFF.toInt())

        /** Grey. */
        @JvmField
        public val DEFAULT_INACTIVE_LINK_STYLE: ZLTextStyle = ZLTextStyle().color(0xFF8E8E93.toInt())

        internal fun colored(text: CharSequence, color: Int): CharSequence = SpannableStringBuilder(text).apply {
            setSpan(android.text.style.ForegroundColorSpan(color), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}
