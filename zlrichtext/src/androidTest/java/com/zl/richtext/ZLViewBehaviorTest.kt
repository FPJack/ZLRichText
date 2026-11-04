package com.zl.richtext

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zl.richtext.rule.ZLRange
import com.zl.richtext.rule.ZLRichMatchListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Truncation, taps, expand / collapse and raw text on real views. */
@RunWith(AndroidJUnit4::class)
class ZLViewBehaviorTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().context

    private fun onMain(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

    private fun View.layoutAt(widthPx: Int = 600) {
        // A parent would set these; TextView reads them when the text changes after a layout.
        if (layoutParams == null) layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        measure(View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        layout(0, 0, measuredWidth, measuredHeight)
    }

    private fun View.tap(x: Float, y: Float) {
        val time = SystemClock.uptimeMillis()
        for ((action, offset) in listOf(MotionEvent.ACTION_DOWN to 0L, MotionEvent.ACTION_UP to 50L)) {
            val event = MotionEvent.obtain(time, time + offset, action, x, y, 0)
            dispatchTouchEvent(event)
            event.recycle()
        }
    }

    /** Taps the middle of a content range. */
    private fun ZLAttributedTextView.tapRange(start: Int, end: Int) {
        val rect = requireNotNull(boundingRectForRange(start, end)) { "range [$start, $end) not shown" }
        tap(rect.centerX(), rect.centerY())
    }

    private val long = "这是一段很长的文字，".repeat(30)

    @Test
    fun multipleLinesAreTruncatedAtTheTail() = onMain {
        val view = ZLAttributedTextView(context)
        view.maxLines = 2
        view.text = long
        view.layoutAt()

        assertTrue(view.isTruncated)
        assertEquals(2, view.layout.lineCount)
        assertTrue(view.fullLineCount > 2)
        assertTrue(view.text.toString().endsWith("…"))
        // The content keeps the whole text.
        assertEquals(long, view.contentText.toString())
    }

    @Test
    fun singleLineMiddleTruncationKeepsHeadAndTail() = onMain {
        val view = ZLAttributedTextView(context)
        view.maxLines = 1
        view.truncationMode = ZLTruncationMode.MIDDLE
        view.text = "HEAD" + "x".repeat(300) + "TAIL"
        view.layoutAt()

        val shown = view.text.toString()
        assertEquals(1, view.layout.lineCount)
        assertTrue(shown, shown.startsWith("HEAD") && shown.endsWith("TAIL") && shown.contains("…"))
    }

    @Test
    fun tapOnRuleMatchCallsItsHandlerOnly() = onMain {
        val view = ZLRichTextView(context)
        val taps = ArrayList<String>()
        view.addRule("mention", "@\\w+") { ctx -> ctx.onTap = ZLRichMatchListener { taps += it.text } }
        view.text = "hello @alice and @bob"
        view.layoutAt()

        val content = view.contentText.toString()
        view.tapRange(content.indexOf("@bob"), content.indexOf("@bob") + 4)
        view.tapRange(content.indexOf("@alice"), content.indexOf("@alice") + 6)
        view.tapRange(0, 5) // plain text
        assertEquals(listOf("@bob", "@alice"), taps)
    }

    @Test
    fun expandAndCollapseTokens() = onMain {
        val view = ZLRichTextView(context)
        val changes = ArrayList<Boolean>()
        view.maxLines = 2
        view.expandToken = "… 全文"
        view.collapseToken = " 收起"
        view.onExpandChangeListener = ZLExpandListener { changes += it }
        view.text = long
        view.layoutAt()

        assertTrue(view.isTruncated)
        assertEquals(2, view.layout.lineCount)
        assertTrue(view.text.toString().endsWith("全文"))

        val token = requireNotNull(view.expandTokenRect)
        view.tap(token.centerX(), token.centerY())
        assertTrue(view.isExpanded)
        assertEquals(listOf(true), changes)
        view.layoutAt()
        assertTrue(view.layout.lineCount > 2)
        assertTrue(view.text.toString().endsWith("收起"))

        // Restoring the state in code does not call the listener.
        view.isExpanded = false
        view.layoutAt()
        assertFalse(view.isExpanded)
        assertEquals(2, view.layout.lineCount)
        assertEquals(listOf(true), changes)
    }

    @Test
    fun displayedTextMapsBackToRawText() = onMain {
        val view = ZLRichTextView(context)
        view.addRule("mention", "@(\\w+)") { ctx -> ctx.setText(ctx.group(1)!!.replaceFirstChar { it.uppercase() }) }
        view.text = "hi @alice!"
        view.layoutAt()

        val content = view.contentText.toString()
        assertEquals("hi Alice!", content)
        // A partially covered match maps to its whole raw text.
        assertEquals("@alice", view.rawTextForRange(ZLRange(4, 6)))
        assertEquals("hi @alice!", view.rawTextForRange(ZLRange(0, content.length)))

        val rect = requireNotNull(view.boundingRectForRange(3, 8))
        val info = view.gestureInfoAt(rect.centerX(), rect.centerY())
        assertEquals("@alice", info.match?.text)
        assertEquals("Alice", info.matchDisplayText)

        view.setTextIsSelectable(true)
        view.select(0, content.length)
        assertEquals("hi Alice!", view.selectedText)
        assertEquals("hi @alice!", view.selectedRawText)
        assertEquals("hi @alice!", view.rawText)
    }

    @Test
    fun turningSelectionOnKeepsContentAndLinks() = onMain {
        // setTextIsSelectable() sets the displayed text again; it must not become new content.
        val view = ZLAttributedTextView(context)
        view.text = "open the docs"
        view.addLink("https://example.com", 9, 13)
        view.layoutAt()
        view.setTextIsSelectable(true)
        view.layoutAt()
        assertEquals("open the docs", view.contentText.toString())
        assertEquals(listOf("https://example.com"), view.links.map { it.url })
    }
}
