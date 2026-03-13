package com.example.argeniecompanion.ar.pose;

/** Snapshot of one annotation's screen-space position and visibility for a single frame. */
public class AnnotationRenderItem {

    public final int    markerId;
    public final String label;
    public final float  screenX;  // centre x in view pixels
    public final float  screenY;  // centre y in view pixels
    public final float  alpha;    // 0.0 – 1.0

    public AnnotationRenderItem(int markerId, String label,
                                float screenX, float screenY, float alpha) {
        this.markerId = markerId;
        this.label    = label;
        this.screenX  = screenX;
        this.screenY  = screenY;
        this.alpha    = alpha;
    }
}
