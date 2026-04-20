package com.pluskey.remapper;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlusKeyMonitorService extends Service {
    private static final String TAG = "PlusKeyMonitorService";
    static final String ACTION_START = "com.pluskey.remapper.START_MONITOR";

    private static final String PREFS_NAME = "plus_key_settings";
    private static final String PREF_LONG_PRESS_MS = "long_press_ms";
    private static final String PREF_SINGLE_ACTION = "single_action";
    private static final String PREF_DOUBLE_ACTION = "double_action";
    private static final String PREF_LONG_ACTION = "long_action";
    private static final int DEFAULT_LONG_PRESS_MS = 650;
    private static final int DOUBLE_PRESS_WINDOW_MS = 450;
    private static final int NOTIFICATION_ID = 100;
    private static final String CHANNEL_ID = "plus_key_monitor";
    private static final String WAKE_LOCK_TAG = "PlusKeyRemapper:LogcatMonitor";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final PlusKeyClassifier classifier = new PlusKeyClassifier();

    private SharedPreferences preferences;
    private CameraManager cameraManager;
    private String torchCameraId;
    private boolean torchEnabled;
    private LogcatReader logcatReader;
    private PowerManager.WakeLock wakeLock;

    @Override
    public void onCreate() {
        super.onCreate();
        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        torchCameraId = findTorchCameraId();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (checkSelfPermission(Manifest.permission.READ_LOGS) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Grant READ_LOGS to keep Plus Key monitoring active", Toast.LENGTH_SHORT).show();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!startInForeground()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        acquireWakeLock();
        startLogcatReader();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopLogcatReader();
        releaseWakeLock();
        classifier.reset();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private boolean startInForeground() {
        Notification notification = buildNotification();
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
            return true;
        } catch (RuntimeException error) {
            Log.e(TAG, "Unable to start foreground monitor", error);
            Toast.makeText(this, "Unable to start background monitor", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    private Notification buildNotification() {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID);

        return builder
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(0xFFE2002B)
                .setContentTitle("Plus Key Remapper")
                .setContentText("Listening for Plus Key presses")
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setShowWhen(false)
                .build();
    }

    private void createNotificationChannel() {
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Plus Key monitor",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Keeps Plus Key actions active in the background");
        notificationManager.createNotificationChannel(channel);
    }

    @SuppressLint("WakelockTimeout")
    private void acquireWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            return;
        }
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager == null) {
            Log.w(TAG, "PowerManager unavailable; cannot acquire wake lock");
            return;
        }
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG);
        wakeLock.setReferenceCounted(false);
        // Held for the lifetime of this foreground service; released in onDestroy.
        wakeLock.acquire();
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        wakeLock = null;
    }

    private void startLogcatReader() {
        if (logcatReader != null) {
            return;
        }
        classifier.reset();
        logcatReader = new LogcatReader(new LogcatReader.Callback() {
            @Override
            public void onPlusKeyLog(PlusKeyLogEvent event) {
                mainHandler.post(() -> classifier.accept(event));
            }

            @Override
            public void onStopped() {
                mainHandler.post(() -> logcatReader = null);
            }
        });
        logcatReader.start();
    }

    private void stopLogcatReader() {
        if (logcatReader != null) {
            logcatReader.stopReading();
            logcatReader = null;
        }
    }

    private int getLongPressThresholdMs() {
        return preferences.getInt(PREF_LONG_PRESS_MS, DEFAULT_LONG_PRESS_MS);
    }

    private PressAction actionFor(PressType pressType) {
        String key;
        PressAction fallback;
        if (pressType == PressType.DOUBLE) {
            key = PREF_DOUBLE_ACTION;
            fallback = PressAction.TOGGLE_RINGER;
        } else if (pressType == PressType.LONG) {
            key = PREF_LONG_ACTION;
            fallback = PressAction.PLAY_PAUSE_MEDIA;
        } else {
            key = PREF_SINGLE_ACTION;
            fallback = PressAction.TOGGLE_FLASHLIGHT;
        }
        String value = preferences.getString(key, fallback.name());
        try {
            return PressAction.valueOf(value);
        } catch (IllegalArgumentException error) {
            return fallback;
        }
    }

    private void reportPress(PressType pressType) {
        PressAction action = actionFor(pressType);
        String result = performAction(action);
        Toast.makeText(this, pressType.label + ": " + result, Toast.LENGTH_SHORT).show();
    }

    private String performAction(PressAction action) {
        if (action == PressAction.TOGGLE_FLASHLIGHT) {
            return toggleFlashlight();
        }
        if (action == PressAction.NO_ACTION) {
            return "No action";
        }
        if (action == PressAction.TOGGLE_RINGER) {
            return toggleRingerMode();
        }
        if (action == PressAction.PLAY_PAUSE_MEDIA) {
            return playPauseMedia();
        }
        return "No action";
    }

    private String toggleFlashlight() {
        if (torchCameraId == null) {
            return "No flashlight available";
        }
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return "Open app to grant Camera permission";
        }
        try {
            torchEnabled = !torchEnabled;
            cameraManager.setTorchMode(torchCameraId, torchEnabled);
            return torchEnabled ? "Flashlight on" : "Flashlight off";
        } catch (CameraAccessException | IllegalArgumentException error) {
            torchEnabled = false;
            return "Flashlight unavailable";
        }
    }

    private String toggleRingerMode() {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) {
            return "Audio service unavailable";
        }

        int currentMode = audioManager.getRingerMode();
        int nextMode;
        String label;
        if (currentMode == AudioManager.RINGER_MODE_NORMAL) {
            nextMode = AudioManager.RINGER_MODE_VIBRATE;
            label = "Vibrate mode";
        } else {
            nextMode = AudioManager.RINGER_MODE_NORMAL;
            label = "Ringer mode";
        }

        try {
            audioManager.setRingerMode(nextMode);
        } catch (SecurityException error) {
            return "Ringer mode blocked";
        }
        if (nextMode == AudioManager.RINGER_MODE_NORMAL) {
            playRingerBeep();
        } else {
            vibrateForFeedback(500L);
        }
        return label;
    }

    private void playRingerBeep() {
        try {
            ToneGenerator toneGenerator = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80);
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 200);
            mainHandler.postDelayed(toneGenerator::release, 400L);
        } catch (RuntimeException error) {
            Log.w(TAG, "Unable to play ringer beep", error);
        }
    }

    private void vibrateForFeedback(long durationMs) {
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) {
            return;
        }
        vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE));
    }

    private String playPauseMedia() {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) {
            return "Audio service unavailable";
        }

        long eventTime = SystemClock.uptimeMillis();
        KeyEvent down = new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0);
        KeyEvent up = new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0);
        audioManager.dispatchMediaKeyEvent(down);
        audioManager.dispatchMediaKeyEvent(up);
        return "Play/Pause media";
    }

    private String findTorchCameraId() {
        if (cameraManager == null) {
            return null;
        }
        try {
            for (String cameraId : cameraManager.getCameraIdList()) {
                Boolean hasFlash = cameraManager
                        .getCameraCharacteristics(cameraId)
                        .get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                Integer lensFacing = cameraManager
                        .getCameraCharacteristics(cameraId)
                        .get(CameraCharacteristics.LENS_FACING);
                if (Boolean.TRUE.equals(hasFlash)
                        && lensFacing != null
                        && lensFacing == CameraCharacteristics.LENS_FACING_BACK) {
                    return cameraId;
                }
            }
        } catch (CameraAccessException error) {
            return null;
        }
        return null;
    }

    private enum PressType {
        SINGLE("Single press"),
        DOUBLE("Double press"),
        LONG("Long press");

        private final String label;

        PressType(String label) {
            this.label = label;
        }
    }

    private enum PressAction {
        NO_ACTION,
        TOGGLE_FLASHLIGHT,
        TOGGLE_RINGER,
        PLAY_PAUSE_MEDIA
    }

    private enum PlusKeyAction {
        DOWN,
        UP,
        LONG
    }

    private final class PlusKeyClassifier {
        private long lastDownEventTimeMs;
        private long pendingTapEventTimeMs;
        private boolean waitingForSecondTap;
        private boolean suppressNextUp;
        private Runnable longPressRunnable;
        private Runnable singlePressRunnable;

        void reset() {
            cancelLongPress();
            cancelSinglePress();
            lastDownEventTimeMs = 0L;
            pendingTapEventTimeMs = 0L;
            waitingForSecondTap = false;
            suppressNextUp = false;
        }

        void accept(PlusKeyLogEvent event) {
            if (event.action == PlusKeyAction.LONG) {
                cancelSinglePress();
                clearPendingTap();
                reportPress(PressType.LONG);
                return;
            }

            if (event.action == PlusKeyAction.DOWN) {
                long downEventTimeMs = event.eventTimeMs > 0L ? event.eventTimeMs : SystemClock.uptimeMillis();
                if (waitingForSecondTap) {
                    long gapMs = downEventTimeMs - pendingTapEventTimeMs;
                    if (gapMs >= 0L && gapMs <= DOUBLE_PRESS_WINDOW_MS) {
                        cancelSinglePress();
                        clearPendingTap();
                        suppressNextUp = true;
                        lastDownEventTimeMs = 0L;
                        reportPress(PressType.DOUBLE);
                        return;
                    }
                    if (gapMs > DOUBLE_PRESS_WINDOW_MS) {
                        cancelSinglePress();
                        clearPendingTap();
                        reportPress(PressType.SINGLE);
                    }
                }
                lastDownEventTimeMs = downEventTimeMs;
                scheduleLongPress(downEventTimeMs);
                return;
            }

            if (event.action != PlusKeyAction.UP) {
                return;
            }

            if (suppressNextUp) {
                suppressNextUp = false;
                lastDownEventTimeMs = 0L;
                cancelLongPress();
                return;
            }

            cancelLongPress();
            long durationMs = event.durationMs();
            if (durationMs < 0L && lastDownEventTimeMs > 0L && event.eventTimeMs > 0L) {
                durationMs = event.eventTimeMs - lastDownEventTimeMs;
            }
            lastDownEventTimeMs = 0L;

            if (durationMs >= getLongPressThresholdMs()) {
                cancelSinglePress();
                clearPendingTap();
                suppressNextUp = false;
                reportPress(PressType.LONG);
                return;
            }

            registerShortPress(event.eventTimeMs);
        }

        private void registerShortPress(long eventTimeMs) {
            long tapEventTimeMs = eventTimeMs > 0L ? eventTimeMs : SystemClock.uptimeMillis();
            if (waitingForSecondTap) {
                long gapMs = tapEventTimeMs - pendingTapEventTimeMs;
                if (gapMs >= 0L && gapMs <= DOUBLE_PRESS_WINDOW_MS) {
                    cancelSinglePress();
                    clearPendingTap();
                    reportPress(PressType.DOUBLE);
                } else {
                    cancelSinglePress();
                    reportPress(PressType.SINGLE);
                    scheduleSinglePress(tapEventTimeMs);
                }
                return;
            }

            scheduleSinglePress(tapEventTimeMs);
        }

        private void scheduleLongPress(long downEventTimeMs) {
            cancelLongPress();
            int thresholdMs = getLongPressThresholdMs();
            long elapsedSinceDownMs = Math.max(0L, SystemClock.uptimeMillis() - downEventTimeMs);
            long remainingMs = Math.max(0L, thresholdMs - elapsedSinceDownMs);
            longPressRunnable = () -> {
                if (lastDownEventTimeMs == downEventTimeMs && !suppressNextUp) {
                    cancelSinglePress();
                    clearPendingTap();
                    suppressNextUp = true;
                    lastDownEventTimeMs = 0L;
                    reportPress(PressType.LONG);
                }
            };
            mainHandler.postDelayed(longPressRunnable, remainingMs);
        }

        private void scheduleSinglePress(long tapEventTimeMs) {
            waitingForSecondTap = true;
            pendingTapEventTimeMs = tapEventTimeMs;
            singlePressRunnable = () -> {
                clearPendingTap();
                reportPress(PressType.SINGLE);
            };
            mainHandler.postDelayed(singlePressRunnable, DOUBLE_PRESS_WINDOW_MS);
        }

        private void clearPendingTap() {
            waitingForSecondTap = false;
            pendingTapEventTimeMs = 0L;
        }

        private void cancelLongPress() {
            if (longPressRunnable != null) {
                mainHandler.removeCallbacks(longPressRunnable);
                longPressRunnable = null;
            }
        }

        private void cancelSinglePress() {
            if (singlePressRunnable != null) {
                mainHandler.removeCallbacks(singlePressRunnable);
                singlePressRunnable = null;
            }
        }
    }

    private static final class PlusKeyLogEvent {
        private final PlusKeyAction action;
        private final long eventTimeMs;
        private final long downTimeMs;

        PlusKeyLogEvent(PlusKeyAction action, long eventTimeMs, long downTimeMs) {
            this.action = action;
            this.eventTimeMs = eventTimeMs;
            this.downTimeMs = downTimeMs;
        }

        long durationMs() {
            if (eventTimeMs <= 0L || downTimeMs <= 0L) {
                return -1L;
            }
            return eventTimeMs - downTimeMs;
        }
    }

    private static final class LogcatReader extends Thread {
        private static final Pattern TRUSTED_SOURCE_PATTERN = Pattern.compile(
                "\\bKEYLOG_PhoneWindowManagerExtImpl\\b.*\\boverrideInterceptKeyBeforeQueueing\\b",
                Pattern.CASE_INSENSITIVE
        );
        private static final Pattern PLUS_KEY_PATTERN = Pattern.compile(
                "(?=.*\\bkeycode\\s*=\\s*keycode_action_button_click\\b)(?=.*\\bscancode\\s*=\\s*735\\b)",
                Pattern.CASE_INSENSITIVE
        );
        private static final Pattern DOWN_PATTERN = Pattern.compile(
                "(action[_ -]?down|\\baction\\s*[=:]\\s*0\\b|\\bdown\\b|key down)",
                Pattern.CASE_INSENSITIVE
        );
        private static final Pattern UP_PATTERN = Pattern.compile(
                "(action[_ -]?up|\\baction\\s*[=:]\\s*1\\b|\\bup\\b|key up)",
                Pattern.CASE_INSENSITIVE
        );
        private static final Pattern LONG_PATTERN = Pattern.compile(
                "(long[ _-]?press|longpressed|long_press)",
                Pattern.CASE_INSENSITIVE
        );
        private static final Pattern EVENT_TIME_PATTERN = Pattern.compile(
                "\\beventtime\\s*=\\s*(\\d+)\\b",
                Pattern.CASE_INSENSITIVE
        );
        private static final Pattern DOWN_TIME_PATTERN = Pattern.compile(
                "\\bdowntime\\s*=\\s*(\\d+)\\b",
                Pattern.CASE_INSENSITIVE
        );

        private final Callback callback;
        private volatile boolean running = true;
        private Process process;

        LogcatReader(Callback callback) {
            super("PlusKeyMonitorLogcatReader");
            this.callback = callback;
        }

        @Override
        public void run() {
            try {
                process = new ProcessBuilder(
                        "logcat", "-v", "time", "-T", "1",
                        "-s", "KEYLOG_PhoneWindowManagerExtImpl:*")
                        .redirectErrorStream(true)
                        .start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while (running && (line = reader.readLine()) != null) {
                        PlusKeyLogEvent event = parseLine(line);
                        if (event != null) {
                            callback.onPlusKeyLog(event);
                        }
                    }
                }
                callback.onStopped();
            } catch (IOException error) {
                callback.onStopped();
            } finally {
                if (process != null) {
                    process.destroy();
                }
            }
        }

        void stopReading() {
            running = false;
            if (process != null) {
                process.destroy();
            }
        }

        private PlusKeyLogEvent parseLine(String line) {
            if (!TRUSTED_SOURCE_PATTERN.matcher(line).find()) {
                return null;
            }
            if (!PLUS_KEY_PATTERN.matcher(line).find()) {
                return null;
            }
            long eventTimeMs = extractTimeMs(EVENT_TIME_PATTERN, line);
            long downTimeMs = extractTimeMs(DOWN_TIME_PATTERN, line);

            if (LONG_PATTERN.matcher(line).find()) {
                return new PlusKeyLogEvent(PlusKeyAction.LONG, eventTimeMs, downTimeMs);
            }
            if (DOWN_PATTERN.matcher(line).find()) {
                return new PlusKeyLogEvent(PlusKeyAction.DOWN, eventTimeMs, downTimeMs);
            }
            if (UP_PATTERN.matcher(line).find()) {
                return new PlusKeyLogEvent(PlusKeyAction.UP, eventTimeMs, downTimeMs);
            }
            return null;
        }

        private long extractTimeMs(Pattern pattern, String line) {
            Matcher matcher = pattern.matcher(line);
            if (!matcher.find()) {
                return 0L;
            }
            try {
                long raw = Long.parseLong(Objects.requireNonNull(matcher.group(1)));
                return raw / 1_000_000L;
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }

        interface Callback {
            void onPlusKeyLog(PlusKeyLogEvent event);

            void onStopped();
        }
    }
}
