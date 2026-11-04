package cn.autohack.rebuild;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.Surface;
import android.view.WindowManager;

/** Foreground-only device heading reader using Android's public rotation-vector sensor. */
public final class DeviceCompassController implements SensorEventListener {
    public interface Listener {
        void onHeadingChanged(String heading);
    }

    private final SensorManager sensorManager;
    private final Sensor rotationVector;
    private final WindowManager windowManager;
    private final Listener listener;
    private boolean registered;

    public DeviceCompassController(Context context, Listener listener) {
        this.sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.rotationVector = sensorManager == null ? null
                : sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        this.listener = listener;
    }

    /** Returns false if the device has no rotation-vector sensor or registration fails. */
    public boolean start() {
        if (registered) return true;
        if (sensorManager == null || rotationVector == null) return false;
        registered = sensorManager.registerListener(this, rotationVector, SensorManager.SENSOR_DELAY_UI);
        return registered;
    }

    public void stop() {
        if (registered && sensorManager != null) sensorManager.unregisterListener(this);
        registered = false;
    }

    public boolean isAvailable() {
        return rotationVector != null;
    }

    @Override public void onSensorChanged(SensorEvent event) {
        if (event == null || event.values == null || event.sensor == null
                || event.sensor.getType() != Sensor.TYPE_ROTATION_VECTOR) return;
        float[] matrix = new float[9];
        float[] adjusted = new float[9];
        float[] orientation = new float[3];
        SensorManager.getRotationMatrixFromVector(matrix, event.values);
        int rotation = windowManager == null ? Surface.ROTATION_0
                : windowManager.getDefaultDisplay().getRotation();
        int axisX = SensorManager.AXIS_X;
        int axisY = SensorManager.AXIS_Y;
        if (rotation == Surface.ROTATION_90) {
            axisX = SensorManager.AXIS_Y;
            axisY = SensorManager.AXIS_MINUS_X;
        } else if (rotation == Surface.ROTATION_180) {
            axisX = SensorManager.AXIS_MINUS_X;
            axisY = SensorManager.AXIS_MINUS_Y;
        } else if (rotation == Surface.ROTATION_270) {
            axisX = SensorManager.AXIS_MINUS_Y;
            axisY = SensorManager.AXIS_X;
        }
        SensorManager.remapCoordinateSystem(matrix, axisX, axisY, adjusted);
        SensorManager.getOrientation(adjusted, orientation);
        float azimuth = (float) Math.toDegrees(orientation[0]);
        if (listener != null) listener.onHeadingChanged(CompassHeading.fromAzimuth(azimuth));
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // No calibration state is persisted; Android may update sensor accuracy at any time.
    }
}
