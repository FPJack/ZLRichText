package com.zl.richtext.tag

import com.zl.richtext.rule.ZLMatchResult
import com.zl.richtext.rule.ZLPattern
import com.zl.richtext.rule.ZLRange

/** Configuration of the tag parser. */
public data class ZLTagParserConfiguration @JvmOverloads constructor(
    /**
     * Recognised tag names (regular expression matching the whole name, case-insensitive).
     * Default `h\d*` (`<h>`, `<h1>`, `<h2>` …).
     */
    val namePattern: String = "h\\d*",
    /** Whether tags may nest: `<b>bold <color value="red">red</color></b>`. */
    val allowsNesting: Boolean = false,
    /** Whether empty self-closing tags are recognised: `<br/>`, `<icon name="star"/>`. */
    val allowsSelfClosing: Boolean = false,
    /**
     * Opening delimiter of tags, default `<`. For example `[`, `【`, `{` or several characters
     * like `{{`; all other rules stay the same. An empty string means `<`.
     */
    val openDelimiter: String = "<",
    /** Closing delimiter of tags, default `>`: `]`, `】`, `}`, `}}` … An empty string means `>`. */
    val closeDelimiter: String = ">",
) {
    /** The opening delimiter in use (`<` when [openDelimiter] is empty). */
    val effectiveOpenDelimiter: String get() = openDelimiter.ifEmpty { "<" }

    /** The closing delimiter in use (`>` when [closeDelimiter] is empty). */
    val effectiveCloseDelimiter: String get() = closeDelimiter.ifEmpty { ">" }

    public companion object {
        /** Any tag name: a letter followed by letters, digits, `_`, `-` or `:`. */
        public const val ANY_TAG_NAME: String = "[a-z][a-z0-9_:-]*"

        /** Default: only `<h>` / `<hN>`, no nesting, no self-closing tags. */
        @JvmField
        public val H_TAGS: ZLTagParserConfiguration = ZLTagParserConfiguration()

        /** Any tag name, nesting and self-closing tags. */
        @JvmField
        public val ANY_TAGS: ZLTagParserConfiguration = ZLTagParserConfiguration(ANY_TAG_NAME, allowsNesting = true, allowsSelfClosing = true)
    }
}

/** A parsed tag. */
public class ZLTagInfo @JvmOverloads constructor(
    /** Position among all tags (all names counted together, from 0, outer tags first). */
    public val index: Int,
    /** Lower-case tag name: `h`, `h1`, `b`, `a` … */
    public val name: String,
    /** Plain text inside the tag (inner tags removed). */
    public val text: String,
    /** The whole raw tag, e.g. `<h1>User Agreement</h1>`. */
    public val raw: String,
    /** Attributes with lower-case names: `<a href="x" disabled>` → `{href=x, disabled=}`. */
    public val attributes: Map<String, String> = emptyMap(),
    /** The enclosing tag when nested. */
    public val parent: ZLTagInfo? = null,
) {
    /** Nesting depth, 0 for outermost tags. */
    public val depth: Int get() = parent?.let { it.depth + 1 } ?: 0

    /** The number after `h` in `<hN>`: 1 for `<h1>`, `null` for `<h>` and other names. */
    public val level: Int? get() = if (name.startsWith("h")) name.substring(1).toIntOrNull() else null

    /** Attribute value (case-insensitive name). */
    public fun attribute(name: String): String? = attributes[name.lowercase()]

    /** Attribute value parsed as an ARGB colour, see [parseColor]. */
    public fun colorAttribute(name: String): Int? = attribute(name)?.let(::parseColor)

    override fun toString(): String {
        val attrs = if (attributes.isEmpty()) "" else ", attributes=$attributes"
        val nested = parent?.let { ", depth=$depth, parent=${it.name}" } ?: ""
        return "ZLTagInfo(index=$index, name=$name, text=$text, raw=$raw$attrs$nested)"
    }

    public companion object {
        private val NAMED = mapOf(
            "red" to 0xFFFF3B30.toInt(), "green" to 0xFF34C759.toInt(), "blue" to 0xFF007AFF.toInt(),
            "orange" to 0xFFFF9500.toInt(), "yellow" to 0xFFFFCC00.toInt(), "purple" to 0xFFAF52DE.toInt(),
            "pink" to 0xFFFF2D55.toInt(), "teal" to 0xFF30B0C7.toInt(), "gray" to 0xFF8E8E93.toInt(),
            "grey" to 0xFF8E8E93.toInt(), "black" to 0xFF000000.toInt(), "white" to 0xFFFFFFFF.toInt(),
            "clear" to 0,
        )

        /**
         * Parses `#rgb`, `#rrggbb`, `#rrggbbaa` (`#` optional) and common colour names (red,
         * green, blue, orange, yellow, purple, pink, teal, gray, black, white, clear) to ARGB.
         */
        @JvmStatic
        public fun parseColor(string: String): Int? {
            val value = string.trim().lowercase()
            NAMED[value]?.let { return it }
            var hex = value.removePrefix("#")
            if (hex.length == 3) hex = hex.map { "$it$it" }.joinToString("")
            if (hex.length != 6 && hex.length != 8) return null
            val number = hex.toLongOrNull(16) ?: return null
            return if (hex.length == 6) (0xFF000000 or number).toInt() else (((number and 0xFF) shl 24) or (number ushr 8)).toInt()
        }
    }
}

