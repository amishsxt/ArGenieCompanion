package com.example.argeniecompanion.ar.vision;

/**
 * Result of detecting and pose-estimating a single ArUco marker in one frame.
 *
 * tvec: translation from camera optical centre to marker centre, in metres [x, y, z].
 * rvec: Rodrigues rotation vector describing marker orientation relative to camera [rx, ry, rz].
 */
public class MarkerDetectionResult {

    public final int    markerId;
    public final double[] tvec;         // [3]
    public final double[] rvec;         // [3]
    public final long   timestampNs;

    public MarkerDetectionResult(int markerId, double[] tvec, double[] rvec, long timestampNs) {
        this.markerId    = markerId;
        this.tvec        = tvec;
        this.rvec        = rvec;
        this.timestampNs = timestampNs;
    }
}
