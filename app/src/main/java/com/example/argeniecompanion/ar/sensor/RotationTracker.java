package com.example.argeniecompanion.ar.sensor;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

/**
 * Tracks device orientation using TYPE_GAME_ROTATION_VECTOR.
 *
 * Why GAME_ROTATION_VECTOR instead of ROTATION_VECTOR?
 *   - No magnetic compass component → no drift in magnetically noisy environments
 *     (common in industrial / AR headset contexts).
 *   - Fuses gyroscope + accelerometer; good short-term accuracy.
 *   - Suitable for the 3-10 second annotation persistence window needed here.
 *
 * Usage:
 *   tracker.start()           // register sensor, typically in onResume
 *   tracker.getSnapshot()     // get current orientation at any time (thread-safe)
 *   getDeltaRotationMatrix()  // compute R_delta between two snapshots
 *   tracker.stop()            // unregister, typically in onPause
 */
public class RotationTracker implements SensorEventListener {

    private static final String TAG = "RotationTracker";

    private final SensorManager sensorManager;

    // Volatile quaternion components written by sensor thread, read from any thread
    private volatile float qx = 0f, qy = 0f, qz = 0f, qw = 1f;
    private volatile long  lastTimestampNs = 0L;

    public RotationTracker(Context context) {
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
    }

    public void start() {
        Sensor sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
        if (sensor == null) {
            Log.w(TAG, "TYPE_GAME_ROTATION_VECTOR not available; falling back to ROTATION_VECTOR");
            sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        }
        if (sensor != null) {
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME);
        } else {
            Log.e(TAG, "No rotation sensor available on this device");
        }
    }

    public void stop() {
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        int type = event.sensor.getType();
        if (type == Sensor.TYPE_GAME_ROTATION_VECTOR || type == Sensor.TYPE_ROTATION_VECTOR) {
            qx = event.values[0];
            qy = event.values[1];
            qz = event.values[2];
            // values[3] == cos(θ/2); present for ROTATION_VECTOR, may be absent for GAME_ROTATION_VECTOR
            qw = (event.values.length >= 4)
                    ? event.values[3]
                    : (float) Math.sqrt(Math.max(0.0, 1.0 - qx*qx - qy*qy - qz*qz));
            lastTimestampNs = event.timestamp;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    /** Thread-safe snapshot of the current device orientation. */
    public OrientationSnapshot getSnapshot() {
        return new OrientationSnapshot(qx, qy, qz, qw, lastTimestampNs);
    }

    /**
     * Computes the 3×3 rotation matrix R such that: v_current = R * v_stored
     *
     * This is used to rotate a stored annotation translation vector (recorded when a marker
     * was visible) into the current camera frame as the device rotates.
     *
     * Returns row-major float[9]: [r00, r01, r02, r10, r11, r12, r20, r21, r22]
     */
    public static float[] getDeltaRotationMatrix(OrientationSnapshot from, OrientationSnapshot to) {
        // delta_q = to_q * inverse(from_q)
        // For unit quaternions, inverse == conjugate
        float fx = -from.x, fy = -from.y, fz = -from.z, fw = from.w; // conjugate of 'from'
        float tx =  to.x,   ty =  to.y,   tz =  to.z,   tw = to.w;

        // Hamilton product: delta = to * conj(from)
        float dx = tw*fx + tx*fw + ty*fz - tz*fy;
        float dy = tw*fy - tx*fz + ty*fw + tz*fx;
        float dz = tw*fz + tx*fy - ty*fx + tz*fw;
        float dw = tw*fw - tx*fx - ty*fy - tz*fz;

        // Normalize (guard against accumulation errors)
        float norm = (float) Math.sqrt(dx*dx + dy*dy + dz*dz + dw*dw);
        if (norm > 1e-6f) { dx /= norm; dy /= norm; dz /= norm; dw /= norm; }

        // Quaternion to rotation matrix (row-major)
        return new float[]{
            1 - 2*(dy*dy + dz*dz),     2*(dx*dy - dw*dz),     2*(dx*dz + dw*dy),
                2*(dx*dy + dw*dz), 1 - 2*(dx*dx + dz*dz),     2*(dy*dz - dw*dx),
                2*(dx*dz - dw*dy),     2*(dy*dz + dw*dx), 1 - 2*(dx*dx + dy*dy)
        };
    }
}
