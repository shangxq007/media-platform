# Whole-cue font profiles

- **noto-sc-color-emoji-v1** is the original pinned MP Cue Sans and Noto Color Emoji
  design. A missing profile resolves to this ID. The original assets are unchanged.
- **noto-serif-sc-color-emoji-v1** uses MP Cue Serif for supported graphemes. MP Cue Serif
  Regular is a renamed OFL 1.1 subset of Noto Serif CJK SC Regular from
  `notofonts/noto-cjk@f8d157532fbfaeda587e826d4cd5b21a49186f7c`,
  `Serif/OTF/SimplifiedChinese/NotoSerifCJKsc-Regular.otf`. See
  `OFL-NotoSerifCJK.txt` and `SOURCES.sha256` for the license and exact identities.
  The subset retains Latin, combining marks, general/CJK punctuation, basic unified
  ideographs and full-width forms. It retains the original outlines and supporting
  layout tables for those characters.

Both profiles try the pinned color-emoji strike for supported emoji graphemes first
(including 🌻, except when text presentation is requested). Otherwise the complete
grapheme uses the profile face or the pinned MP Cue Sans fallback. A sequence not
covered by either is rejected with a code-point diagnostic. System fonts are never used.
Logical layout remains in the 320-unit space. A profile change can reflow because
the faces have different glyph metrics. Raster size does not change line breaks.

Upstream face and license paths are under
https://github.com/notofonts/noto-cjk/tree/f8d157532fbfaeda587e826d4cd5b21a49186f7c/Serif
