package com.zl.richtext.detect

import com.zl.richtext.rule.ZLMatchResult
import com.zl.richtext.rule.ZLPattern
import com.zl.richtext.rule.ZLRange
import java.util.Calendar
import java.util.Date

/** Kinds of content recognised by [ZLDataDetector]. */
public enum class ZLDataDetectorType(internal val flag: Int) {
    /** `https://…`, `www.…`, bare domains with common suffixes. */
    LINK(1),

    /** `010-12345678`, `+86 138 0013 8000`, `(408) 555-0100` … */
    PHONE_NUMBER(2),

    /** E-mail addresses. */
    EMAIL(4),

    /** `2026-10-01`, `2026/10/1`, `2026年10月1日 下午3点`, `Oct 1, 2026`. */
    DATE(8),

    /** Simple addresses: `1 Infinite Loop, Cupertino, CA 95014`, `北京市海淀区…路…号`. */
    ADDRESS(16),
    ;

    public companion object {
        /** Types of a flag set (the `zl_dataDetectorTypes` XML attribute). */
        @JvmStatic
        public fun fromFlags(flags: Int): Set<ZLDataDetectorType> = entries.filterTo(LinkedHashSet()) { flags and it.flag != 0 }
    }
}

/** One detection. */
public class ZLDataDetectorResult(
    public val type: ZLDataDetectorType,
    public val input: String,
    public val range: ZLRange,
) {
    /** The matched text. */
    public val text: String get() = input.substring(range.start, range.end)

    /** URL of links (`https://` added when missing), `mailto:` of e-mails, `tel:` of phone numbers. */
    public val url: String?
        get() = when (type) {
            ZLDataDetectorType.LINK -> if (SCHEME.containsMatchIn(text)) text else "https://$text"
            ZLDataDetectorType.EMAIL -> "mailto:$text"
            ZLDataDetectorType.PHONE_NUMBER -> "tel:$phoneNumber"
            else -> null
        }

    /** Digits (and a leading `+`) of phone numbers. */
    public val phoneNumber: String? get() = if (type == ZLDataDetectorType.PHONE_NUMBER) text.replace(NON_DIGIT, "") else null

    /** Parsed date of date results. */
    public val date: Date? get() = if (type == ZLDataDetectorType.DATE) ZLDataDetector.parseDate(text) else null

    override fun toString(): String = "ZLDataDetectorResult($type, \"$text\", $range)"

    private companion object {
        val SCHEME = Regex("^[a-z][a-z0-9+.-]*://", RegexOption.IGNORE_CASE)
        val NON_DIGIT = Regex("[^\\d+]")
    }
}

/**
 * Regular-expression based data detector (the counterpart of `NSDataDetector`). It is a
 * [ZLPattern], so it can be the pattern of a rule; the payload of each match is a
 * [ZLDataDetectorResult].
 */
public class ZLDataDetector(types: Set<ZLDataDetectorType>) : ZLPattern {
    public val types: Set<ZLDataDetectorType> = types.toSet()

    /** All results, sorted, without overlaps (earlier / longer results win). */
    public fun detect(string: String): List<ZLDataDetectorResult> {
        val all = ArrayList<ZLDataDetectorResult>()
        fun collect(type: ZLDataDetectorType, regex: Regex) {
            if (type !in types) return
            for (m in regex.findAll(string)) {
                var end = m.range.last + 1
                if (type == ZLDataDetectorType.LINK) {
                    while (end > m.range.first && string[end - 1] in ".,;:!?)") end--
                }
                if (end > m.range.first) all += ZLDataDetectorResult(type, string, ZLRange(m.range.first, end))
            }
        }
        // Order matters for equal positions: e-mail before link, date before phone number.
        collect(ZLDataDetectorType.EMAIL, EMAIL)
        collect(ZLDataDetectorType.LINK, LINK)
        collect(ZLDataDetectorType.DATE, DATE)
        collect(ZLDataDetectorType.ADDRESS, ADDRESS)
        collect(ZLDataDetectorType.PHONE_NUMBER, PHONE)
        all.sortWith(compareBy<ZLDataDetectorResult> { it.range.start }.thenByDescending { it.range.length })
        val result = ArrayList<ZLDataDetectorResult>()
        for (r in all) {
            if (result.isNotEmpty() && result.last().range.end > r.range.start) continue
            result += r
        }
        return result
    }

