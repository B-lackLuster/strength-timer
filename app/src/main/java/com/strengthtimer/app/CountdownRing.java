package com.strengthtimer.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Stage progress is independent of the overall workout timeline. */
final class CountdownRing extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float fraction = 1;
    private int color = 0xffcaef76;
    CountdownRing(Context context) { super(context); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
    void update(long remaining, long duration, int color) {
        this.fraction = Math.max(0, Math.min(1, remaining / (float)Math.max(1, duration)));
        this.color = color;
        invalidate();
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float stroke = 8 * getResources().getDisplayMetrics().density;
        float size = Math.min(getWidth(), getHeight()) - stroke * 2;
        float left = (getWidth() - size) / 2, top = (getHeight() - size) / 2;
        RectF bounds = new RectF(left, top, left + size, top + size);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(stroke); paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(0xff344039); canvas.drawOval(bounds, paint);
        paint.setColor(color); canvas.drawArc(bounds, -90, 360 * fraction, false, paint);
    }
}