/** Plain text (`tag == null`) or an `<h>` tag, see [ZLTagParser.parseHTags]. */
public class ZLTagSegment(public val text: String, public val tag: ZLTagInfo?) {
    public val isTag: Boolean get() = tag != null
}

/** Node of the parsed tag tree. */
public sealed class ZLTagNode {
    /** Raw text between tags. */
    public class Text(public val range: ZLRange) : ZLTagNode()

    /** A tag element. */
    public class Element(public val element: ZLTagElement) : ZLTagNode()
}

/** A tag of the parsed tree. */
public class ZLTagElement internal constructor(
    public val name: String,
    public val attributes: Map<String, String>,
    /** Range of the opening tag (the whole tag when self-closing). */
    public val openRange: ZLRange,
    public val isSelfClosing: Boolean,
) {
    /** Range of the closing tag, `null` when self-closing. */
    public var closeRange: ZLRange? = null
        internal set

    public val children: MutableList<ZLTagNode> = ArrayList()

    public var parent: ZLTagElement? = null
        internal set

    /** The whole tag, from the opening to the closing tag. */
    public val range: ZLRange get() = ZLRange(openRange.start, (closeRange ?: openRange).end)

    /** The content between the opening and closing tags. */
    public val contentRange: ZLRange
        get() = closeRange?.let { ZLRange(openRange.end, it.start) } ?: ZLRange(openRange.end, openRange.end)

    /** Plain text inside the tag (inner tags removed). */
    public fun plainText(source: String): String = buildString {
        for (child in children) {
            when (child) {
                is ZLTagNode.Text -> append(source, child.range.start, child.range.end)
                is ZLTagNode.Element -> append(child.element.plainText(source))
            }
        }
    }

    /** This element and all inner elements, outer first. */
    public val allElements: List<ZLTagElement>
        get() = listOf(this) + children.filterIsInstance<ZLTagNode.Element>().flatMap { it.element.allElements }
}

/** Result of one parse. */
public class ZLTagDocument(public val source: String, public val roots: List<ZLTagElement>)

/**
 * Tag parser.
 *
 * * [tags] / [parse]: configurable parser — any tag name, attributes, nesting, self-closing
 *   tags, custom delimiters (`[h1]text[/h1]`, `【h1】text【/h1】`, `{{b}}text{{/b}}`) and
 *   error tolerance;
 * * [parseHTags]: the simple `<h>` tag parser.
 *
 * Error tolerance: names not matching `namePattern` are text; mismatched / unclosed tags show
 * the opening tag as text, keep the content and do not affect later tags; without nesting a new
 * opening tag drops the outer unclosed tag; `<name/>` closes an open tag with the same name
 * (`<h1>text<h1/>`), otherwise it is an empty tag when self-closing tags are allowed.
 */
public object ZLTagParser {

    private val tokenRegexCache = HashMap<Pair<String, String>, Regex>()

    /**
     * Tag token: group 1 is `/` (closing), group 2 the name, group 3 the attributes, group 4 `/`
     * (self-closing). Cached per delimiter pair; attribute names and unquoted values cannot
     * contain characters of the delimiters.
     */
    private fun tokenRegex(open: String, close: String): Regex = synchronized(tokenRegexCache) {
        tokenRegexCache.getOrPut(open to close) {
            val delimiters = open + close
            val codePoints = sortedSetOf<Int>()
            var i = 0
            while (i < delimiters.length) {
                val codePoint = delimiters.codePointAt(i)
                codePoints += codePoint
                i += Character.charCount(codePoint)
            }
            val excluded = codePoints.joinToString("") { "\\x{%X}".format(it) }
            val o = Regex.escape(open)
            val c = Regex.escape(close)
            Regex(
                """$o(/)?([a-z][a-z0-9_:-]*)((?:\s+[^\s=/"'$excluded]+(?:\s*=\s*(?:"[^"]*"|'[^']*'|[^\s"'=`$excluded]+))?)*)\s*(/)?$c""",
                RegexOption.IGNORE_CASE,
            )
        }
    }

