package com.example.argeniecompanion.ar.sensor;

/**
 * Immutable snapshot of device orientation as a unit quaternion [x, y, z, w].
 * Captured from TYPE_GAME_ROTATION_VECTOR (gyro + accelerometer, no compass).
 */
public class OrientationSnapshot {

    public final float x, y, z, w;
    public final long  timestampNs;

    public OrientationSnapshot(float x, float y, float z, float w, long timestampNs) {
        this.x = x; this.y = y; this.z = z; this.w = w;
        this.timestampNs = timestampNs;
    }

    public float[] toArray() {
        return new float[]{x, y, z, w};
    }
}
