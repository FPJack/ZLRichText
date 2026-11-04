package com.zl.richtext.rule

/** Renders one match: writes the display content into the context, sets taps and highlights. */
public fun interface ZLRichRuleHandler {
    public fun render(ctx: ZLRichRuleContext)
}

/** Callback with the rule match that was tapped / long pressed. */
public fun interface ZLRichMatchListener {
    public fun onMatch(match: ZLRichMatch)
}

/**
 * A pattern based rule.
 *
 * ```kotlin
 * val mention = ZLRichRule.regex("mention", "@\\w+", priority = 10) { ctx ->
 *     ctx.addStyle(ZLTextStyle().color(Color.MAGENTA))
 *     ctx.onTap = ZLRichMatchListener { match -> openProfile(match.text) }
 * }
 * ```
 *
 * When matches of different rules overlap:
 * 1. the higher [priority] wins;
 * 2. same priority: the earlier match wins → then the longer one → then the rule added first
 *    (built-in rules come before external rules);
 * 3. a losing match is dropped as a whole; its text is handled as plain text or by other rules.
 *
 * The handler output is cached per match while the rule object, the matched text and its
 * position stay the same. Call `rebuild()` / `setNeedsRebuild(ruleName)` on the view when the
 * data the handler reads changes.
 */
public class ZLRichRule(
    /** Name of the rule (used to remove / refresh rules and in callbacks). */
    public val name: String,
    /** What to match. */
    public val pattern: ZLPattern,
    /** Larger values win when matches overlap. */
    public val priority: Int = 0,
    /** Renders a match. */
    public val handler: ZLRichRuleHandler,
) {
    override fun toString(): String = "ZLRichRule($name, priority=$priority)"

    public companion object {
        /** A rule matching a regular expression. Throws [java.util.regex.PatternSyntaxException] when invalid. */
        @JvmStatic
        @JvmOverloads
        public fun regex(
            name: String,
            pattern: String,
            priority: Int = 0,
            options: Set<RegexOption> = emptySet(),
            handler: ZLRichRuleHandler,
        ): ZLRichRule = ZLRichRule(name, ZLPattern.regex(pattern, options), priority, handler)
    }
}

/** One effective match of a rule (ranges are relative to the raw string). */
public class ZLRichMatch(
    public val rule: ZLRichRule,
    /** Position among all effective matches (all rules counted together, from 0). */
    public val index: Int,
    /** The pattern result, with capture groups. */
    public val result: ZLMatchResult,
) {
    /** The raw string. */
    public val source: String get() = result.input

    /** Matched raw text. */
    public val text: String get() = result.value

    /** Range in the raw string. */
    public val range: ZLRange get() = result.range

    /** Capture group [index] (0 is the whole match), `null` when it did not take part. */
    public fun group(index: Int): String? = result.group(index)

    /** Named capture group `(?<name>…)`. */
    public fun group(name: String): String? = result.group(name)

    override fun toString(): String = "ZLRichMatch(rule=${rule.name}, index=$index, text=$text, range=$range)"
}

/** Plain text (`match == null`) or a rule match, in order. */
public class ZLRichSegment(public val text: String, public val match: ZLRichMatch?) {
    public val isRuleMatch: Boolean get() = match != null

    override fun toString(): String = match?.toString() ?: "ZLRichSegment(\"$text\")"
}

/** Runs rules and resolves overlaps by priority. */
public object ZLRichParser {

    private class Candidate(
        val start: Int,
        val end: Int,
        val priority: Int,
        val order: Int,
        val rule: ZLRichRule,
        val result: ZLMatchResult,
    )

    private val candidateOrder = Comparator<Candidate> { a, b ->
        when {
            a.priority != b.priority -> b.priority.compareTo(a.priority)
            a.start != b.start -> a.start.compareTo(b.start)
            a.end - a.start != b.end - b.start -> (b.end - b.start).compareTo(a.end - a.start)
            else -> a.order.compareTo(b.order)
        }
    }

    /** Effective matches sorted by position, O(n log n) for n candidates. */
    @JvmStatic
    public fun resolve(source: String, rules: List<ZLRichRule>): List<ZLRichMatch> {
        val candidates = ArrayList<Candidate>()
        rules.forEachIndexed { order, rule ->
            for (result in rule.pattern.findAll(source)) {
                if (result.end > result.start) {
                    candidates += Candidate(result.start, result.end, rule.priority, order, rule, result)
                }
            }
        }
        candidates.sortWith(candidateOrder)
        // Accepted candidates never overlap and are sorted by position: a binary search finds
        // the insertion point and only the two neighbours can overlap.
        val accepted = ArrayList<Candidate>(candidates.size)
        for (c in candidates) {
            var low = 0
            var high = accepted.size
            while (low < high) {
                val mid = (low + high) ushr 1
                if (accepted[mid].start < c.start) low = mid + 1 else high = mid
            }
            if (low > 0 && accepted[low - 1].end > c.start) continue
            if (low < accepted.size && accepted[low].start < c.end) continue
            accepted.add(low, c)
        }
        return accepted.mapIndexed { i, c -> ZLRichMatch(c.rule, i, c.result) }
    }

    /** Splits [source] into plain text and matches. */
    @JvmStatic
    public fun segments(source: String, rules: List<ZLRichRule>): List<ZLRichSegment> =
        segmentsOf(source, resolve(source, rules))

    /** Splits [source] into plain text and the given (sorted, non-overlapping) matches. */
    @JvmStatic
    public fun segmentsOf(source: String, matches: List<ZLRichMatch>): List<ZLRichSegment> {
        val result = ArrayList<ZLRichSegment>()
        var cursor = 0
        for (match in matches) {
            if (match.range.start > cursor) result += ZLRichSegment(source.substring(cursor, match.range.start), null)
            result += ZLRichSegment(match.text, match)
            cursor = match.range.end
        }
        if (cursor < source.length) result += ZLRichSegment(source.substring(cursor), null)
        return result
    }
}