    private val attributeRegex = Regex("""([^\s=/>"']+)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+)))?""")
    private val nameRegexCache = HashMap<String, Regex?>()

    private fun nameRegex(pattern: String): Regex? = synchronized(nameRegexCache) {
        nameRegexCache.getOrPut(pattern) {
            runCatching { Regex("^(?:$pattern)$", RegexOption.IGNORE_CASE) }.getOrNull()
        }
    }

    /** All tags, outer tags first. */
    @JvmStatic
    @JvmOverloads
    public fun tags(source: String, configuration: ZLTagParserConfiguration = ZLTagParserConfiguration.H_TAGS): List<ZLTagInfo> {
        val result = ArrayList<ZLTagInfo>()
        val infos = HashMap<ZLTagElement, ZLTagInfo>()
        for (root in parse(source, configuration).roots) {
            for (element in root.allElements) {
                val info = makeInfo(element, source, result.size, element.parent?.let { infos[it] })
                infos[element] = info
                result += info
            }
        }
        return result
    }

    /**
     * All tags with custom delimiters (Java-friendly), e.g. `[h1]text[/h1]` with `[` and `]`:
     * `ZLTagParser.tags(source, "h\\d*", false, false, "[", "]")`.
     */
    @JvmStatic
    public fun tags(
        source: String,
        namePattern: String,
        allowsNesting: Boolean,
        allowsSelfClosing: Boolean,
        openDelimiter: String,
        closeDelimiter: String,
    ): List<ZLTagInfo> = tags(source, ZLTagParserConfiguration(namePattern, allowsNesting, allowsSelfClosing, openDelimiter, closeDelimiter))

    /** Builds the [ZLTagInfo] of [element]. */
    @JvmStatic
    public fun makeInfo(element: ZLTagElement, source: String, index: Int, parent: ZLTagInfo?): ZLTagInfo =
        ZLTagInfo(
            index = index,
            name = element.name,
            text = element.plainText(source),
            raw = source.substring(element.range.start, element.range.end),
            attributes = element.attributes,
            parent = parent,
        )

    /** Parses [source] into a tag tree. */
    @JvmStatic
    @JvmOverloads
    public fun parse(source: String, configuration: ZLTagParserConfiguration = ZLTagParserConfiguration.H_TAGS): ZLTagDocument {
        val root = ZLTagElement("", emptyMap(), ZLRange(0, 0), false)
        val stack = arrayListOf(root)
        var cursor = 0
        val names = nameRegex(configuration.namePattern)

        // Drops an unclosed tag: its opening tag becomes text, the content moves to the parent.
        fun abandon(element: ZLTagElement) {
            val parent = element.parent ?: return
            val index = parent.children.indexOfLast { it is ZLTagNode.Element && it.element === element }
            if (index < 0) return
            for (child in element.children) if (child is ZLTagNode.Element) child.element.parent = parent
            parent.children.removeAt(index)
            parent.children.addAll(index, listOf<ZLTagNode>(ZLTagNode.Text(element.openRange)) + element.children)
        }

        // Stack index of an open tag closed by `name` (only the innermost without nesting).
        fun openIndex(name: String): Int? {
            if (stack.size <= 1) return null
            if (!configuration.allowsNesting) return if (stack.last().name == name) stack.size - 1 else null
            for (i in stack.size - 1 downTo 1) if (stack[i].name == name) return i
            return null
        }

        fun close(index: Int, range: ZLRange) {
            for (j in stack.size - 1 downTo index + 1) abandon(stack[j])
            stack[index].closeRange = range
            while (stack.size > index) stack.removeAt(stack.size - 1)
        }

        fun append(node: ZLTagNode) {
            stack.last().children += node
        }

        fun appendElement(element: ZLTagElement) {
            if (!configuration.allowsNesting && stack.size > 1) abandon(stack.removeAt(stack.size - 1))
            element.parent = stack.last()
            append(ZLTagNode.Element(element))
        }

        val tokenRegex = tokenRegex(configuration.effectiveOpenDelimiter, configuration.effectiveCloseDelimiter)
        for (token in tokenRegex.findAll(source)) {
            val start = token.range.first
            val end = token.range.last + 1
            if (start > cursor) append(ZLTagNode.Text(ZLRange(cursor, start)))
            cursor = end
            val range = ZLRange(start, end)
            val name = token.groupValues[2].lowercase()
            val isClosing = token.groups[1] != null
            val isSelfClosing = token.groups[4] != null
            if (names?.matches(name) != true || (isClosing && isSelfClosing)) {
                append(ZLTagNode.Text(range))
                continue
            }
            when {
                isClosing -> openIndex(name)?.let { close(it, range) } ?: append(ZLTagNode.Text(range))
                isSelfClosing -> {
                    val index = openIndex(name)
                    when {
                        index != null -> close(index, range) // <h1>text<h1/>
                        configuration.allowsSelfClosing ->
                            appendElement(ZLTagElement(name, attributes(token.groups[3]?.value), range, true))
                        else -> append(ZLTagNode.Text(range))
                    }
                }
                else -> {
                    val element = ZLTagElement(name, attributes(token.groups[3]?.value), range, false)
                    appendElement(element)
                    stack += element
                }
            }
        }
        if (cursor < source.length) append(ZLTagNode.Text(ZLRange(cursor, source.length)))
        // Tags still open at the end are dropped.
        while (stack.size > 1) abandon(stack.removeAt(stack.size - 1))

        val roots = root.children.filterIsInstance<ZLTagNode.Element>().map { it.element.apply { parent = null } }
        return ZLTagDocument(source, roots)
    }

