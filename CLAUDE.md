# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Single-module Android app (Java, AGP 8.7.3, `compileSdk 35`, `minSdk 26`, namespace/applicationId `com.pluskey.remapper`) that maps the OnePlus 15 "Plus Key" to flashlight / ringer / media-play actions. Detection is done by parsing `logcat` — intentionally **not** via an accessibility service, input-remapping API, or root-only event device. See `README.md` for user-facing setup.

## Build & run

```bash
./gradlew :app:assembleDebug        # build APK
./gradlew :app:installDebug         # install onto connected device
./gradlew :app:unitTestClasses      # compile unit test sources
./gradlew :app:androidTestClasses   # compile instrumentation test sources
```

No test sources exist yet; the `unitTestClasses` / `androidTestClasses` tasks in `app/build.gradle` are scaffolding that compile whatever lands in `src/test` or `src/androidTest`. There is no lint/format config — `./gradlew :app:lint` runs the stock Android lint.

After install, `READ_LOGS` must be granted manually (Android does not prompt for it):

```bash
adb shell pm grant com.pluskey.remapper android.permission.READ_LOGS
```

## Architecture

Four classes, all in `app/src/main/java/com/pluskey/remapper/`:

- **`MainActivity`** — launcher. On `onResume()` checks `READ_LOGS`; if granted, starts `PlusKeyMonitorService` as a foreground service and requests `POST_NOTIFICATIONS`. If not granted, shows the adb setup guide.
- **`SettingsActivity`** — edits per-press-type action mapping and the long-press threshold.
- **`Theme`** — static design tokens (color constants, typography helpers like `display`/`title`/`body`/`eyebrow`/`caption`/`pill`, and drawable factories `roundedFill`/`roundedStroke`/`rippleOn`/`rippleTransparent`). Both activities build their UI through it.
- **`PlusKeyMonitorService`** — foreground `Service` (`foregroundServiceType="specialUse"`, subtype `plus_key_logcat_monitor`) that owns all runtime logic. It contains three inner types that together form the detection pipeline:
  1. `LogcatReader` (a `Thread`) — runs `logcat -v time -T 1` in a `ProcessBuilder` and line-matches against the `Pattern` constants. **All regex lives here.** The key ones: `TRUSTED_SOURCE_PATTERN` (restricts to `KEYLOG_PhoneWindowManagerExtImpl`'s `overrideInterceptKeyBeforeQueueing` line — this is how false positives from other components are avoided), `PLUS_KEY_PATTERN` (requires both `KEYCODE_ACTION_BUTTON_CLICK` and `scanCode=735`), and `DOWN_PATTERN` / `UP_PATTERN` / `LONG_PATTERN`. Extracts `eventTime` / `downTime` from the log line.
  2. `PlusKeyLogEvent` — immutable (action, eventTimeMs, downTimeMs) record emitted to the main thread via `Handler`.
  3. `PlusKeyClassifier` — runs on the main thread. Translates `DOWN`/`UP`/`LONG` events into `SINGLE`/`DOUBLE`/`LONG` presses using two timers posted on the main `Handler`: a long-press timer scheduled on `DOWN` (fires at the configured threshold even while the key is still held), and a single-press timer scheduled on `UP` that resolves after the 450 ms `DOUBLE_PRESS_WINDOW_MS` if no second press arrives. Uses `KeyEvent`'s own `eventTime` / `downTime` for gap math rather than wall-clock time, because logcat delivery can lag.

The service dispatches the resolved press type to `reportPress` → `performAction` (flashlight via `CameraManager.setTorchMode`, ringer via `AudioManager.setRingerMode`, media via `AudioManager.dispatchMediaKeyEvent`). A `Toast` always shows the outcome.

### Shared state

Preferences live in `SharedPreferences` named `plus_key_settings`, with keys duplicated in both `SettingsActivity` and `PlusKeyMonitorService`. When renaming or adding keys, update **both** files. Keys: `long_press_ms` (int, default 650, clamped 100–3000), `single_action` / `double_action` / `long_action` (enum name stored as string; fallbacks: flashlight / ringer / play-pause).

### UI

All layouts are built programmatically in Java (`buildContent()` in each activity constructs `LinearLayout`/`ScrollView`/`TextView` trees). There are no XML layouts — only `res/values/*.xml` and launcher icons exist. New views should go through `Theme.*` helpers (`Theme.dp`, `Theme.text`/`title`/`body`/`eyebrow`/`pill`, `Theme.roundedFill`/`roundedStroke`, `Theme.block` for layout params). Don't hardcode hex colors or `dp` math inline — add or reuse a token in `Theme`.

## Device-specific detection notes

The log patterns are tuned for the OnePlus 15 firmware that emits lines like:

```
KEYLOG_PhoneWindowManagerExtImpl ... overrideInterceptKeyBeforeQueueing ...
  keyCode=KEYCODE_ACTION_BUTTON_CLICK, scanCode=735
```

If a different firmware build changes the scan code or the source tag, update `TRUSTED_SOURCE_PATTERN` and `PLUS_KEY_PATTERN` in `PlusKeyMonitorService.LogcatReader`. Avoid broadening to generic aliases like `smart key` / `quick button` — the README calls this out explicitly, because those match unrelated device events.

To capture the raw line on a new firmware:

```bash
adb logcat | findstr /i "KEYCODE_ACTION_BUTTON_CLICK scanCode ACTION_DOWN ACTION_UP"
```
