package com.zl.richtext.demo

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.MaterialToolbar

/** Base page: a toolbar on top and the content below, inset for the system bars (edge-to-edge). */
abstract class DemoActivity : AppCompatActivity() {

    /** The page content below the toolbar. */
    abstract fun createContent(): View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val toolbar = MaterialToolbar(this).apply {
            setBackgroundColor(0xFFD0BCFF.toInt())
            title = this@DemoActivity.title
        }
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(!isTaskRoot)
        toolbar.setNavigationOnClickListener { finish() }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFD0BCFF.toInt())
            addView(toolbar, matchWidth())
            addView(createContent().apply { setBackgroundColor(0xFFFEF7FF.toInt()) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(root)
    }
}
