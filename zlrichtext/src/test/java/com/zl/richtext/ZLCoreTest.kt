package com.zl.richtext

import com.zl.richtext.detect.ZLDataDetector
import com.zl.richtext.detect.ZLDataDetectorType
import com.zl.richtext.rule.ZLRegexPattern
import com.zl.richtext.rule.ZLRichParser
import com.zl.richtext.rule.ZLRichRule
import com.zl.richtext.tag.ZLTagInfo
import com.zl.richtext.tag.ZLTagMarkupPattern
import com.zl.richtext.tag.ZLTagParser
import com.zl.richtext.tag.ZLTagParserConfiguration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ZLRichParserTest {

    private fun rule(name: String, pattern: String, priority: Int = 0) = ZLRichRule.regex(name, pattern, priority) { }

    @Test
    fun higherPriorityWinsAndLosersAreDroppedWhole() {
        val matches = ZLRichParser.resolve("call 13800138000 or 2024", listOf(rule("number", "\\d+", 1), rule("phone", "1\\d{10}", 20)))
        assertEquals(listOf("phone", "number"), matches.map { it.rule.name })
        assertEquals(listOf("13800138000", "2024"), matches.map { it.text })
        assertEquals(listOf(0, 1), matches.map { it.index })
    }

    @Test
    fun samePriorityEarlierThenLongerThenRuleOrder() {
        val matches = ZLRichParser.resolve("**nested `x`** and `y`", listOf(rule("bold", "\\*\\*(.+?)\\*\\*"), rule("code", "`([^`]+)`")))
        assertEquals(listOf("bold", "code"), matches.map { it.rule.name })
        assertEquals("nested `x`", matches[0].group(1))
    }

    @Test
    fun namedGroupsWorkWithoutEngineSupport() {
        assertEquals(mapOf("key" to 1), ZLRegexPattern.namedGroupIndices("\\{\\{(?<key>\\w+)\\}\\}"))
        assertEquals(mapOf("b" to 2), ZLRegexPattern.namedGroupIndices("(a)(?:x)(?<b>c)(?=d)[(]"))
        val segments = ZLRichParser.segments("Hi {{name}}!", listOf(rule("var", "\\{\\{(?<key>\\w+)\\}\\}")))
        assertEquals(listOf("Hi ", "{{name}}", "!"), segments.map { it.text })
        assertEquals("name", segments[1].match!!.group("key"))
        assertNull(segments[1].match!!.group("missing"))
    }

    @Test
    fun resolvesManyMatchesQuickly() {
        val text = "@user #tag# word ".repeat(5000)
        val start = System.currentTimeMillis()
        val matches = ZLRichParser.resolve(text, listOf(rule("mention", "@\\w+"), rule("topic", "#\\w+#"), rule("word", "\\w+", -1)))
        assertEquals(15000, matches.size)
        assertTrue(System.currentTimeMillis() - start < 3000)
    }
}

class ZLTagParserTest {

    @Test
    fun hTagsClosingStylesCaseAndTolerance() {
        val tags = ZLTagParser.tags("<h>a</h> <h1>b<h1/> <H4>c</H4> <h1>x</h2> <h2>y <hx>z</hx>")
        assertEquals(listOf("h", "h1", "h4"), tags.map { it.name })
        assertEquals(listOf("a", "b", "c"), tags.map { it.text })
        assertEquals(1, tags[1].level)
        assertNull(tags[0].level)
    }

    @Test
    fun withoutNestingOnlyTheInnermostTagIsKept() {
        assertEquals(listOf("<h2>b</h2>"), ZLTagParser.tags("<h1>a<h2>b</h2>c</h1>").map { it.raw })
    }

    @Test
    fun anyTagsAttributesNestingSelfClosingAndTolerance() {
        val source = "<b>bold <color value=\"#2196F3\">blue <u>under</u></color></b><br/>" +
            "<a href=\"https://x.com\" target=_blank disabled>link</a> <b>open <i>tag</b> </u>"
        val tags = ZLTagParser.tags(source, ZLTagParserConfiguration.ANY_TAGS)
        assertEquals(listOf("b", "color", "u", "br", "a", "b"), tags.map { it.name })
        assertEquals("bold blue under", tags[0].text)
        assertEquals(2, tags[2].depth)
        assertEquals("color", tags[2].parent!!.name)
        assertEquals(0xFF2196F3.toInt(), tags[1].colorAttribute("value"))
        assertEquals(mapOf("href" to "https://x.com", "target" to "_blank", "disabled" to ""), tags[4].attributes)
        assertEquals("open <i>tag", tags[5].text)
    }

    @Test
    fun colors() {
        assertEquals(0xFFFF0000.toInt(), ZLTagInfo.parseColor("#f00"))
        assertEquals(0x8000FF00.toInt(), ZLTagInfo.parseColor("00ff0080"))
        assertTrue(ZLTagInfo.parseColor("orange") != null)
        assertNull(ZLTagInfo.parseColor("nope"))
    }

