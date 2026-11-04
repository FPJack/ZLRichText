package com.zl.richtext

import android.content.Context
import android.util.AttributeSet

/**
 * A [ZLTagTextView] whose tags are written with `[` `]` (the counterpart of the iOS
 * `ZLBracketLabel`): `[h1]User Agreement[/h1]`, `[h2]Privacy Policy[h2/]`. Everything else
 * (styles, taps, attributes, nesting, self-closing tags, error tolerance) works as in
 * [ZLTagTextView]; `<h1>` is shown as typed.
 *
 * ```kotlin
 * val view = ZLBracketTextView(context)
 * view.tagStyles = mapOf("h1" to ZLTextStyle().color(BLUE).underline())
 * view.onTagClickListener = ZLTagListener { info -> log(info.name, info.text) }
 * view.text = "I agree to the [h1]User Agreement[/h1] and [h2]Privacy Policy[/h2]."
 * ```
 *
 * The delimiters can still be changed with [tagOpenDelimiter] / [tagCloseDelimiter] or the
 * `zl_tagOpenDelimiter` / `zl_tagCloseDelimiter` XML attributes.
 */
public open class ZLBracketTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle,
) : ZLTagTextView(context, attrs, defStyleAttr) {

    override val defaultTagOpenDelimiter: String get() = DEFAULT_OPEN_DELIMITER
    override val defaultTagCloseDelimiter: String get() = DEFAULT_CLOSE_DELIMITER

    public companion object {
        /** Default opening delimiter `[`. */
        public const val DEFAULT_OPEN_DELIMITER: String = "["

        /** Default closing delimiter `]`. */
        public const val DEFAULT_CLOSE_DELIMITER: String = "]"
    }
}
