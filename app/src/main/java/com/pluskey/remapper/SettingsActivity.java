package com.pluskey.remapper;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class SettingsActivity extends Activity {
    private static final String PREFS_NAME = "plus_key_settings";
    private static final String PREF_LONG_PRESS_MS = "long_press_ms";
    private static final String PREF_SINGLE_ACTION = "single_action";
    private static final String PREF_DOUBLE_ACTION = "double_action";
    private static final String PREF_LONG_ACTION = "long_action";
    private static final int REQUEST_CAMERA_PERMISSION = 1001;
    private static final int DEFAULT_LONG_PRESS_MS = 650;
    private static final int MIN_LONG_PRESS_MS = 100;
    private static final int MAX_LONG_PRESS_MS = 3000;

    private SharedPreferences preferences;
    private TextView singlePressValue;
    private TextView doublePressValue;
    private TextView longPressValue;
    private TextView timeoutValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        setContentView(buildContent());
        refreshValues();
    }

    private View buildContent() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Theme.COLOR_BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Theme.dp(this, 24), Theme.dp(this, 40), Theme.dp(this, 24), Theme.dp(this, 32));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(Theme.eyebrow(this, "Settings"), Theme.block(this, 0, 0, 0, 10));
        root.addView(Theme.display(this, "Customize the Plus Key."), Theme.block(this, 0, 0, 0, 10));
        root.addView(Theme.body(this, "Pick what each press does and fine-tune the long-press feel."),
                Theme.block(this, 0, 0, 0, 28));

        root.addView(Theme.eyebrow(this, "Press actions"), Theme.block(this, 4, 0, 0, 10));

        singlePressValue = Theme.text(this, "", 14f, Theme.COLOR_ACCENT, Typeface.NORMAL);
        doublePressValue = Theme.text(this, "", 14f, Theme.COLOR_ACCENT, Typeface.NORMAL);
        longPressValue = Theme.text(this, "", 14f, Theme.COLOR_ACCENT, Typeface.NORMAL);

        LinearLayout actionGroup = buildCardGroup();
        actionGroup.addView(settingsRow("Single press", singlePressValue, v -> showActionDialog(PressType.SINGLE)));
        actionGroup.addView(divider());
        actionGroup.addView(settingsRow("Double press", doublePressValue, v -> showActionDialog(PressType.DOUBLE)));
        actionGroup.addView(divider());
        actionGroup.addView(settingsRow("Long press", longPressValue, v -> showActionDialog(PressType.LONG)));
        root.addView(actionGroup, Theme.block(this, 0, 0, 0, 24));

        root.addView(Theme.eyebrow(this, "Timing"), Theme.block(this, 4, 0, 0, 10));

        timeoutValue = Theme.text(this, "", 14f, Theme.COLOR_ACCENT, Typeface.NORMAL);
        LinearLayout timingGroup = buildCardGroup();
        timingGroup.addView(settingsRow("Long-press threshold", timeoutValue, v -> showTimeoutDialog()));
        root.addView(timingGroup, Theme.block(this, 0, 0, 0, 10));

        TextView helper = Theme.caption(this, "Lower values trigger long press sooner. Allowed range: "
                + MIN_LONG_PRESS_MS + "–" + MAX_LONG_PRESS_MS + " ms.");
        LinearLayout.LayoutParams helperParams = Theme.block(this, 4, 2, 4, 0);
        root.addView(helper, helperParams);

        return scrollView;
    }

    private LinearLayout buildCardGroup() {
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setBackground(Theme.roundedStroke(this, Theme.COLOR_SURFACE, Theme.COLOR_BORDER, 16));
        group.setClipToOutline(true);
        group.setElevation(Theme.dp(this, 1));
        return group;
    }

    private View settingsRow(String label, TextView valueView, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Theme.dp(this, 18), Theme.dp(this, 16), Theme.dp(this, 18), Theme.dp(this, 16));
        row.setMinimumHeight(Theme.dp(this, 60));
        row.setClickable(true);
        row.setFocusable(true);
        row.setBackground(Theme.rippleTransparent(Theme.COLOR_RIPPLE_DARK));
        row.setOnClickListener(listener);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);

        TextView labelView = Theme.text(this, label, 16f, Theme.COLOR_TEXT_PRIMARY, Typeface.BOLD);
        labels.addView(labelView);

        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        valueParams.topMargin = Theme.dp(this, 3);
        labels.addView(valueView, valueParams);

        LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(labels, labelsParams);

        TextView chevron = Theme.text(this, "›", 24f, Theme.COLOR_TEXT_TERTIARY, Typeface.NORMAL);
        chevron.setPadding(Theme.dp(this, 8), 0, 0, 0);
        row.addView(chevron);

        return row;
    }

    private View divider() {
        View view = new View(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(this, 1));
        params.setMargins(Theme.dp(this, 18), 0, Theme.dp(this, 18), 0);
        view.setLayoutParams(params);
        view.setBackgroundColor(Theme.COLOR_DIVIDER);
        return view;
    }

    private void showActionDialog(PressType pressType) {
        PressAction currentAction = getAction(pressType);
        String[] labels = PressAction.labels();
        new AlertDialog.Builder(this)
                .setTitle("Choose action")
                .setSingleChoiceItems(labels, currentAction.ordinal(), (dialog, which) -> {
                    PressAction action = PressAction.fromPosition(which);
                    setAction(pressType, action);
                    refreshValues();
                    if (action == PressAction.TOGGLE_FLASHLIGHT
                            && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
                    }
                    Toast.makeText(this, pressType.label + " set to " + action.label, Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showTimeoutDialog() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        input.setText(String.valueOf(getLongPressMs()));
        input.setPadding(Theme.dp(this, 20), Theme.dp(this, 10), Theme.dp(this, 20), Theme.dp(this, 10));

        new AlertDialog.Builder(this)
                .setTitle("Long-press threshold")
                .setMessage("Enter a value from " + MIN_LONG_PRESS_MS + " to " + MAX_LONG_PRESS_MS + " ms.")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> saveTimeout(input.getText().toString()))
                .show();
    }

    private void saveTimeout(String rawValue) {
        int value;
        try {
            value = Integer.parseInt(rawValue.trim());
        } catch (NumberFormatException error) {
            Toast.makeText(this, "Enter a valid number", Toast.LENGTH_SHORT).show();
            return;
        }

        if (value < MIN_LONG_PRESS_MS || value > MAX_LONG_PRESS_MS) {
            Toast.makeText(this, "Use " + MIN_LONG_PRESS_MS + " to " + MAX_LONG_PRESS_MS + " ms", Toast.LENGTH_SHORT).show();
            return;
        }

        preferences.edit().putInt(PREF_LONG_PRESS_MS, value).apply();
        refreshValues();
        Toast.makeText(this, "Long press set to " + value + " ms", Toast.LENGTH_SHORT).show();
    }

    private void refreshValues() {
        singlePressValue.setText(getAction(PressType.SINGLE).label);
        doublePressValue.setText(getAction(PressType.DOUBLE).label);
        longPressValue.setText(getAction(PressType.LONG).label);
        timeoutValue.setText(getLongPressMs() + " ms");
    }

    private int getLongPressMs() {
        return preferences.getInt(PREF_LONG_PRESS_MS, DEFAULT_LONG_PRESS_MS);
    }

    private PressAction getAction(PressType pressType) {
        String value = preferences.getString(prefKeyFor(pressType), fallbackFor(pressType).name());
        try {
            return PressAction.valueOf(value);
        } catch (IllegalArgumentException error) {
            return fallbackFor(pressType);
        }
    }

    private void setAction(PressType pressType, PressAction action) {
        preferences.edit().putString(prefKeyFor(pressType), action.name()).apply();
    }

    private String prefKeyFor(PressType pressType) {
        if (pressType == PressType.DOUBLE) {
            return PREF_DOUBLE_ACTION;
        }
        if (pressType == PressType.LONG) {
            return PREF_LONG_ACTION;
        }
        return PREF_SINGLE_ACTION;
    }

    private PressAction fallbackFor(PressType pressType) {
        if (pressType == PressType.DOUBLE) {
            return PressAction.TOGGLE_RINGER;
        }
        if (pressType == PressType.LONG) {
            return PressAction.PLAY_PAUSE_MEDIA;
        }
        return PressAction.TOGGLE_FLASHLIGHT;
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
        NO_ACTION("No action"),
        TOGGLE_FLASHLIGHT("Toggle flashlight"),
        TOGGLE_RINGER("Toggle sound mode"),
        PLAY_PAUSE_MEDIA("Play / pause media");

        private final String label;

        PressAction(String label) {
            this.label = label;
        }

        static String[] labels() {
            PressAction[] actions = values();
            String[] labels = new String[actions.length];
            for (int index = 0; index < actions.length; index++) {
                labels[index] = actions[index].label;
            }
            return labels;
        }

        static PressAction fromPosition(int position) {
            PressAction[] values = values();
            if (position < 0 || position >= values.length) {
                return NO_ACTION;
            }
            return values[position];
        }
    }
}