    @Test
    fun simpleHTagParser() {
        assertEquals(listOf("b"), ZLTagParser.parseHTags("a <h1>b</h1> <h2>c").filter { it.isTag }.map { it.text })
    }
}

class ZLTagDelimiterTest {

    private fun describe(tags: List<ZLTagInfo>) = tags.map { "${it.name}:${it.text}:${it.depth}" }

    private fun config(open: String, close: String, any: Boolean = false) =
        (if (any) ZLTagParserConfiguration.ANY_TAGS else ZLTagParserConfiguration.H_TAGS).copy(openDelimiter = open, closeDelimiter = close)

    @Test
    fun defaultDelimitersUnchanged() {
        assertEquals("<", ZLTagParserConfiguration.H_TAGS.openDelimiter)
        assertEquals(">", ZLTagParserConfiguration.H_TAGS.closeDelimiter)
        // Empty strings mean the default delimiters.
        assertEquals(listOf("h1:a:0"), describe(ZLTagParser.tags("<h1>a</h1>", config("", ""))))
    }

    @Test
    fun customDelimitersFollowTheSameRulesAsAngleBrackets() {
        // The result with `<` `>` is the reference; replacing the delimiters gives the same tags.
        val template = "agree <h1>User</h1>, <h2>Privacy<h2/> <h>x</h1> <h3>a</h3> <h1>a<h2>b</h2></h1> <hx>y</hx>"
        val expected = describe(ZLTagParser.tags(template))
        assertEquals(listOf("h1:User:0", "h2:Privacy:0", "h3:a:0", "h2:b:0"), expected)
        for ((open, close) in listOf("[" to "]", "【" to "】", "{" to "}", "{{" to "}}", "((" to "))")) {
            val source = template.replace("<", open).replace(">", close)
            val tags = ZLTagParser.tags(source, config(open, close))
            assertEquals("$open$close", expected, describe(tags))
            assertEquals("${open}h1${close}User$open/h1$close", tags.first().raw)
            // `<h1>` is no longer a tag.
            assertEquals(emptyList<String>(), describe(ZLTagParser.tags("<h1>a</h1>", config(open, close))))
        }
    }

    @Test
    fun customDelimitersWithAttributesNestingAndSelfClosing() {
        val source = "[b]粗 [color value=\"#f00\"]红[/color] [a href='https://x.com?a=1' target=_blank]链接[/a][/b][br/]"
        val tags = ZLTagParser.tags(source, config("[", "]", any = true))
        assertEquals(listOf("b:粗 红 链接:0", "color:红:1", "a:链接:1", "br::0"), describe(tags))
        assertEquals(mapOf("href" to "https://x.com?a=1", "target" to "_blank"), tags[2].attributes)
        assertEquals(0xFFFF0000.toInt(), tags[1].colorAttribute("value"))
        // An unquoted value stops at the closing delimiter.
        val t = ZLTagParser.tags("【color value=red】x【/color】", config("【", "】", any = true))
        assertEquals(mapOf("value" to "red"), t.first().attributes)
        assertEquals("x", t.first().text)
    }

    @Test
    fun javaEntry() {
        val tags = ZLTagParser.tags("{h1}a{/h1}", "h\\d*", false, false, "{", "}")
        assertEquals(listOf("h1:a:0"), describe(tags))
    }

    @Test
    fun ruleMatchCaptureGroupsWithMultiCharDelimiter() {
        val match = ZLTagMarkupPattern(config("{{", "}}")).findAll("x {{h2}}ok{{/h2}}").single()
        assertEquals("h2", match.group(1)) // group 1 is the tag name
        assertEquals("ok", match.group(2)) // group 2 is the content
    }
}

class ZLDataDetectorTest {

    @Test
    fun linksPhonesDatesAddresses() {
        val results = ZLDataDetector(ZLDataDetectorType.entries.toSet()).detect(
            "网址 https://developer.apple.com，电话 010-12345678，日期 2026年10月1日 下午3点，地址 1 Infinite Loop, Cupertino, CA 95014。",
        )
        assertEquals(
            listOf(ZLDataDetectorType.LINK, ZLDataDetectorType.PHONE_NUMBER, ZLDataDetectorType.DATE, ZLDataDetectorType.ADDRESS),
            results.map { it.type },
        )
        assertEquals("https://developer.apple.com", results[0].url)
        assertEquals("01012345678", results[1].phoneNumber)
        val date = Calendar.getInstance().apply { time = results[2].date!! }
        assertEquals(2026, date.get(Calendar.YEAR))
        assertEquals(15, date.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun flags() {
        assertEquals(setOf(ZLDataDetectorType.LINK, ZLDataDetectorType.EMAIL), ZLDataDetectorType.fromFlags(1 or 4))
    }
}
