# Changelog

## 1.0.0

First release: the Android version of the iOS library ZLRichLabel 2.4.0, built on `AppCompatTextView`.

* `ZLAttributedTextView`: rich text with links, data detectors (link, phone number, email,
  date, address), default / pressed / inactive link styles, a larger link touch area, head /
  middle / tail truncation with rich tokens, `adjustsFontSizeToFit`, highlighted text colour,
  selection with "copy raw text", geometry queries and TalkBack links.
* `ZLRichTextView`: regex rules with priorities and handlers (styles, text replacement, inline
  widgets, taps, long presses, press highlights, sub-links), render cache, expand / collapse
  tokens, long press / double tap, mapping between displayed and raw text.
* `ZLTagTextView`: `<h>` / `<hN>` tags and generic tags with attributes, nesting, self-closing
  tags and error tolerance; styles, highlights and taps per tag name; custom tag delimiters
  (`tagOpenDelimiter` / `tagCloseDelimiter`).
* `ZLBracketTextView`: `ZLTagTextView` with `[` `]` tags by default.
* `ZLTextStyle` chain API with colour resources and sp sizes (dark mode, font scale), capsule
  backgrounds (`ZLTextBackground`) and gradient text drawn per line.
* `ZLRichWidget`: icons, drawables and view snapshots, tinted with the text colour or with
  their own tap.
* XML attributes for the common properties of all views.
* `ZLRichParser`, `ZLTagParser` and `ZLDataDetector` can be used on their own.
