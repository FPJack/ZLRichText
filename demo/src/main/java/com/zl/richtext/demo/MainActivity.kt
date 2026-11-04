package com.zl.richtext.demo

import android.content.Intent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity

/**
 * Home page: every feature of `ZLTagTextView` / `ZLRichTextView` (cases 1 – 31 of the iOS
 * example). The menu opens the RecyclerView page, the XML page and the base `ZLAttributedTextView`
 * page.
 */
class MainActivity : DemoActivity() {
    private val log = TapLog()

    override fun createContent(): View {
        title = "ZLRichText Demo"
        val page = buildPage(this, log, allCases(this, log))
        // Debug helper: `adb shell am start -n com.zl.richtext.demo/.MainActivity --es case 24` scrolls to case 24.
        intent.getStringExtra("case")?.let { prefix -> page.postDelayed({ scrollToCase(page, prefix) }, 1500) }
        return page
    }

    private fun scrollToCase(page: View, prefix: String) {
        val scroll = page.findViewById<ScrollView>(R.id.demo_scroll)
        val column = scroll.getChildAt(0) as LinearLayout
        for (i in 0 until column.childCount) {
            val card = column.getChildAt(i)
            if ((card.tag as? String)?.startsWith("$prefix.") == true) {
                scroll.scrollTo(0, card.top - dp(8f))
                return
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, MENU_LIST, 0, "RecyclerView")
        menu.add(0, MENU_XML, 1, "XML")
        menu.add(0, MENU_BASE, 2, "Base")
        menu.add(0, MENU_CLEAR, 3, "清空点击记录")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            MENU_LIST -> startActivity(Intent(this, ListDemoActivity::class.java))
            MENU_XML -> startActivity(Intent(this, XmlDemoActivity::class.java))
            MENU_BASE -> startActivity(Intent(this, AttributedDemoActivity::class.java))
            MENU_CLEAR -> log.clear()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    companion object {
        private const val MENU_LIST = 1
        private const val MENU_XML = 2
        private const val MENU_BASE = 3
        private const val MENU_CLEAR = 4

        /** All cards of the home page, in the order of the iOS example. */
        fun allCases(activity: AppCompatActivity, log: TapLog): List<View> =
            BasicTagCases.create(activity, log) + RuleCases.create(activity, log) + AdvancedCases.create(activity, log) +
                HistoryCard(activity, log)

        /** Tap panel + scrolling column of cards. */
        fun buildPage(activity: AppCompatActivity, log: TapLog, cards: List<View>): View {
            val column = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                val padding = activity.dp(12f)
                setPadding(padding, padding, padding, padding)
                cards.forEachIndexed { i, card ->
                    addView(card, matchWidth().apply { if (i > 0) topMargin = activity.dp(12f) })
                }
            }
            return activity.vertical(
                TapInfoPanel(activity, log),
                ScrollView(activity).apply {
                    id = R.id.demo_scroll
                    // Selectable labels are focusable: keep the page from jumping to them.
                    isFocusableInTouchMode = true
                    descendantFocusability = android.view.ViewGroup.FOCUS_BEFORE_DESCENDANTS
                    addView(column)
                },
                spacing = 0,
            ).apply {
                (getChildAt(1).layoutParams as LinearLayout.LayoutParams).apply {
                    height = 0
                    weight = 1f
                }
            }
        }
    }
}
