package com.zl.richtext

import android.content.Context
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ZLGeometryViewTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().context

    private fun onMain(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

    private fun View.layoutAt(widthPx: Int) {
        measure(View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        layout(0, 0, measuredWidth, measuredHeight)
    }

    @Test
    fun rangeWrappingAtLineEndOnlyCoversItsGlyphs() = onMain {
        val view = ZLAttributedTextView(context)
        view.text = "0123456789".repeat(20)
        view.layoutAt(600)
        val lineEnd = view.layout.getLineEnd(0)
        assertTrue(view.layout.lineCount > 1)

        // Two characters at the end of line 0, three at the start of line 1.
        val rects = view.rectsForRange(lineEnd - 2, lineEnd + 3)
        assertEquals(2, rects.size)
        val charWidth = view.paint.measureText("0")
        assertTrue("first line part ${rects[0]}", rects[0].width() < charWidth * 3)
        assertTrue("first line part ${rects[0]}", rects[0].left > charWidth * 3)
        assertTrue("second line part ${rects[1]}", rects[1].width() < charWidth * 4)
    }
}
