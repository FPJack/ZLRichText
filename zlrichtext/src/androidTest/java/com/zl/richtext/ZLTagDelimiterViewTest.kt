package com.zl.richtext

import android.content.Context
import android.graphics.Color
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zl.richtext.style.ZLTextStyle
import com.zl.richtext.test.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ZLTagDelimiterViewTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().context

    private fun onMain(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

    private fun View.layoutAt(widthPx: Int = 1000) {
        measure(View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        layout(0, 0, measuredWidth, measuredHeight)
    }

    private fun ZLAttributedTextView.colorAt(index: Int): Int? =
        (contentText as Spanned).getSpans(index, index + 1, ForegroundColorSpan::class.java).lastOrNull()?.foregroundColor

    /** Taps the link covering [index] and returns whether one was found. */
    private fun ZLAttributedTextView.tapCharacter(index: Int): Boolean {
        val link = links.firstOrNull { index >= it.start && index < it.end } ?: return false
        performLinkClick(link)
        return true
    }

    @Test
    fun viewUsesCustomDelimiters() = onMain {
        val view = ZLTagTextView(context)
        val tapped = ArrayList<String>()
        view.tagOpenDelimiter = "【"
        view.tagCloseDelimiter = "】"
        view.tagStyles = mapOf("h1" to ZLTextStyle().color(Color.BLUE))
        view.onTagClickListener = ZLTagListener { tapped += "${it.name}:${it.text}" }
        view.text = "同意【h1】用户协议【/h1】和<h2>隐私</h2>"
        view.layoutAt()

        assertEquals("【", view.tagConfiguration.openDelimiter)
        val display = view.contentText.toString()
        assertEquals("同意用户协议和<h2>隐私</h2>", display)
        assertEquals(listOf("h1"), view.tags.map { it.name })
        val index = display.indexOf("用户协议")
        assertEquals(Color.BLUE, view.colorAt(index))
        assertTrue(view.tapCharacter(index))
        assertEquals(listOf("h1:用户协议"), tapped)

        // Back to the default delimiters: parsed again.
        view.tagOpenDelimiter = "<"
        view.tagCloseDelimiter = ">"
        assertEquals("同意【h1】用户协议【/h1】和隐私", view.contentText.toString())
        assertEquals(listOf("h2"), view.tags.map { it.name })
    }

    @Test
    fun bracketViewDefaultsToBrackets() = onMain {
        val view = ZLBracketTextView(context)
        assertEquals("[", view.tagOpenDelimiter)
        assertEquals("]", view.tagCloseDelimiter)
        assertEquals(listOf("h1"), ZLBracketTextView(context).apply { text = "[h1]a[/h1]" }.tags.map { it.name })
    }

    @Test
    fun bracketViewParsesBracketTags() = onMain {
        val view = ZLBracketTextView(context)
        val tapped = ArrayList<String>()
        view.tagStyles = mapOf("h1" to ZLTextStyle().color(Color.BLUE))
        view.onTagClickListener = ZLTagListener { tapped += "${it.name}:${it.text}" }
        view.text = "同意 [h1]用户协议[/h1] 和 [h2]隐私[h2/]，[h3]未闭合 <h1>尖括号</h1>"
        view.layoutAt()

        val display = view.contentText.toString()
        assertEquals("同意 用户协议 和 隐私，[h3]未闭合 <h1>尖括号</h1>", display)
        assertEquals(listOf("h1:用户协议", "h2:隐私"), view.tags.map { "${it.name}:${it.text}" })
        assertEquals("[h1]用户协议[/h1]", view.tags.first().raw)
        val index = display.indexOf("用户协议")
        assertEquals(Color.BLUE, view.colorAt(index))
        assertTrue(view.tapCharacter(index))
        assertEquals(listOf("h1:用户协议"), tapped)
    }

    @Test
    fun bracketViewFromXmlAndOverride() = onMain {
        val root = LayoutInflater.from(context).inflate(R.layout.zl_test_bracket, null)
        val byDefault = root.findViewById<ZLBracketTextView>(R.id.zl_test_default)
        assertEquals("[", byDefault.tagOpenDelimiter)
        assertEquals("a<h1>b</h1>", byDefault.contentText.toString())
        // XML attributes win over the subclass default.
        val overridden = root.findViewById<ZLBracketTextView>(R.id.zl_test_override)
        assertEquals("【", overridden.tagOpenDelimiter)
        assertEquals("[h1]a[/h1]b", overridden.contentText.toString())

        // Still changeable in code.
        val view = ZLBracketTextView(context)
        view.tagOpenDelimiter = "<"
        view.tagCloseDelimiter = ">"
        view.text = "[h1]a[/h1]<h1>b</h1>"
        assertEquals("[h1]a[/h1]b", view.contentText.toString())
    }
}
