package com.example.dicerollerjava;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/** Wrap controls at their measured font-scaled width instead of clipping them. */
public final class FlowLayout extends ViewGroup {
    private final int gap;
    public FlowLayout(Context context) { super(context); gap = Math.round(8 * getResources().getDisplayMetrics().density); }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        int available = Math.max(0, width - getPaddingLeft() - getPaddingRight());
        int x = 0, y = 0, rowHeight = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            child.measure(MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            int w = child.getMeasuredWidth(), h = child.getMeasuredHeight();
            if (x > 0 && x + w > available) { x = 0; y += rowHeight + gap; rowHeight = 0; }
            x += w + gap; rowHeight = Math.max(rowHeight, h);
        }
        setMeasuredDimension(resolveSize(width, widthSpec), resolveSize(y + rowHeight + getPaddingTop() + getPaddingBottom(), heightSpec));
    }
    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int available = right - left - getPaddingLeft() - getPaddingRight();
        int x = 0, y = getPaddingTop(), rowHeight = 0;
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            int w = child.getMeasuredWidth(), h = child.getMeasuredHeight();
            if (x > 0 && x + w > available) { x = 0; y += rowHeight + gap; rowHeight = 0; }
            int childLeft = rtl ? right - left - getPaddingRight() - x - w : getPaddingLeft() + x;
            child.layout(childLeft, y, childLeft + w, y + h);
            x += w + gap; rowHeight = Math.max(rowHeight, h);
        }
    }
    @Override protected LayoutParams generateDefaultLayoutParams() { return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT); }
}