    private fun attributes(source: String?): Map<String, String> {
        if (source.isNullOrBlank()) return emptyMap()
        val result = LinkedHashMap<String, String>()
        for (match in attributeRegex.findAll(source)) {
            val value = match.groups[2]?.value ?: match.groups[3]?.value ?: match.groups[4]?.value ?: ""
            result[match.groupValues[1].lowercase()] = decodeEntities(value)
        }
        return result
    }

    /** Decodes the common entities in attribute values. */
    @JvmStatic
    public fun decodeEntities(value: String): String {
        if (!value.contains('&')) return value
        return value.replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
            .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
    }

    // Simple <h> tags

    /** `<h>` tags: group 1 is the name, group 2 the text. */
    private val hTagRegex = Regex("""<(h\d*)>((?:(?!</?h\d*/?>)[\s\S])*?)(?:</\1>|<\1/>)""", RegexOption.IGNORE_CASE)

    /** Splits [source] into text and `<h>` tags (no nesting; mismatched / unclosed tags are text). */
    @JvmStatic
    public fun parseHTags(source: String): List<ZLTagSegment> {
        val segments = ArrayList<ZLTagSegment>()
        var cursor = 0
        var index = 0
        for (match in hTagRegex.findAll(source)) {
            if (match.range.first > cursor) segments += ZLTagSegment(source.substring(cursor, match.range.first), null)
            val info = ZLTagInfo(index++, match.groupValues[1].lowercase(), match.groupValues[2], match.value)
            segments += ZLTagSegment(info.text, info)
            cursor = match.range.last + 1
        }
        if (cursor < source.length) segments += ZLTagSegment(source.substring(cursor), null)
        return segments
    }

    /** Only the `<h>` tags of [parseHTags]. */
    @JvmStatic
    public fun hTags(source: String): List<ZLTagInfo> = parseHTags(source).mapNotNull { it.tag }
}

/**
 * Adapts the tag parser to a [ZLPattern]: every outermost tag is a match (group 1 is the tag
 * name, group 2 the content, the payload is the [ZLTagElement]).
 */
public class ZLTagMarkupPattern(public val configuration: ZLTagParserConfiguration) : ZLPattern {
    private var cached: ZLTagDocument? = null

    /** Parse result of [source] (cached for the last string). */
    public fun document(source: String): ZLTagDocument {
        cached?.let { if (it.source == source) return it }
        return ZLTagParser.parse(source, configuration).also { cached = it }
    }

    override fun findAll(input: String): List<ZLMatchResult> = document(input).roots.map { element ->
        val nameStart = element.openRange.start + configuration.effectiveOpenDelimiter.length
        ZLMatchResult(
            input = input,
            groups = listOf(element.range, ZLRange(nameStart, nameStart + element.name.length), element.contentRange),
            payload = element,
        )
    }
}
