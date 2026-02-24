package com.example.argeniecompanion.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * Vuzix-optimised PDF page view using a viewport/crop approach.
 *
 * <p>The caller supplies a high-resolution page {@link Bitmap} (rendered at
 * {@code displayWidth × RENDER_SCALE}).  This view draws only a
 * <em>viewport</em> — a cropped {@code srcRect} of that bitmap — scaled to
 * fill the display via {@link Canvas#drawBitmap(Bitmap, Rect, RectF, Paint)}.
 *
 * <p>Calling {@link #smoothScroll(int)} moves the viewport up or down with a
 * {@link DecelerateInterpolator} animation.  When the viewport reaches the top
 * or bottom edge of the bitmap a {@link ScrollCallback} is fired so the
 * fragment can load the adjacent page.  No {@link android.widget.ScrollView}
 * or gesture detector is involved.
 *
 * <h3>Coordinate note</h3>
 * {@link #smoothScroll(int)} accepts <em>display pixels</em>.  The conversion
 * to bitmap pixels ({@code dy × renderScale}) is handled internally so callers
 * never need to know the render scale.
 */
public class PdfViewportView extends View {

    // ── Tuning constants ──────────────────────────────────────────────────────

    /** Scroll animation duration per button press (ms). Lower = snappier. */
    private static final int ANIM_MS = 120;

    /**
     * Edge-detection tolerance in bitmap pixels.
     * Prevents floating-point rounding from blocking the page-change callback.
     */
    private static final int EDGE_PX = 4;

    // ── Callback ──────────────────────────────────────────────────────────────

    /** Notified when the viewport scrolls into a page edge (main thread). */
    public interface ScrollCallback {
        /** Viewport reached the top — load the previous page. */
        void onTopEdgeReached();
        /** Viewport reached the bottom — load the next page. */
        void onBottomEdgeReached();
    }

    // ── State ─────────────────────────────────────────────────────────────────

    private Bitmap         pageBitmap;
    /** Current top of the visible region, in bitmap pixels. */
    private int            viewportY   = 0;
    private ScrollCallback scrollCallback;
    private ValueAnimator  runningAnim;

    private final Paint paint   = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Rect  srcRect = new Rect();
    private final RectF dstRect = new RectF();

    // ── Constructors ──────────────────────────────────────────────────────────

    public PdfViewportView(Context ctx)                          { super(ctx);          init(); }
    public PdfViewportView(Context ctx, AttributeSet a)          { super(ctx, a);       init(); }
    public PdfViewportView(Context ctx, AttributeSet a, int d)   { super(ctx, a, d);   init(); }

    private void init() { setBackgroundColor(Color.BLACK); }

    // ── Public API ────────────────────────────────────────────────────────────

    public void setScrollCallback(ScrollCallback cb) { this.scrollCallback = cb; }

    /**
     * Show {@code bitmap} from the <b>top</b> of the page.
     * Use when navigating forward (next page).
     */
    public void showPageFromTop(Bitmap bitmap) {
        cancelAnim();
        pageBitmap = bitmap;
        viewportY  = 0;
        invalidate();
    }

    /**
     * Show {@code bitmap} from the <b>bottom</b> of the page.
     * Use when navigating backward (previous page) so reading continues upward.
     */
    public void showPageFromBottom(Bitmap bitmap) {
        cancelAnim();
        pageBitmap = bitmap;
        viewportY  = maxViewportY(bitmap);
        invalidate();
    }

    /**
     * Scroll the viewport by {@code displayPx} display pixels.
     * Positive = down, negative = up.
     *
     * <p>An in-progress animation is cancelled first so rapid presses stay
     * responsive.  If the scroll would overshoot an edge it clamps and fires
     * the appropriate {@link ScrollCallback} when the animation ends.
     */
    public void smoothScroll(int displayPx) {
        if (pageBitmap == null || pageBitmap.isRecycled()) return;
        if (getWidth() == 0 || getHeight() == 0)          return;

        float renderScale = (float) pageBitmap.getWidth() / getWidth();
        int   bitmapDy    = (int) (displayPx * renderScale);
        int   maxY        = maxViewportY(pageBitmap);
        int   targetY     = Math.max(0, Math.min(viewportY + bitmapDy, maxY));

        boolean hitsBottom = displayPx > 0 && targetY >= maxY - EDGE_PX;
        boolean hitsTop    = displayPx < 0 && targetY <= EDGE_PX;

        if (targetY == viewportY) {
            // Already at the edge — fire callback immediately
            if (hitsBottom && scrollCallback != null) scrollCallback.onBottomEdgeReached();
            if (hitsTop    && scrollCallback != null) scrollCallback.onTopEdgeReached();
            return;
        }

        cancelAnim();

        ValueAnimator anim = ValueAnimator.ofInt(viewportY, targetY);
        anim.setDuration(ANIM_MS);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(a -> { viewportY = (int) a.getAnimatedValue(); invalidate(); });
        anim.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled = false;
            @Override public void onAnimationCancel(Animator a) { cancelled = true; runningAnim = null; }
            @Override public void onAnimationEnd(Animator a) {
                runningAnim = null;
                if (cancelled) return;
                if (hitsBottom && scrollCallback != null) scrollCallback.onBottomEdgeReached();
                if (hitsTop    && scrollCallback != null) scrollCallback.onTopEdgeReached();
            }
        });
        runningAnim = anim;
        anim.start();
    }

    // ── Drawing ───────────────────────────────────────────────────────────────

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(Color.BLACK);
        if (pageBitmap == null || pageBitmap.isRecycled()) return;

        int   viewW       = getWidth();
        int   viewH       = getHeight();
        int   bitmapW     = pageBitmap.getWidth();
        int   bitmapH     = pageBitmap.getHeight();
        float renderScale = (float) bitmapW / viewW; // > 1 for high-DPI bitmaps

        // How many bitmap rows fill the viewport height?
        int srcH = (int) (viewH * renderScale);

        // Crop window: spans full bitmap width, srcH rows from viewportY
        int srcBottom = Math.min(viewportY + srcH, bitmapH);
        srcRect.set(0, viewportY, bitmapW, srcBottom);

        // Destination: fills view width; height shrinks only on the last partial screen
        float dstH = (srcBottom - viewportY) / renderScale;
        dstRect.set(0, 0, viewW, dstH);

        canvas.drawBitmap(pageBitmap, srcRect, dstRect, paint);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Maximum value of viewportY for {@code bmp} given the current view size. */
    private int maxViewportY(Bitmap bmp) {
        if (bmp == null || getWidth() == 0 || getHeight() == 0) return 0;
        float rs = (float) bmp.getWidth() / getWidth();
        return Math.max(0, bmp.getHeight() - (int) (getHeight() * rs));
    }

    private void cancelAnim() {
        if (runningAnim != null) { runningAnim.cancel(); runningAnim = null; }
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public boolean isAtTop()    { return viewportY <= EDGE_PX; }
    public boolean isAtBottom() { return pageBitmap == null || viewportY >= maxViewportY(pageBitmap) - EDGE_PX; }
}
