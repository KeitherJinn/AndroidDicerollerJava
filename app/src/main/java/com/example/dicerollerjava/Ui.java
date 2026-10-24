package com.example.dicerollerjava;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared native-view styling; appearance values come from the selected palette. */
public final class Ui {
    public final Context context;
    public final ThemeColors colors;
    public Ui(Context context, ThemeColors colors) { this.context = context; this.colors = colors; }
    public int dp(float value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
    public LinearLayout column() {
        LinearLayout layout = new LinearLayout(context); layout.setOrientation(LinearLayout.VERTICAL);
        layout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return layout;
    }
    public LinearLayout card(int color) {
        LinearLayout layout = column(); layout.setPadding(dp(16), dp(16), dp(16), dp(16)); layout.setBackground(background(color, false)); return layout;
    }
    public GradientDrawable background(int color, boolean outlined) {
        GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(dp(16));
        if (outlined) shape.setStroke(dp(1), colors.outline);
        return shape;
    }
    public TextView text(CharSequence value, int sp, int color) {
        TextView view = new TextView(context); view.setText(value); view.setTextSize(sp); view.setTextColor(color);
        view.setPadding(0, dp(3), 0, dp(3)); view.setLayoutParams(new LinearLayout.LayoutParams(-1, -2)); return view;
    }
    public Button button(CharSequence value, String tag, Runnable action, boolean filled) {
        Button button = new Button(context); button.setText(value); button.setTag(tag); button.setAllCaps(false);
        button.setTextSize(14); button.setMinHeight(dp(48)); button.setMinimumHeight(dp(48)); button.setMinWidth(dp(64));
        button.setPadding(dp(12), dp(8), dp(12), dp(8));
        int[][] states = {new int[]{-android.R.attr.state_enabled}, new int[]{}};
        button.setTextColor(new ColorStateList(states, new int[]{colors.onSurfaceVariant, filled ? colors.onPrimary : colors.primary}));
        GradientDrawable bg = background(filled ? colors.primary : Color.TRANSPARENT, false);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33666666), bg, background(Color.WHITE, false)));
        button.setOnClickListener(v -> action.run()); return button;
    }
    public FlowLayout flow() { return new FlowLayout(context); }
    public void addGap(LinearLayout layout, int size) { View gap = new View(context); layout.addView(gap, new LinearLayout.LayoutParams(1, dp(size))); }
}
