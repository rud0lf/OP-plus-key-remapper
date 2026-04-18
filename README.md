# Plus Key Remapper

A small Android app for OnePlus 15 devices that listens to logcat for Plus Key press events, classifies the action, and displays a toast for:

- Single press
- Double press
- Long press

Each press type can be mapped in the app to one of these actions:

- Toggle Flashlight
- Toggle between silent, vibrate, and ringer mode
- Play/Pause media

The app intentionally reads the key through logcat only. It does not use accessibility services, input remapping APIs, or root-only event devices.

## Build

Open this folder in Android Studio, let Gradle sync, then run the `app` configuration on the phone.

The project uses:

- Java
- Android Gradle Plugin 8.7.3
- `compileSdk 35`
- No external Android UI libraries

## Grant logcat access

Install and open the app once, then run this from your computer:

```powershell
adb shell pm grant com.pluskey.remapper android.permission.READ_LOGS
```

Return to the app. The status chip should change to `READ_LOGS granted`, and the app will ask you to press the Plus Key.

## How detection works

After `READ_LOGS` is granted, the app starts `PlusKeyMonitorService` as a foreground service. The service runs `logcat -v time -T 1` and keeps monitoring while the app is in the background. On the captured OnePlus 15 firmware, the Plus Key appears in logcat as:

```text
keyCode=KEYCODE_ACTION_BUTTON_CLICK, scanCode=735
```

The app only accepts lines that contain both that keycode and scan code, plus a real key action such as `ACTION_DOWN`, `ACTION_UP`, `action=0`, `action=1`, or `long press`.

After that, it classifies the timing:

- A short press followed by no second press inside 450 ms becomes `Single press detected`.
- A second press that starts inside 450 ms becomes `Double press detected`.
- A press held for the configured long-press threshold becomes `Long press detected`.

The long-press threshold is editable in the app and is saved on the device. The default is 650 ms, with allowed values from 100 ms to 3000 ms.

The classifier uses the `eventTime` and `downTime` inside the `KeyEvent` log line instead of the wall-clock time when logcat delivers the line. Long press also starts a hold timer on `ACTION_DOWN`, so a held key is reported as soon as the configured threshold is reached instead of waiting for key release.

The matching patterns are in:

```text
app/src/main/java/com/example/pluskeyremapper/MainActivity.java
```

Look for `PLUS_KEY_PATTERN`, `DOWN_PATTERN`, `UP_PATTERN`, and `LONG_PATTERN`.

If a different OP15 firmware build uses another scan code, find the exact emitted line with:

```powershell
adb logcat | findstr /i "KEYCODE_ACTION_BUTTON_CLICK scanCode ACTION_DOWN ACTION_UP"
```

Then update `PLUS_KEY_PATTERN` with the exact keycode and scan code. Avoid generic aliases like `smart key`, `quick button`, or plain `button`, because those can match other device events.

## Notes

`READ_LOGS` is a restricted Android permission. The app declares it in the manifest, but Android will not prompt for it in the normal runtime permission dialog. Granting it through adb is required.

The background monitor runs as a foreground service, so Android shows a persistent notification while Plus Key actions are active. On Android 13 and later, allow notification permission when prompted so the monitor can run normally.

Flashlight uses the Camera permission. The app requests it the first time a flashlight action runs.

Some Android builds may restrict silent mode changes unless the app has the relevant system sound/DND access. If the toast says ringer mode is blocked, grant the app sound/notification policy access in Android settings.

If the monitor still stops after some time, disable battery optimization for Plus Key Remapper in system settings. Some OEM battery managers are aggressive with long-running background services.