    override fun findAll(input: String): List<ZLMatchResult> =
        detect(input).map { ZLMatchResult(input, listOf(it.range), payload = it) }

    public companion object {
        private val LINK = Regex(
            """(?:(?:https?|ftp)://|www\.)[^\s<>"'，。；、）】」』]+|\b(?:[a-z0-9-]+\.)+(?:com|net|org|cn|io|dev|app|edu|gov|co)(?:/[^\s<>"'，。；、）】」』]*)?""",
            RegexOption.IGNORE_CASE,
        )
        private val EMAIL = Regex("""\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b""")
        private val PHONE = Regex(
            """(?<![\w/])(?:\+?\d{1,3}[\s-]?)?(?:\(\d{2,4}\)[\s-]?|\d{2,4}[\s-])?\d{3,4}[\s-]?\d{4}(?![\w/])|(?<!\d)1[3-9]\d{9}(?!\d)""",
        )
        private val DATE = Regex(
            """\d{4}年\d{1,2}月\d{1,2}[日号](?:\s*(?:上午|下午|晚上|中午)?\s*\d{1,2}(?:[点时:：]\d{0,2}分?)?)?""" +
                """|\b\d{4}[-/.]\d{1,2}[-/.]\d{1,2}(?:[ T]\d{1,2}:\d{2}(?::\d{2})?)?\b""" +
                """|\b(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Sept|Oct|Nov|Dec)[a-z]*\.?\s+\d{1,2}(?:st|nd|rd|th)?,?\s+\d{4}\b""",
            RegexOption.IGNORE_CASE,
        )
        private val ADDRESS = Regex(
            """\b\d{1,6}\s+(?:[A-Z][A-Za-z]*\s+){1,4}(?:St|Street|Rd|Road|Ave|Avenue|Blvd|Boulevard|Loop|Way|Dr|Drive|Ln|Lane|Ct|Court|Pkwy|Parkway)\.?""" +
                """(?:,\s*[A-Z][A-Za-z]*(?:\s+[A-Z][A-Za-z]*)*)?(?:,\s*[A-Z]{2})?(?:\s+\d{5}(?:-\d{4})?)?""" +
                """|[一-龥]{2,}(?:省|市)[一-龥]+(?:区|县|市)[一-龥\d]+(?:路|街|道|巷)\d+号""",
        )
        private val CN_DATE = Regex("""(\d{4})年(\d{1,2})月(\d{1,2})[日号]\s*(上午|下午|晚上|中午)?\s*(\d{1,2})?(?:[点时:：](\d{1,2})?)?""")
        private val ISO_DATE = Regex("""(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})(?:[ T](\d{1,2}):(\d{2}))?""")
        private val EN_DATE = Regex("""([a-z]{3})[a-z]*\.?\s+(\d{1,2})(?:st|nd|rd|th)?,?\s+(\d{4})""", RegexOption.IGNORE_CASE)
        private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

        /** Parses the date formats recognised by the detector. */
        @JvmStatic
        public fun parseDate(text: String): Date? {
            fun date(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0): Date =
                Calendar.getInstance().apply { clear(); set(y, m - 1, d, h, min) }.time

            CN_DATE.find(text)?.let { g ->
                var hour = g.groupValues[5].toIntOrNull() ?: 0
                val period = g.groupValues[4]
                if ((period == "下午" || period == "晚上") && hour < 12) hour += 12
                return date(g.groupValues[1].toInt(), g.groupValues[2].toInt(), g.groupValues[3].toInt(), hour,
                    g.groupValues[6].toIntOrNull() ?: 0)
            }
            ISO_DATE.find(text)?.let { g ->
                return date(g.groupValues[1].toInt(), g.groupValues[2].toInt(), g.groupValues[3].toInt(),
                    g.groupValues[4].toIntOrNull() ?: 0, g.groupValues[5].toIntOrNull() ?: 0)
            }
            EN_DATE.find(text)?.let { g ->
                val month = MONTHS.indexOf(g.groupValues[1].lowercase()) + 1
                if (month > 0) return date(g.groupValues[3].toInt(), month, g.groupValues[2].toInt())
            }
            return null
        }
    }
}
