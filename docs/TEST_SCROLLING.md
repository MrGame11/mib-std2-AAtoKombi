# Scrolling / live-progress test branch

This document describes `test/scrolling-live-progress`. The feature choices remain compile-time
settings: edit `jar/src/de/aatokombi/Config.java`, then use the normal PC/Docker build. No runtime
configuration file or Toolbox settings screen is added by this branch.

## Normal build choices

- `SCROLL_MEDIA_TITLE` enables the now-playing title marquee on MQB, PQ and AID.
- `SCROLL_NAV_STREET` enables the next-road marquee on both navigation outputs: native navsd BAP and
  the text fallback in the media page.
- `SCROLL_STEP_MS` is the time between character steps. Values below 200 ms are clamped to 200 ms.
- `SCROLL_EDGE_PAUSE_MS` is the hold time at the first and final window.

The marquee starts only if at least two character units are outside the estimated window. With no hidden
character, or only one, AAtoKombi keeps sending the complete static string and lets the cluster clip
it normally. At the final window it pauses, jumps to the beginning, then pauses before moving again;
it does not reverse direction.

These are not measurements of actual clipping. The cluster does not report visible glyphs through
the text interfaces used here. Two units beyond the estimate may differ from two letters clipped
on the physical screen. See [display research and MQB mono measurement instructions](DISPLAY_METRICS.md).

## Advanced hardware tuning

The window widths are intentionally not normal `Config.java` choices. Cluster fonts are
proportional, and the same character count can occupy very different pixel widths. Advanced users
can still change the constants in these readers before rebuilding:

- `ShmemMediaReader`: `TITLE_CHARS_MQB` and `TITLE_CHARS_PQ` (AID uses MQB).
- `AANavReader`: `STREET_CHARS_MQB_NAV`, `STREET_CHARS_MQB_MEDIA`, and `STREET_CHARS_PQ`.
- `TextScroller`: `MIN_HIDDEN_CHARS` controls the two-hidden-character activation threshold.

The current estimates are MQB/AID title 18, PQ title 20, MQB native-navigation street 24,
MQB media-navigation street 28, and PQ street 20 character units. Mono and colour MQB displays
are not identified separately. Do not treat these values as validated across those displays.

Scrolling also respects the target firmware's UTF-8 transport budgets: media 72 bytes on MQB and
48 on PQ; native `TurnToInfo` street 75 bytes on MQB and 74 on PQ. These are payload limits after
the length prefix, not pixel widths. Windows may therefore contain fewer units for multibyte text.
Surrogate pairs and BMP combining marks remain together; this is not full Unicode grapheme shaping
and does not add missing glyphs to the cluster font. A combining unit larger than the entire BAP
budget falls back to the stock static formatter.

The native navigation ticker re-sends only `TurnToInfo`; it does not repeat the maneuver, distance,
lane or destination functions. In the media fallback, street scrolling pauses while the lane row
temporarily occupies the street line.

## Live streams and long-form audio

With `SHOW_MEDIA_PROGRESS` enabled, a missing or invalid duration renders as `LIVE`. Exact `:` and
`-` metadata placeholders are treated as empty. Finite media uses `m:ss` below one hour and
`h:mm:ss` from one hour up. The twelve-character progress bar shrinks as the two time strings grow;
if there is no useful room left, only elapsed/total is sent.

`LIVE` is a display fallback for unknown duration, not proof that the source is an internet radio
stream. A finite track whose app omits duration will receive the same label. Missing song/artist
metadata cannot be reconstructed by this option. On PQ, progress or `LIVE` occupies the album row.

The Android Auto `Playlist` metadata field is not captured in this test branch. It is app-defined
context (playlist, queue, station/category, or empty), not a reliable source for song and artist.

## Compatibility and build

One JAR contains the MQB and PQ paths and selects the active stack at runtime. AID follows the MQB
audio path; album-cover behaviour is unchanged. Build both artifacts against the target unit's
files as usual:

```sh
./build.sh /path/to/MIBHMI.jxe /path/to/libext.google.gal.receiver.so
```

The outputs are `dist/AAtoKombi.jar` and the firmware-specific
`dist/libext.google.gal.receiver.so`.

## Review corrections (2026-10-28)

Compared with the earlier `a442887` test build:

- Keep surrogate pairs and combining accents together, including when counting the threshold.
- Bound scrolling windows by UTF-8 payload size and continue far enough to show the final text.
- Restart a title on artist/album changes, even when its title text is identical; re-check the PQ
  layout on fresh metadata/position frames after initialisation.
- Avoid sending a scroll refresh when successive windows contain identical text.
- Prevent signed integer overflow in very large navigation destination times/distances (a pre-existing
  issue, reproduced by new boundary tests).
- Correct the descriptions of estimated widths and the test branch's progress default.

The review includes a full JAR build against the supplied HMI, text/progress/scrolling tests,
navigation mapping tests, and shim parser/fuzz tests. It does not exercise the real HMI timer,
BAP bus, cluster renderer, disconnect/reconnect sequence in the vehicle, or other firmware versions.
The existing limited structural compatibility checker is not a complete firmware compatibility audit.
