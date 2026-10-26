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

The marquee starts only if at least two characters are outside the measured window. With no hidden
character, or only one, AAtoKombi keeps sending the complete static string and lets the cluster clip
it normally. At the final window it pauses, jumps to the beginning, then pauses before moving again;
it does not reverse direction.

## Advanced hardware tuning

The window widths are intentionally not normal `Config.java` choices. Cluster fonts are
proportional, and the same character count can occupy very different pixel widths. Advanced users
can still change the constants in these readers before rebuilding:

- `ShmemMediaReader`: `TITLE_CHARS_MQB` and `TITLE_CHARS_PQ` (AID uses MQB).
- `AANavReader`: `STREET_CHARS_MQB_NAV`, `STREET_CHARS_MQB_MEDIA`, and `STREET_CHARS_PQ`.
- `TextScroller`: `MIN_HIDDEN_CHARS` controls the two-hidden-character activation threshold.

The native navigation ticker re-sends only `TurnToInfo`; it does not repeat the maneuver, distance,
lane or destination functions. In the media fallback, street scrolling pauses while the lane row
temporarily occupies the street line.

## Live streams and long-form audio

With `SHOW_MEDIA_PROGRESS` enabled, a missing or invalid duration renders as `LIVE`. Exact `:` and
`-` metadata placeholders are treated as empty. Finite media uses `m:ss` below one hour and
`h:mm:ss` from one hour up. The twelve-character progress bar shrinks as the two time strings grow;
if there is no useful room left, only elapsed/total is sent.

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
