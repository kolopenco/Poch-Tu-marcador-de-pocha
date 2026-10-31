package com.kolopenco.poch;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/** Coloca los hijos en filas, saltando de línea cuando no caben (chips). */
final class Flow extends ViewGroup {
    int hGap, vGap;

    Flow(Context c, int hGap, int vGap) {
        super(c);
        this.hGap = hGap;
        this.vGap = vGap;
    }

    @Override protected void onMeasure(int ws, int hs) {
        int maxW = MeasureSpec.getSize(ws) - getPaddingLeft() - getPaddingRight();
        int x = 0, y = 0, rowH = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View c = getChildAt(i);
            if (c.getVisibility() == GONE) continue;
            measureChild(c, MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            int w = c.getMeasuredWidth(), h = c.getMeasuredHeight();
            if (x > 0 && x + w > maxW) {
                x = 0;
                y += rowH + vGap;
                rowH = 0;
            }
            x += w + hGap;
            rowH = Math.max(rowH, h);
        }
        setMeasuredDimension(MeasureSpec.getSize(ws), y + rowH + getPaddingTop() + getPaddingBottom());
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int maxW = r - l - getPaddingLeft() - getPaddingRight();
        int x = 0, y = 0, rowH = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View c = getChildAt(i);
            if (c.getVisibility() == GONE) continue;
            int w = c.getMeasuredWidth(), h = c.getMeasuredHeight();
            if (x > 0 && x + w > maxW) {
                x = 0;
                y += rowH + vGap;
                rowH = 0;
            }
            c.layout(getPaddingLeft() + x, getPaddingTop() + y, getPaddingLeft() + x + w, getPaddingTop() + y + h);
            x += w + hGap;
            rowH = Math.max(rowH, h);
        }
    }
}
