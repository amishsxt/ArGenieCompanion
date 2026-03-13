package com.example.argeniecompanion.ar.pose;

import com.example.argeniecompanion.ar.sensor.OrientationSnapshot;

/**
 * One annotation anchored to a physical ArUco marker.
 *
 * When the marker is visible, tvec and storedOrientation are refreshed every frame.
 * When it leaves the frame, isFading becomes true and the annotation gradually
 * disappears over FADE_DURATION_MS using the gyroscope to keep it in the right direction.
 */
public class AnchoredAnnotation {

    public final int         markerId;
    public double[]          tvec;               // camera-frame translation at last detection (metres)
    public OrientationSnapshot storedOrientation; // device orientation at last detection
    public String            label;

    public boolean isFading;
    public long    fadeStartMs;

    public AnchoredAnnotation(int markerId, double[] tvec, OrientationSnapshot orientation) {
        this.markerId          = markerId;
        this.tvec              = tvec.clone();
        this.storedOrientation = orientation;
        this.label             = "Marker " + markerId;
        this.isFading          = false;
        this.fadeStartMs       = 0;
    }
}
