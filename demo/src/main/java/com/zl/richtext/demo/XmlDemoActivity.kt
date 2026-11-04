package com.zl.richtext.demo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.materialswitch.MaterialSwitch
import com.zl.richtext.ZLAttributedTextView
import com.zl.richtext.ZLBracketTextView
import com.zl.richtext.ZLExpandListener
import com.zl.richtext.ZLLinkListener
import com.zl.richtext.ZLRichTextView
import com.zl.richtext.ZLTagListener
import com.zl.richtext.ZLTagTextView
import com.zl.richtext.rule.ZLRichMatchListener
import com.zl.richtext.style.ZLTextStyle

/**
 * Views configured in XML (`activity_xml_demo.xml`), plus the dynamic cases of the iOS SwiftUI
 * page: live text, expand state synced with a switch, `maxLines` stepper.
 */
class XmlDemoActivity : DemoActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        title = "XML 配置"
        super.onCreate(savedInstanceState)
    }

    override fun createContent(): View {
        val root = LayoutInflater.from(this).inflate(R.layout.activity_xml_demo, null)
        val event = root.findViewById<TextView>(R.id.xml_event)
        fun report(text: String) {
            event.text = text
        }

        // 1. Links come from the XML data detectors.
        root.findViewById<ZLAttributedTextView>(R.id.xml_attributed).onLinkClickListener =
            ZLLinkListener { _, link -> report("onLinkClick ${link.result?.type} ${link.url}") }

        // 3. Rules are added in code; tokens / highlight come from XML.
        root.findViewById<ZLRichTextView>(R.id.xml_rich).apply {
            addRule("mention", "@\\w+") { ctx ->
                ctx.addStyle(ZLTextStyle().color(M.PURPLE).bold())
                ctx.onTap = ZLRichMatchListener { report("mention ${it.text}") }
            }
            addRule("topic", "#[^#\\s]+#") { ctx ->
                ctx.addStyle(ZLTextStyle().color(M.PURPLE).capsule(M.PURPLE))
                ctx.onTap = ZLRichMatchListener { report("topic ${it.text}") }
            }
            onExpandChangeListener = ZLExpandListener { report("onExpandChanged($it)") }
        }

        // 4. Tag styles from XML, taps in code.
        root.findViewById<ZLTagTextView>(R.id.xml_tag).onTagClickListener = ZLTagListener { report("onTagClick <${it.name}> ${it.text}") }
        root.findViewById<ZLTagTextView>(R.id.xml_any_tags).tagStyles = mapOf(
            "b" to ZLTextStyle().bold(),
            "u" to ZLTextStyle().underline().color(M.PINK),
            "i" to ZLTextStyle().italic(),
        )
        // Delimiters from XML (zl_tagOpenDelimiter / zl_tagCloseDelimiter) and the [ ] subclass.
        root.findViewById<ZLTagTextView>(R.id.xml_delimiter_tags).onTagClickListener = ZLTagListener { report("onTagClick ${it.raw}") }
        root.findViewById<ZLBracketTextView>(R.id.xml_bracket).onTagClickListener = ZLTagListener { report("onTagClick ${it.raw}") }

        // 5. Dynamic text.
        val dynamic = root.findViewById<ZLTagTextView>(R.id.xml_dynamic)
        val input = root.findViewById<EditText>(R.id.xml_input)
        dynamic.text = input.text
        input.doAfterTextChanged { dynamic.text = it?.toString() ?: "" }

        // 6. Expand state synced both ways.
        val expandable = root.findViewById<ZLTagTextView>(R.id.xml_expand)
        val toggle = root.findViewById<MaterialSwitch>(R.id.xml_expand_switch)
        expandable.onExpandChangeListener = ZLExpandListener { expanded ->
            toggle.isChecked = expanded
            report("onExpandChanged($expanded)")
        }
        toggle.setOnCheckedChangeListener { _, checked ->
            // Setting isExpanded does not call onExpandChangeListener: no loop.
            if (expandable.isExpanded != checked) expandable.isExpanded = checked
        }

        // 7. maxLines stepper.
        val lines = root.findViewById<ZLTagTextView>(R.id.xml_lines)
        val value = root.findViewById<TextView>(R.id.xml_lines_value)
        var limit = 2
        fun apply() {
            lines.maxLines = if (limit == 0) Int.MAX_VALUE else limit
            value.text = "maxLines = ${if (limit == 0) "不限" else limit}"
        }
        root.findViewById<Button>(R.id.xml_lines_minus).setOnClickListener {
            if (limit > 0) limit--
            apply()
        }
        root.findViewById<Button>(R.id.xml_lines_plus).setOnClickListener {
            if (limit < 5) limit++
            apply()
        }
        return root
    }
}
