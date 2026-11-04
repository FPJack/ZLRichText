package com.zl.richtext.rule

/**
 * A half-open range `[start, end)` of UTF-16 offsets, the same convention as
 * Android spans.
 */
public data class ZLRange(val start: Int, val end: Int) {
    init {
        require(start <= end) { "start ($start) > end ($end)" }
    }

    val length: Int get() = end - start

    val isEmpty: Boolean get() = start == end

    public operator fun contains(index: Int): Boolean = index in start until end

    /** Whether the two ranges share at least one character. */
    public fun intersects(other: ZLRange): Boolean = start < other.end && other.start < end

    public fun shift(offset: Int): ZLRange = ZLRange(start + offset, end + offset)

    override fun toString(): String = "[$start, $end)"
}

/** Result of a [ZLPattern]: the whole range plus capture groups. */
public class ZLMatchResult(
    /** The searched string. */
    public val input: String,
    /** Group ranges; index 0 is the whole match, `null` for groups that did not take part. */
    public val groups: List<ZLRange?>,
    /** Named group → group index. */
    public val names: Map<String, Int> = emptyMap(),
    /** Extra data of custom patterns (detection result, tag element …). */
    public val payload: Any? = null,
) {
    init {
        require(groups.isNotEmpty() && groups[0] != null) { "group 0 is required" }
    }

    public val range: ZLRange get() = groups[0]!!

    public val start: Int get() = range.start

    public val end: Int get() = range.end

    public val value: String get() = input.substring(start, end)

    /** Number of capture groups (without group 0). */
    public val groupCount: Int get() = groups.size - 1

    public fun group(index: Int): String? = groups.getOrNull(index)?.let { input.substring(it.start, it.end) }

    public fun group(name: String): String? = names[name]?.let { group(it) }
}

/**
 * Finds content in a string. [ZLPattern.regex] adapts a [Regex]; the data
 * detector and the tag parser are patterns as well.
 */
public fun interface ZLPattern {
    /** All matches in [input], sorted by position. */
    public fun findAll(input: String): List<ZLMatchResult>

    public companion object {
        /** A pattern backed by a regular expression. */
        @JvmStatic
        @JvmOverloads
        public fun regex(pattern: String, options: Set<RegexOption> = emptySet()): ZLPattern =
            ZLRegexPattern(Regex(pattern, options))

        @JvmStatic
        public fun regex(regex: Regex): ZLPattern = ZLRegexPattern(regex)
    }
}

/** [ZLPattern] backed by a [Regex]. Named groups work on every API level. */
public class ZLRegexPattern(public val regex: Regex) : ZLPattern {
    private val names: Map<String, Int> = namedGroupIndices(regex.pattern)

    override fun findAll(input: String): List<ZLMatchResult> =
        regex.findAll(input).map { match ->
            ZLMatchResult(
                input = input,
                groups = match.groups.map { group -> group?.range?.let { ZLRange(it.first, it.last + 1) } },
                names = names,
            )
        }.toList()

    override fun toString(): String = "ZLRegexPattern(${regex.pattern})"

    internal companion object {
        /**
         * Indices of named groups `(?<name>…)`, counted like the regex engine
         * counts capture groups (named group lookup through the engine needs
         * API 26 on Android).
         */
        fun namedGroupIndices(pattern: String): Map<String, Int> {
            val result = mutableMapOf<String, Int>()
            var index = 0
            var i = 0
            var inClass = false
            while (i < pattern.length) {
                val c = pattern[i]
                when {
                    c == '\\' -> i++
                    inClass -> if (c == ']') inClass = false
                    c == '[' -> inClass = true
                    c == '(' -> {
                        if (pattern.startsWith("(?<", i) && i + 3 < pattern.length &&
                            pattern[i + 3] != '=' && pattern[i + 3] != '!'
                        ) {
                            index++
                            val close = pattern.indexOf('>', i + 3)
                            if (close > 0) result[pattern.substring(i + 3, close)] = index
                        } else if (!pattern.startsWith("(?", i)) {
                            index++
                        }
                    }
                }
                i++
            }
            return result
        }
    }
}
