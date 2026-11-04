package com.zl.richtext.demo

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.zl.richtext.ZLExpandListener
import com.zl.richtext.ZLTagListener
import com.zl.richtext.ZLTagTextView
import com.zl.richtext.rule.ZLRichMatchListener
import com.zl.richtext.style.ZLTextStyle
import com.zl.richtext.style.zlStyled

/**
 * Labels in a RecyclerView (the "TableView" page of the iOS example): view holders are reused,
 * the expand state lives in the model and is restored with `isExpanded` (no callback), heights
 * follow the content.
 */
class ListDemoActivity : DemoActivity() {
    private val log = TapLog()

    /** A post; [expanded] is the state to restore when the holder is reused. */
    private class Post(val id: Int, val text: String, var expanded: Boolean = false)

    private val posts = List(60) { i ->
        val body = "第 ${i + 1} 条动态：".repeat(1) + "这是一段列表中的内容，".repeat(1 + i % 6) +
            "提到了 @user$i 和 #话题${i % 5}#，阅读 <h1>详情</h1>。"
        Post(i, body)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        title = "RecyclerView Demo"
        super.onCreate(savedInstanceState)
    }

    override fun createContent(): View {
        val list = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@ListDemoActivity)
            adapter = PostAdapter()
            setPadding(dp(12f), dp(6f), dp(12f), dp(6f))
            clipToPadding = false
        }
        return vertical(TapInfoPanel(this, log), list, spacing = 0).apply {
            (list.layoutParams as LinearLayout.LayoutParams).apply {
                height = 0
                weight = 1f
            }
        }
    }

    private inner class Holder(val card: CardView, val title: android.widget.TextView, val label: ZLTagTextView) : RecyclerView.ViewHolder(card) {
        var post: Post? = null
    }

    private inner class PostAdapter : RecyclerView.Adapter<Holder>() {
        override fun getItemCount(): Int = posts.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val context = parent.context
            val title = context.label("", sizeSp = 13f, color = M.DEEP_PURPLE, bold = true)
            val label = context.newTagView()
            val card = CardView(context).apply {
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(6f)
                    bottomMargin = dp(6f)
                }
                add(title)
                add(label, dp(6f))
            }
            val holder = Holder(card, title, label)
            // Configured once per holder: the rule objects stay the same, so rendered matches are cached.
            label.apply {
                gravity = Gravity.TOP or Gravity.START
                maxLines = 3
                tagStyle = ZLTextStyle().color(M.BLUE)
                tagHighlightStyle = ZLTextStyle().backgroundColor(0x14000000)
                expandToken = zlStyled("… 全文", ZLTextStyle().color(M.DEEP_ORANGE).bold())
                collapseToken = zlStyled(" 收起", ZLTextStyle().color(M.DEEP_ORANGE).bold())
                onTagClickListener = ZLTagListener { log.add("列表第 ${holder.post?.id?.plus(1)} 行", it) }
                addRule("mention", "@\\w+") { ctx ->
                    ctx.addStyle(ZLTextStyle().color(M.PURPLE))
                    ctx.onTap = ZLRichMatchListener { log.add("列表第 ${holder.post?.id?.plus(1)} 行", ruleInfo(it), "rule:mention") }
                }
                addRule("topic", "#[^#\\s]+#") { ctx -> ctx.addStyle(ZLTextStyle().color(M.PURPLE).capsule(M.PURPLE)) }
                // Saves the state in the model; RecyclerView re-measures the item automatically.
                onExpandChangeListener = ZLExpandListener { expanded ->
                    holder.post?.expanded = expanded
                    log.add("列表第 ${holder.post?.id?.plus(1)} 行", com.zl.richtext.tag.ZLTagInfo(0, "expand", if (expanded) "全文" else "收起", ""), "onExpandChanged($expanded)")
                }
            }
            return holder
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val post = posts[position]
            holder.post = post
            holder.title.text = "#${post.id + 1}"
            // Restoring the state does not call onExpandChangeListener.
            holder.label.isExpanded = post.expanded
            holder.label.text = post.text
        }
    }
}
