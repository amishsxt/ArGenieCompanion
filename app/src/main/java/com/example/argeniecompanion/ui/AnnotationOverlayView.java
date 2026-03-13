package com.example.argeniecompanion.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import com.example.argeniecompanion.ar.pose.AnnotationRenderItem;

import java.util.Collections;
import java.util.List;

/**
 * Transparent View drawn on top of the GLSurfaceView.
 * Uses Canvas to render annotation labels at the projected 2D screen position of each marker.
 *
 * Visual design:
 *   • Small white dot at the exact projected point
 *   • Thin connecting line
 *   • Rounded-rect label box (semi-transparent blue) with white text
 *   • Everything fades out as alpha → 0
 *
 * Must be updated on the main thread via {@link #setRenderItems(List)}.
 */
public class AnnotationOverlayView extends View {

    // Label box dimensions in pixels (fixed size — fine for a headset display)
    private static final float BOX_HALF_W    = 140f;
    private static final float BOX_HALF_H    = 34f;
    private static final float BOX_LIFT      = 90f;   // how far above the dot the box sits
    private static final float CORNER_RADIUS = 14f;
    private static final float DOT_RADIUS    = 7f;
    private static final float TEXT_SIZE     = 26f;

    // Blue palette matching the app's colorPrimary (#007AFF)
    private static final int BG_R = 0x00, BG_G = 0x4A, BG_B = 0xCC;
    private static final int BD_R = 0x4D, BD_G = 0x7B, BD_F = 0xFF;

    private final Paint bgPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bdPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF boxRect   = new RectF();

    private List<AnnotationRenderItem> items = Collections.emptyList();

    public AnnotationOverlayView(Context context) {
        super(context);
        init();
    }

    public AnnotationOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setBackgroundColor(Color.TRANSPARENT);

        bgPaint.setStyle(Paint.Style.FILL);

        bdPaint.setStyle(Paint.Style.STROKE);
        bdPaint.setStrokeWidth(2.5f);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(TEXT_SIZE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(1.8f);

        dotPaint.setStyle(Paint.Style.FILL);
    }

    /** Update the render list and trigger a redraw. Call on main thread. */
    public void setRenderItems(List<AnnotationRenderItem> newItems) {
        items = (newItems != null) ? newItems : Collections.emptyList();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (AnnotationRenderItem item : items) {
            draw(canvas, item);
        }
    }

    private void draw(Canvas canvas, AnnotationRenderItem item) {
        int a = Math.round(item.alpha * 255f);
        if (a <= 0) return;

        float px = item.screenX;
        float py = item.screenY;
        float bcy = py - BOX_LIFT; // box centre y

        // ---- Dot ----
        dotPaint.setColor(Color.argb(Math.min(255, (int)(item.alpha * 230)), 255, 255, 255));
        canvas.drawCircle(px, py, DOT_RADIUS, dotPaint);

        // ---- Connecting line ----
        linePaint.setColor(Color.argb((int)(item.alpha * 160), 255, 255, 255));
        canvas.drawLine(px, py - DOT_RADIUS, px, bcy + BOX_HALF_H, linePaint);

        // ---- Box background ----
        boxRect.set(px - BOX_HALF_W, bcy - BOX_HALF_H, px + BOX_HALF_W, bcy + BOX_HALF_H);
        bgPaint.setColor(Color.argb(Math.min(220, (int)(item.alpha * 220)), BG_R, BG_G, BG_B));
        canvas.drawRoundRect(boxRect, CORNER_RADIUS, CORNER_RADIUS, bgPaint);

        // ---- Box border ----
        bdPaint.setColor(Color.argb(a, BD_R, BD_G, BD_F));
        canvas.drawRoundRect(boxRect, CORNER_RADIUS, CORNER_RADIUS, bdPaint);

        // ---- Label text ----
        textPaint.setAlpha(a);
        float textY = bcy + (textPaint.getTextSize() / 3f);
        canvas.drawText(item.label, px, textY, textPaint);
    }
}
