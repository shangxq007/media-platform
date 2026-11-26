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

## Authoring policy and historical compatibility

The installed profile IDs above are immutable. Application authoring settings
`media.subtitle-fonts.available`, `media.subtitle-fonts.default` and
`media.subtitle-fonts.by-language` control new selections. The first is a
comma-separated list of installed IDs. The last is a comma-separated list of
exact BCP-47 language-to-ID assignments such as
`zh-Hans=noto-serif-sc-color-emoji-v1,zh-Hant=noto-sc-color-emoji-v1`.
Matching is case-insensitive but preserves script/region distinctions. Both
defaults and overrides must name a selectable ID. The test-fixture identity
`n18-fixture-serif-v1` resolves to the same installed Serif resource but is
absent from production's default selectable list.

For the N18 editor's single implicit subtitle track, canonical Timeline
metadata `subtitleTrack.language` is optional. The existing initialization
Operation accepts it as `subtitleTrackLanguage` and commits it in the
revision. Without a declaration or matching override, a new defaulted cue
uses the configured global font. Browser locale does not participate.
Omitting the font on an existing cue preserves its pin. A historical cue
without a pin uses N17's Sans, independently of current authoring settings.

The authenticated project font catalog lists selectable profiles. Operation
Preview returns the resolved font used to draw its picture. The confirmed
body retains the caller's exact explicit or default intent, and the existing
plan digest binds the resolved font. A changed policy can require a fresh
preview. An already-completed matching command is reconstructed from the
existing scoped durable result and checked by the existing fingerprint
before replay. No new command record or revision is written on replay.
