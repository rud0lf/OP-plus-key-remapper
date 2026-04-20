package com.pluskey.remapper;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String PACKAGE_NAME = "com.pluskey.remapper";
    private static final int REQUEST_NOTIFICATIONS_PERMISSION = 1002;

    private TextView permissionPill;
    private TextView monitorPill;
    private TextView statusTitle;
    private TextView statusBody;
    private LinearLayout setupCard;
    private LinearLayout setupSteps;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildContent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshState();
    }

    private View buildContent() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Theme.COLOR_BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        Theme.applyEdgeInsets(scrollView, root, 24, 40, 32);
        scrollView.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(Theme.eyebrow(this, "Plus Key Remapper"), Theme.block(this, 0, 0, 0, 10));
        root.addView(Theme.display(this, "Make the Plus Key yours."), Theme.block(this, 0, 0, 0, 10));
        root.addView(Theme.body(this, "Assign single, double, and long press to the actions you use most."),
                Theme.block(this, 0, 0, 0, 28));

        root.addView(buildStatusCard(), Theme.block(this, 0, 0, 0, 14));

        setupCard = buildSetupCard();
        root.addView(setupCard, Theme.block(this, 0, 0, 0, 24));

        Button settingsButton = primaryButton("Open settings");
        settingsButton.setOnClickListener(view -> startActivity(new Intent(this, SettingsActivity.class)));
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(this, 54));
        root.addView(settingsButton, buttonParams);

        return scrollView;
    }

    private View buildStatusCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Theme.dp(this, 20), Theme.dp(this, 20), Theme.dp(this, 20), Theme.dp(this, 22));
        card.setBackground(Theme.roundedStroke(this, Theme.COLOR_SURFACE, Theme.COLOR_BORDER, 18));
        card.setElevation(Theme.dp(this, 1));

        LinearLayout pillRow = new LinearLayout(this);
        pillRow.setOrientation(LinearLayout.HORIZONTAL);
        pillRow.setGravity(Gravity.CENTER_VERTICAL);
        pillRow.setBaselineAligned(false);

        permissionPill = Theme.pill(this, "Checking", Theme.COLOR_ACCENT_SOFT, Theme.COLOR_ACCENT_INK);
        pillRow.addView(permissionPill);

        monitorPill = Theme.pill(this, "Monitor idle", Theme.COLOR_SURFACE_MUTED, Theme.COLOR_TEXT_SECONDARY);
        LinearLayout.LayoutParams monitorParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        monitorParams.leftMargin = Theme.dp(this, 8);
        pillRow.addView(monitorPill, monitorParams);

        card.addView(pillRow, Theme.block(this, 0, 0, 0, 18));

        statusTitle = Theme.title(this, "");
        card.addView(statusTitle, Theme.block(this, 0, 0, 0, 6));

        statusBody = Theme.body(this, "");
        card.addView(statusBody);

        return card;
    }

    private LinearLayout buildSetupCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Theme.dp(this, 20), Theme.dp(this, 20), Theme.dp(this, 20), Theme.dp(this, 22));
        card.setBackground(Theme.roundedStroke(this, Theme.COLOR_SURFACE, Theme.COLOR_BORDER, 18));
        card.setElevation(Theme.dp(this, 1));

        card.addView(Theme.eyebrow(this, "Setup"), Theme.block(this, 0, 0, 0, 10));
        card.addView(Theme.title(this, "Grant log access once"), Theme.block(this, 0, 0, 0, 6));
        card.addView(Theme.body(this, "The Plus Key only surfaces in system logs on this device. Run one adb command and you're done."),
                Theme.block(this, 0, 0, 0, 18));

        setupSteps = new LinearLayout(this);
        setupSteps.setOrientation(LinearLayout.VERTICAL);
        card.addView(setupSteps);

        return card;
    }

    private void refreshState() {
        boolean readLogsGranted = checkSelfPermission(Manifest.permission.READ_LOGS) == PackageManager.PERMISSION_GRANTED;
        if (readLogsGranted) {
            Theme.setPill(this, permissionPill, "Permission granted", Theme.COLOR_SUCCESS_SOFT, Theme.COLOR_SUCCESS_INK);
            Theme.setPill(this, monitorPill, "Listening", Theme.COLOR_ACCENT_SOFT, Theme.COLOR_ACCENT_INK);
            statusTitle.setText("Listening for your Plus Key");
            statusBody.setText("Single, double, and long presses trigger the actions you pick. Tap a binding in settings to change it any time.");
            setupCard.setVisibility(View.GONE);
            startMonitorService();
            requestNotificationPermissionIfNeeded();
        } else {
            Theme.setPill(this, permissionPill, "Setup needed", Theme.COLOR_WARNING_SOFT, Theme.COLOR_WARNING_INK);
            Theme.setPill(this, monitorPill, "Monitor paused", Theme.COLOR_SURFACE_MUTED, Theme.COLOR_TEXT_SECONDARY);
            statusTitle.setText("One step to go");
            statusBody.setText("Grant log access from your computer and the app starts listening in the background right away.");
            setupCard.setVisibility(View.VISIBLE);
            renderSetupSteps();
        }
    }

    private void renderSetupSteps() {
        setupSteps.removeAllViews();
        setupSteps.addView(step("1", "Enable USB debugging and connect your OnePlus 15."));
        setupSteps.addView(step("2", "Install and open this app once."));
        setupSteps.addView(step("3", "Run this from your computer:"));

        TextView command = Theme.text(this,
                "adb shell pm grant " + PACKAGE_NAME + " android.permission.READ_LOGS",
                13f, Theme.COLOR_TEXT_PRIMARY, Typeface.NORMAL);
        command.setTypeface(Typeface.MONOSPACE);
        command.setLineSpacing(0f, 1.35f);
        command.setPadding(Theme.dp(this, 14), Theme.dp(this, 12), Theme.dp(this, 14), Theme.dp(this, 12));
        command.setBackground(Theme.roundedStroke(this, Theme.COLOR_SURFACE_MUTED, Theme.COLOR_BORDER, 10));
        LinearLayout.LayoutParams commandParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        commandParams.setMargins(Theme.dp(this, 40), Theme.dp(this, 4), 0, Theme.dp(this, 14));
        setupSteps.addView(command, commandParams);

        setupSteps.addView(step("4", "Return here. Allow notifications if asked — Camera is only requested if you map to Flashlight."));
    }

    private View step(String number, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        row.setPadding(0, 0, 0, Theme.dp(this, 12));

        TextView badge = Theme.text(this, number, 12f, Theme.COLOR_ACCENT_INK, Typeface.BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(Theme.roundedFill(this, Theme.COLOR_ACCENT_SOFT, 999));
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(Theme.dp(this, 26), Theme.dp(this, 26));
        row.addView(badge, badgeParams);

        TextView body = Theme.body(this, value);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        bodyParams.leftMargin = Theme.dp(this, 14);
        bodyParams.topMargin = Theme.dp(this, 2);
        row.addView(body, bodyParams);

        return row;
    }

    private void startMonitorService() {
        Intent intent = new Intent(this, PlusKeyMonitorService.class);
        intent.setAction(PlusKeyMonitorService.ACTION_START);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        } catch (RuntimeException error) {
            Theme.setPill(this, monitorPill, "Monitor blocked", Theme.COLOR_DANGER_SOFT, Theme.COLOR_DANGER_INK);
            Toast.makeText(this, "Android blocked the background monitor", Toast.LENGTH_SHORT).show();
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS_PERMISSION);
        }
    }

    private Button primaryButton(String value) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(value);
        button.setTextSize(16f);
        button.setTextColor(Theme.COLOR_ON_ACCENT);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setLetterSpacing(0.01f);
        GradientDrawable content = Theme.roundedFill(this, Theme.COLOR_ACCENT, 14);
        button.setBackground(Theme.rippleOn(this, content, Theme.COLOR_RIPPLE_LIGHT, 14));
        button.setStateListAnimator(null);
        button.setElevation(0f);
        return button;
    }
}
