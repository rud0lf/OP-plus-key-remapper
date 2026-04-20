package com.pluskey.remapper;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Theme {
    static final int COLOR_BG             = 0xFFF6F7F9;
    static final int COLOR_SURFACE        = 0xFFFFFFFF;
    static final int COLOR_SURFACE_MUTED  = 0xFFF1F2F5;
    static final int COLOR_DIVIDER        = 0xFFECEEF2;
    static final int COLOR_BORDER         = 0xFFE2E5EB;
    static final int COLOR_TEXT_PRIMARY   = 0xFF0B0D12;
    static final int COLOR_TEXT_SECONDARY = 0xFF5B6473;
    static final int COLOR_TEXT_TERTIARY  = 0xFF8E97A6;
    static final int COLOR_ACCENT         = 0xFF4F46E5;
    static final int COLOR_ACCENT_SOFT    = 0xFFEEEFFE;
    static final int COLOR_ACCENT_INK     = 0xFF3730A3;
    static final int COLOR_SUCCESS_INK    = 0xFF065F46;
    static final int COLOR_SUCCESS_SOFT   = 0xFFDCF4E7;
    static final int COLOR_WARNING_INK    = 0xFF9A3412;
    static final int COLOR_WARNING_SOFT   = 0xFFFDEBD3;
    static final int COLOR_DANGER_INK     = 0xFF991B1B;
    static final int COLOR_DANGER_SOFT    = 0xFFFDE2E2;
    static final int COLOR_ON_ACCENT      = 0xFFFFFFFF;
    static final int COLOR_RIPPLE_DARK    = 0x14000000;
    static final int COLOR_RIPPLE_LIGHT   = 0x33FFFFFF;

    private Theme() {}

    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static TextView text(Context context, String value, float sp, int color, int style) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        return view;
    }

    static TextView display(Context context, String value) {
        TextView view = text(context, value, 30f, COLOR_TEXT_PRIMARY, Typeface.BOLD);
        view.setIncludeFontPadding(false);
        view.setLineSpacing(0f, 1.08f);
        view.setLetterSpacing(-0.02f);
        return view;
    }

    static TextView title(Context context, String value) {
        TextView view = text(context, value, 20f, COLOR_TEXT_PRIMARY, Typeface.BOLD);
        view.setIncludeFontPadding(false);
        view.setLineSpacing(0f, 1.22f);
        view.setLetterSpacing(-0.01f);
        return view;
    }

    static TextView body(Context context, String value) {
        TextView view = text(context, value, 15f, COLOR_TEXT_SECONDARY, Typeface.NORMAL);
        view.setLineSpacing(0f, 1.38f);
        return view;
    }

    static TextView caption(Context context, String value) {
        return text(context, value, 13f, COLOR_TEXT_TERTIARY, Typeface.NORMAL);
    }

    static TextView eyebrow(Context context, String value) {
        TextView view = text(context, value.toUpperCase(), 11f, COLOR_TEXT_TERTIARY, Typeface.BOLD);
        view.setLetterSpacing(0.16f);
        return view;
    }

    static GradientDrawable roundedFill(Context context, int fill, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    static GradientDrawable roundedStroke(Context context, int fill, int stroke, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(context, radiusDp));
        drawable.setStroke(dp(context, 1), stroke);
        return drawable;
    }

    static Drawable rippleOn(Context context, Drawable content, int rippleColor, int maskRadiusDp) {
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(0xFFFFFFFF);
        mask.setCornerRadius(dp(context, maskRadiusDp));
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask);
    }

    static Drawable rippleTransparent(int rippleColor) {
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), null, null);
    }

    static TextView pill(Context context, String value, int fillColor, int textColor) {
        TextView view = text(context, value, 12f, textColor, Typeface.BOLD);
        view.setLetterSpacing(0.02f);
        view.setPadding(dp(context, 10), dp(context, 6), dp(context, 10), dp(context, 6));
        view.setGravity(Gravity.CENTER);
        view.setBackground(roundedFill(context, fillColor, 999));
        return view;
    }

    static void setPill(Context context, TextView pill, String value, int fillColor, int textColor) {
        pill.setText(value);
        pill.setTextColor(textColor);
        pill.setBackground(roundedFill(context, fillColor, 999));
    }

    static void applyEdgeInsets(View outer, View padded, int horizontalDp, int topDp, int bottomDp) {
        Context context = padded.getContext();
        int horizontal = dp(context, horizontalDp);
        int top = dp(context, topDp);
        int bottom = dp(context, bottomDp);
        padded.setPadding(horizontal, top, horizontal, bottom);
        outer.setOnApplyWindowInsetsListener((view, insets) -> {
            padded.setPadding(
                    horizontal + insets.getSystemWindowInsetLeft(),
                    top + insets.getSystemWindowInsetTop(),
                    horizontal + insets.getSystemWindowInsetRight(),
                    bottom + insets.getSystemWindowInsetBottom());
            return insets;
        });
    }

    static LinearLayout.LayoutParams block(Context context, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(context, left), dp(context, top), dp(context, right), dp(context, bottom));
        return params;
    }
}
