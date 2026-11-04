# ZLRichText

ZLRichText 是 iOS 富文本库 [ZLRichLabel](https://github.com/FPJack/ZLRichLabel) 的 Android 版本，基于 `AppCompatTextView` 实现，Kotlin 编写，Java 也可以直接调用。

- 正则规则：多条规则按优先级处理重叠，在 handler 里设置样式、替换文字、插入图标，并设置点击、长按和按下高亮
- 标签高亮：`<h1>用户协议</h1>`、`<h2>隐私政策<h2/>`，也能识别任意标签名，支持属性、嵌套、自闭合标签和容错；标签符号可以换成 `[ ]`、`【 】`、`{{ }}` 等
- 链接：自动识别网址、电话、邮箱、日期、地址；有默认、按下、失活三种样式；支持扩大点击热区
- 截断：尾部、头部、中间截断，截断符可以是富文本；支持「… 全文」/「收起」展开收起，展开状态可以保存在列表数据里
- 字号自适应：放不下时所有字号按同一比例缩小，最小到 `minimumScaleFactor`
- 行内 Widget：图标、图片、View 截图，可以跟随文字颜色着色，也可以有自己的点击
- 胶囊背景（圆角、边框、内边距）和渐变文字，跨行时逐行绘制，支持 RTL
- 长按、双击，选中复制时可以「复制原文」（展示的文字和原始字符串一一对应）
- 深色模式和系统字号：颜色可以用资源（`colorRes`），字号可以用 sp，配置变化时自动重新渲染
- 渲染缓存：只有变化的规则会重新执行 handler，适合 RecyclerView
- TalkBack：每个链接都可以单独聚焦、双击触发
- 常用属性都能在 XML 里配置

最低支持 Android 6.0（API 23）。

## 目录

- [安装](#安装)
- [快速开始](#快速开始)
- [组件](#组件)
- [ZLAttributedTextView：富文本与链接](#zlattributedtextview富文本与链接)
- [ZLRichTextView：正则规则](#zlrichtextview正则规则)
- [ZLTagTextView：标签](#zltagtextview标签)
- [展开 / 收起](#展开--收起)
- [ZLTextStyle：链式样式](#zltextstyle链式样式)
- [行内 Widget](#行内-widget)
- [手势、选中与复制](#手势选中与复制)
- [在 RecyclerView 中使用](#在-recyclerview-中使用)
- [XML 属性](#xml-属性)
- [与 iOS 版的对应关系](#与-ios-版的对应关系)
- [没有实现的功能](#没有实现的功能)
- [Demo](#demo)
- [工程结构](#工程结构)

## 安装

库还没有发布到 Maven Central 时，有两种用法：

**作为模块引入**：把 `zlrichtext` 目录拷到工程里。

```kotlin
// settings.gradle.kts
include(":zlrichtext")

// app/build.gradle.kts
dependencies {
    implementation(project(":zlrichtext"))
}
```

**发布到本地 Maven**：

```bash
./gradlew :zlrichtext:publishToMavenLocal
```

```kotlin
// settings.gradle.kts → dependencyResolutionManagement.repositories
mavenLocal()

// app/build.gradle.kts
implementation("io.github.fpjack:zlrichtext:1.0.0")
```

库依赖 `androidx.appcompat`。库没有使用反射，不需要额外的混淆规则。

## 快速开始

XML：

```xml
<com.zl.richtext.ZLTagTextView
    android:id="@+id/agreement"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:text="我已阅读并同意&lt;h1&gt;《用户协议》&lt;/h1&gt;和&lt;h2&gt;《隐私政策》&lt;/h2&gt;"
    android:textSize="15sp"
    app:zl_tagColor="#2196F3"
    app:zl_highlightBackgroundColor="#1F000000" />
```

代码：

```kotlin
val view = findViewById<ZLTagTextView>(R.id.agreement)
view.tagStyles = mapOf("h2" to ZLTextStyle().color(Color.RED).underline())
view.setTagClickListener("h1") { openAgreement() }
view.onTagClickListener = ZLTagListener { info -> log(info.name, info.text) }
```

正则规则：

```kotlin
val view = ZLRichTextView(context)
view.style = ZLTextStyle().textSizeSp(15f).color(Color.BLACK)
view.addRule("mention", "@\\w+") { ctx ->
    ctx.addStyle(ZLTextStyle().color(PURPLE).bold())
    ctx.onTap = ZLRichMatchListener { match -> openUser(match.text) }
}
view.addRule("topic", "#[^#\\s]+#") { ctx ->
    ctx.addStyle(ZLTextStyle().color(PURPLE).capsule(PURPLE))
}
view.text = "@alice 发布了 #新话题#"
```

## 组件

```
AppCompatTextView
└── ZLAttributedTextView   富文本、链接、自动识别、截断、字号自适应、选中复制
    └── ZLRichTextView     正则规则、展开 / 收起、长按 / 双击、原文映射
        └── ZLTagTextView  标签：<h1>…</h1> 与通用标签
            └── ZLBracketTextView  默认用 [ ] 包裹标签
```

| 类 | 说明 |
| --- | --- |
| `ZLAttributedTextView` | 显示富文本（`CharSequence`），处理链接、截断和选中 |
| `ZLRichTextView` | 用正则规则把原始字符串渲染成富文本 |
| `ZLTagTextView` | 内置标签规则，可以和其他规则一起用 |
| `ZLBracketTextView` | `ZLTagTextView` 子类，标签默认写成 `[h1]…[/h1]` |
| `ZLTextStyle` | 不可变的样式，链式配置，所有接收样式的地方都用它 |
| `ZLRichWidget` | 行内图标、图片或 View 截图 |
| `ZLTextBuilder` / `buildZLText {}` | 拼接富文本 |
| `ZLRichRule` / `ZLRichParser` | 规则与解析，可以单独使用 |
| `ZLTagParser` | 标签解析，可以单独使用 |
| `ZLDataDetector` | 识别网址、电话、邮箱、日期、地址，可以单独使用 |

这些 View 都是普通的 TextView：`android:textSize`、`textColor`、`maxLines`、`gravity`、`padding`、`lineSpacingExtra`、`textIsSelectable`、`textColorHighlight` 等属性照常使用。

修改属性后不会立刻重新排版：所有 setter 只标记变化，下一次 measure 时统一处理一次。所以连续设置多个属性不需要额外的批量更新接口。

## ZLAttributedTextView：富文本与链接

```kotlin
val view = ZLAttributedTextView(context)
view.text = buildZLText(context) {
    append("访问 ")
    append("官网", ZLTextStyle().link("https://example.com"))
    append("，或者")
    append("联系客服", ZLTextStyle().color(ORANGE))
}
// 给一段文字加链接
view.addLink(start, end) { _, link -> openService() }
view.addLink("https://github.com", start, end)
// 自定义样式的链接
view.addLink(ZLLink(start, end,
    style = ZLTextStyle().color(Color.WHITE).backgroundColor(ORANGE),
    activeStyle = ZLTextStyle().backgroundColor(BROWN),
    onClick = { _, link -> /* … */ }))

view.dataDetectorTypes = setOf(ZLDataDetectorType.LINK, ZLDataDetectorType.PHONE_NUMBER)
view.onLinkClickListener = ZLLinkListener { _, link ->
    link.url            // 网址 / ZLTextStyle.link
    link.result         // 自动识别的结果：type、url、phoneNumber、date
}
view.onLinkLongClickListener = ZLLinkListener { _, link -> /* … */ }
```

| 属性 | 说明 |
| --- | --- |
| `linkStyle` / `activeLinkStyle` / `inactiveLinkStyle` | 链接默认、按下时、窗口失去焦点（弹出 dialog）时的样式 |
| `dataDetectorTypes` | 自动识别的类型 |
| `extendsLinkTouchArea` | 手指没按在文字上时，向四周最多 15dp 查找链接 |
| `maxLines` / `truncationMode` / `truncationToken` | 行数、截断位置（多行只截断最后一行的尾部）、截断符（可以是富文本） |
| `adjustsFontSizeToFit` / `minimumScaleFactor` | 放不下时整体缩小字号 |
| `isTextHighlighted` / `highlightedTextColor` | 全部文字使用高亮颜色（例如列表项选中时） |
| `isTruncated` / `fullLineCount` / `currentFontScale` | 排版结果 |
| `characterIndexAt(x, y)` / `rectsForRange` / `boundingRectForRange` / `linkAt(x, y)` | 位置查询 |
| `statistics` | 渲染、排版次数统计，用来观察缓存 |

`android:ellipsize` 的 `start` / `middle` / `end` 分别对应 `HEAD` / `MIDDLE` / `TAIL`。

## ZLRichTextView：正则规则

```kotlin
view.addRule("mention", "@(?<name>\\w+)", priority = 10) { ctx ->
    ctx.group("name")                    // 命名分组，API 23 也可用
    ctx.addStyle(ZLTextStyle().color(PURPLE))
    ctx.highlightStyle = ZLTextStyle().backgroundColor(0x33000000)
    ctx.onTap = ZLRichMatchListener { match -> /* … */ }
    ctx.onLongPress = ZLRichMatchListener { match -> /* … */ }
}
view.addRule("emoji", "\\[(smile|cry)\\]") { ctx ->
    ctx.setWidget(ZLRichWidget.icon(context, R.drawable.ic_smile, sizeDp = 18f))
}
view.addRule("code", "`([^`]+)`") { ctx ->
    ctx.setText(ctx.group(1) ?: "", ZLTextStyle().monospace().codeBackground())
}
view.rawText = "@alice [smile] `code`"   // 和 view.text = … 相同
```

handler 里通过 `ZLRichRuleContext` 修改这一段的内容：

| 方法 | 说明 |
| --- | --- |
| `addStyle(style, start, end)` | 给内容（或其中一部分）加样式 |
| `setText(text, style)` | 替换显示的文字 |
| `setWidget(widget)` / `widgetText(widget)` | 替换为 Widget，或者生成一段 Widget 文字用于拼接 |
| `styled(text, style)` | 生成带样式的文字用于拼接 |
| `onTap` / `onLongPress` / `highlightStyle` | 整段的点击、长按和按下高亮 |
| `addLink(start, end, highlightStyle, onTap, onLongPress)` | 内容中的一部分单独响应点击 |
| `group(index)` / `group(name)` / `matchedText` | 匹配结果 |

规则说明：

- **优先级**：重叠时优先级高的规则保留，低的整段放弃。优先级相同时先出现的保留，起点相同时长的保留，再相同时先添加的保留。
- **缓存**：每个匹配的渲染结果按「规则 + 位置 + 文字」缓存。只修改按下高亮等配置时不会重新执行 handler。`setNeedsRebuild(ruleName)` 可以让某条规则重新渲染。
- **原文**：`segments`、`matches`、`matchAt(x, y)`、`rawTextForRange(range)` 可以把展示的文字对应回原始字符串。
- **单独使用**：`ZLRichParser.segments(source, rules)` 可以脱离 View 使用。

## ZLTagTextView：标签

```kotlin
view.tagStyle = ZLTextStyle().color(BLUE)                       // 所有标签
view.tagStyles = mapOf("h2" to ZLTextStyle().color(RED))         // 按标签名
view.tagStyleBuilder = ZLTagStyleBuilder { info -> /* 单个标签 */ null }
view.tagHighlightStyles = mapOf("h1" to ZLTextStyle().backgroundColor(0x33000000))
view.setTagClickListener("h1") { info -> openAgreement() }
view.onTagClickListener = ZLTagListener { info -> /* 没有单独处理的标签 */ }
view.onTagLongClickListener = ZLTagListener { info -> /* … */ }
view.onTagsParsedListener = ZLTagParsedListener { tags -> /* 只在标签变化时回调 */ }
view.tagWidgetBuilder = ZLTagWidgetBuilder { info, style -> /* 返回 ZLRichWidget 替换标签 */ null }
view.tagSpanBuilder = ZLTagSpanBuilder { info, style, ctx -> /* 完全自定义内容 */ null }
```

`ZLTagInfo` 包含以下信息：

| 字段 | 说明 |
| --- | --- |
| `index` | 在所有标签中的序号 |
| `name` | 标签名，小写 |
| `level` | `<h1>` 为 1 |
| `text` | 标签内的纯文本 |
| `raw` | 整个原始标签 |
| `attributes` / `attribute()` / `colorAttribute()` | 标签属性 |
| `parent` / `depth` | 外层标签与嵌套深度 |

**写法与容错**

- 闭合可以写成 `</h1>` 或 `<h1/>`，标签名不区分大小写。
- 开闭标签名不一致或标签没有闭合时，原样显示，不影响后面的标签。

**通用标签**：可以识别任意标签名，并支持属性、嵌套和自闭合标签。

```kotlin
view.tagConfiguration = ZLTagParserConfiguration.ANY_TAGS
view.text = "<b>粗体 <color value=\"#2196F3\">蓝色</color></b> <a href=\"https://x.com\">链接</a><br/>"
```

内层标签继承外层的样式。点击时内层优先。

**自定义标签符号**：其他规则完全不变。

```kotlin
view.tagOpenDelimiter = "【"       // 【h1】用户协议【/h1】
view.tagCloseDelimiter = "】"
// "[" "]"、"{" "}"、"{{" "}}"（多个字符）都可以；空字符串按 "<" ">" 处理
```

- 换了符号以后，`<h1>` 不再被识别，原样显示。
- 属性名和不带引号的属性值里不能包含符号字符，需要时用引号包起来。
- 固定用 `[ ]` 时可以直接用 `ZLBracketTextView`。

**单独解析**：

```kotlin
ZLTagParser.tags(source)                                       // <h> 标签
ZLTagParser.tags(source, ZLTagParserConfiguration.ANY_TAGS)    // 通用标签
ZLTagParser.tags(source, "h\\d*", false, false, "[", "]")      // 自定义符号（Java 友好）
```

## 展开 / 收起

```kotlin
view.maxLines = 3
view.expandToken = context.zlStyled("… 全文", ZLTextStyle().color(ORANGE).bold())
view.collapseToken = context.zlStyled(" 收起", ZLTextStyle().color(ORANGE).bold())
view.collapseThreshold = 5        // 不超过 5 行时完整显示，超过才收起到 maxLines 行
view.onExpandChangeListener = ZLExpandListener { expanded -> post.expanded = expanded }
// 自己处理点击（例如跳转详情页）；设置后不会自动展开，需要时调用 v.toggleExpanded()
view.onExpandTokenClickListener = ZLExpandTokenClickListener { v, wantsExpand -> openDetail() }
view.isExpanded = true            // 代码设置不会回调 onExpandChangeListener
```

## ZLTextStyle：链式样式

`ZLTextStyle` 是不可变的 data class，每个链式方法都返回新对象，可以放心共用。

```kotlin
ZLTextStyle().textSizeSp(15f).colorRes(R.color.text).bold()
    .underline().strikethrough().italic().kern(0.1f).baselineShift(2f)
    .shadow(0x4D000000, radiusDp = 2f, dyDp = 1f)
    .backgroundColor(Color.YELLOW).link("https://…")
    .capsule(PURPLE)                       // 胶囊背景
    .codeBackground()                      // 行内代码背景
    .gradientColors(intArrayOf(PINK, ORANGE))
    .indent(firstLineDp = 16f)
    .alignment(Layout.Alignment.ALIGN_CENTER)
```

- 合并用 `a + b` 或 `a.merge(b)`，`b` 中设置的字段覆盖 `a`。
- 颜色可以是 `ZLColor.of(int)`，也可以是 `ZLColor.res(id)`。资源颜色在渲染时解析，所以会跟随深色模式变化。
- 字号用 `ZLDimension.sp/dp/px`，sp 会跟随系统字号。

胶囊背景的完整配置：

```kotlin
ZLTextBackground(
    fillColor = ZLColor.of(0x1A9C27B0), strokeColor = ZLColor.of(PURPLE), strokeWidth = ZLDimension.dp(1f),
    cornerRadius = ZLDimension.dp(4f), paddingHorizontal = ZLDimension.dp(6f), paddingVertical = ZLDimension.dp(1f),
)
```

## 行内 Widget

```kotlin
ZLRichWidget.icon(context, R.drawable.ic_star, sizeDp = 16f)       // 默认跟随文字颜色着色
ZLRichWidget.drawable(drawable, widthPx, heightPx, ZLWidgetAlignment.BASELINE)
ZLRichWidget.view(badgeView)                                       // 把 View 渲染成图片
widget.copy(marginStart = 8, onClick = Runnable { toggle() })      // 间距单位为 px；自己处理点击
```

- 没有设置 `onClick` 的 Widget 属于所在的规则或标签，点击时触发它们的点击。
- 设置了 `onClick` 的 Widget 自己处理点击，例如复选框、关注按钮。状态变化后重新设置文字即可，文字会自动重新排版。

## 手势、选中与复制

```kotlin
view.onLongPressListener = ZLGestureListener { info ->
    info.characterIndex; info.match; info.matchDisplayText; info.rawText
}
view.onDoubleTapListener = ZLGestureListener { info -> /* … */ }

view.setTextIsSelectable(true)              // 系统选中，菜单里多一个「复制原文」
view.copyRawMenuTitle = "复制原文"
view.onCopyListener = ZLCopyListener { text, isRaw -> /* … */ }
view.onSelectionChangeListener = ZLSelectionListener { range -> /* … */ }
view.selectedText; view.selectedRawText
view.select(0, 5); view.selectAllContent(); view.copySelection(raw = true)
```

「复制原文」复制的是原始字符串。例如规则把 `@alice` 显示成「Alice」，复制得到的仍然是 `@alice`。

## 在 RecyclerView 中使用

```kotlin
override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
    val label = ZLTagTextView(parent.context).apply {
        // 规则、样式、监听只在创建 ViewHolder 时设置一次，规则对象不变时渲染结果可以复用
        addRule("mention", "@\\w+") { ctx -> /* … */ }
        onExpandChangeListener = ZLExpandListener { expanded -> holder.post?.expanded = expanded }
    }
    …
}

override fun onBindViewHolder(holder: Holder, position: Int) {
    holder.label.isExpanded = posts[position].expanded   // 恢复状态，不会触发回调
    holder.label.text = posts[position].text
}
```

## XML 属性

`ZLAttributedTextView`（所有子类都可以使用）：

| 属性 | 类型 | 说明 |
| --- | --- | --- |
| `zl_linkColor` | color | 链接颜色 |
| `zl_linkUnderline` | boolean | 链接下划线，默认 true |
| `zl_activeLinkColor` / `zl_activeLinkBackgroundColor` | color | 按下时链接的文字颜色 / 背景颜色 |
| `zl_inactiveLinkColor` | color | 窗口失去焦点时链接的颜色 |
| `zl_dataDetectorTypes` | flags | `none` `link` `phoneNumber` `email` `date` `address` `all` |
| `zl_extendsLinkTouchArea` | boolean | 扩大链接点击热区 |
| `zl_truncationMode` | enum | `tail` `head` `middle` |
| `zl_truncationToken` / `zl_truncationTokenColor` | string / color | 截断符及其颜色 |
| `zl_adjustsFontSizeToFit` / `zl_minimumScaleFactor` | boolean / float | 字号自适应 |
| `zl_highlighted` / `zl_highlightedTextColor` | boolean / color | 全部文字高亮 |

`ZLRichTextView`：

| 属性 | 类型 | 说明 |
| --- | --- | --- |
| `zl_highlightTextColor` / `zl_highlightBackgroundColor` | color | 按下高亮 |
| `zl_expandToken` / `zl_collapseToken` / `zl_expandTokenColor` | string / string / color | 展开、收起按钮 |
| `zl_collapseThreshold` | integer | 超过多少行才收起 |
| `zl_expanded` | boolean | 初始展开状态 |
| `zl_copyRawMenuTitle` | string | 「复制原文」菜单标题 |

`ZLTagTextView` / `ZLBracketTextView`：

| 属性 | 类型 | 说明 |
| --- | --- | --- |
| `zl_tagColor` / `zl_tagBackgroundColor` | color | 所有标签的颜色 / 背景 |
| `zl_tagUnderline` / `zl_tagBold` | boolean | 下划线 / 加粗 |
| `zl_tagTextSize` | dimension | 标签字号 |
| `zl_tagNamePattern` | string | 识别的标签名（正则），默认 `h\d*` |
| `zl_anyTags` | boolean | 任意标签名 + 嵌套 + 自闭合 |
| `zl_allowsNestedTags` / `zl_allowsSelfClosingTags` | boolean | 嵌套 / 自闭合 |
| `zl_tagOpenDelimiter` / `zl_tagCloseDelimiter` | string | 标签符号，例如 `【` `】`；`<` 在 XML 中写成 `&lt;` |
| `zl_tagPriority` | integer | 标签规则的优先级 |
| `zl_parsesTags` | boolean | 设为 false 时关闭标签解析 |

```xml
<com.zl.richtext.ZLRichTextView
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:maxLines="3"
    android:lineSpacingExtra="4dp"
    app:zl_expandToken="… 全文"
    app:zl_collapseToken=" 收起"
    app:zl_expandTokenColor="#FF5722"
    app:zl_highlightBackgroundColor="#33FF9800"
    app:zl_dataDetectorTypes="link|phoneNumber" />
```

## 与 iOS 版的对应关系

| iOS（ZLRichLabel） | Android（ZLRichText） |
| --- | --- |
| `ZLAttributedLabel` | `ZLAttributedTextView` |
| `ZLRichLabel` | `ZLRichTextView` |
| `ZLTagRichLabel` | `ZLTagTextView` |
| `ZLBracketLabel` | `ZLBracketTextView` |
| `ZLRichText`（SwiftUI） | XML 布局 / Compose 中用 `AndroidView` |
| `numberOfLines` | `maxLines` / `android:maxLines` |
| `lineBreakMode` | `truncationMode` / `android:ellipsize` |
| `attributedTruncationToken` | `truncationToken` |
| `enabledTextCheckingTypes` | `dataDetectorTypes` |
| `linkAttributes` / `activeLinkAttributes` / `inactiveLinkAttributes` | `linkStyle` / `activeLinkStyle` / `inactiveLinkStyle` |
| `textInsets` | `padding` |
| `verticalAlignment` | `gravity` |
| `label.update { }` / `beginUpdates()` | 不需要：setter 会合并到下一次 measure |
| `onTagTap` / `setTapHandler(_:forTag:)` / `tagTapHandlers` | `onTagClickListener` / `setTagClickListener` / `setTagClickListeners` |
| `onTagLongPress` / `onParsed` | `onTagLongClickListener` / `onTagsParsedListener` |
| `tagOpenDelimiter` / `tagCloseDelimiter` | 同名属性，以及 XML `zl_tagOpenDelimiter` / `zl_tagCloseDelimiter` |
| `onExpandChanged` / `onExpandTokenTap` | `onExpandChangeListener` / `onExpandTokenClickListener` |
| `onLongPress` / `onDoubleTap` | `onLongPressListener` / `onDoubleTapListener` |
| `isSelectable` / `onCopy` / `onSelectionChanged` | `textIsSelectable` / `onCopyListener` / `onSelectionChangeListener` |
| `NSTextAttachment` / 行内 UIView | `ZLRichWidget`（Drawable，或 View 截图） |
| `ZLTextStyle` 链式（`UIFont` / `UIColor`） | `ZLTextStyle` 链式（sp / dp、`@ColorInt` / `@ColorRes`） |
| `currentFontScaleFactor` | `currentFontScale` |

## 没有实现的功能

以下功能在 TextView 上实现成本高或没有对应能力，所以没有实现：

| 功能 | 说明 |
| --- | --- |
| 可交互的子 View | TextView 不能包含子 View。`ZLRichWidget.view()` 会把 View 渲染成图片，点击可以通过 Widget 的 `onClick` 处理，状态变化后重新设置文字 |
| 下划线 / 删除线单独设置颜色、双删除线 | Android 的 span 不支持，下划线和删除线的颜色跟随文字颜色 |
| 按段设置行距、段间距、`headIndent` / `tailIndent` | 行距用 TextView 的 `lineSpacingExtra` / `lineSpacingMultiplier` 统一设置；缩进只支持 `indent(firstLine, restLines)`（`LeadingMarginSpan`） |
| 链接的无障碍提示（accessibilityHint） | TalkBack 可以单独聚焦每个链接并双击触发，但没有单独的提示文字 |

## Demo

`demo` 模块包含 iOS 示例工程中的所有用例：

| 页面 | 内容 |
| --- | --- |
| 主页 | 用例 1 – 33：标签样式与点击、容错、正则规则、优先级、Widget、RTL、截断、展开收起、深色模式与系统字号、性能统计、长按与双击、选中复制、通用标签、胶囊背景与渐变、内边距、Widget 跟随尺寸、链式样式、自定义标签符号、`ZLBracketTextView` |
| RecyclerView | 60 条动态，展开状态保存在数据中，复用时恢复 |
| XML 配置 | 只用 XML 配置的各种用法、动态文字、展开状态双向同步、maxLines 步进器 |
| Base | `ZLAttributedTextView` 的 14 个用例：样式、链接、自动识别、截断、热区、失活样式、字号自适应、Widget 等 |

页面顶部会显示最近一次点击得到的完整信息，底部有点击历史。

```bash
./gradlew :demo:installDebug
```

## 工程结构

```
zlrichtext/src/main/java/com/zl/richtext/
├── ZLAttributedTextView.kt   富文本、链接、截断、字号自适应、选中复制、几何查询
├── ZLRichTextView.kt         正则规则、渲染缓存、展开 / 收起、手势、原文映射
├── ZLTagTextView.kt          标签规则与按标签名的配置
├── ZLBracketTextView.kt      [ ] 标签
├── ZLTypes.kt                ZLLink、ZLGestureInfo、ZLStatistics 等
├── rule/                     ZLPattern、ZLRichRule、ZLRichParser、ZLRichRuleContext
├── tag/                      ZLTagParser、ZLTagInfo、ZLTagMarkupPattern
├── detect/                   ZLDataDetector
├── style/                    ZLTextStyle、ZLRichWidget、ZLTextBuilder、颜色与尺寸
└── internal/                 Span、展示文字构建（截断 / 内边距映射）、装饰绘制
```

测试：

```bash
./gradlew :zlrichtext:testDebugUnitTest               # 解析器、标签、自动识别（JVM）
./gradlew :zlrichtext:connectedDebugAndroidTest       # View 测试（需要设备或模拟器）
```

构建环境（都在工程里固定，不需要另外配置）：

| 项目 | 版本 | 位置 |
| --- | --- | --- |
| Gradle | 9.1.0 | `gradle/wrapper/gradle-wrapper.properties` |
| Gradle 守护进程 JDK | 17 | `gradle/gradle-daemon-jvm.properties`（本机没有 JDK 17 时由 foojay 插件自动下载） |
| AGP / Kotlin | 8.13.1 / 2.2.20 | `gradle/libs.versions.toml` |
| 编译目标 | Java 17（`jvmToolchain(17)`），compileSdk 36，minSdk 23 | 各模块 `build.gradle.kts` |

Android Studio 打开工程后，Settings › Build, Execution, Deployment › Build Tools › Gradle 里的 Gradle JDK 选「Daemon JVM criteria」即可（新版本会自动选上）。

## License

MIT，见 [LICENSE](LICENSE)。
