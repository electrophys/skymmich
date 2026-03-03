package red.henry.skymmich;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

public class MotionWakeController implements SensorEventListener {

    private static final String TAG = "MotionWake";
    private static final float MOTION_THRESHOLD = 0.5f;      // m/s² delta for wake
    private static final float TAP_THRESHOLD = 3.0f;          // m/s² delta for tap
    private static final int TAP_COUNT_REQUIRED = 3;
    private static final long TAP_WINDOW_MS = 1000;
    private static final long DIM_TIMEOUT_MS = 5 * 60 * 1000;   // 5 min
    private static final long SLEEP_TIMEOUT_MS = 10 * 60 * 1000; // 10 min

    public interface Callback {
        void onBrightnessChange(float brightness);
        void onTripleTap();
    }

    private final SensorManager sensorManager;
    private final Callback callback;

    private float baseX, baseY, baseZ;
    private boolean baselineSet = false;
    private long lastMotionTime;

    // Tap detection
    private final long[] tapTimes = new long[TAP_COUNT_REQUIRED];
    private int tapIndex = 0;

    // Current brightness state
    private float currentBrightness = 1.0f;

    public MotionWakeController(SensorManager sensorManager, Callback callback) {
        this.sensorManager = sensorManager;
        this.callback = callback;
        this.lastMotionTime = System.currentTimeMillis();
    }

    public void start() {
        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
            Log.d(TAG, "Accelerometer registered");
        } else {
            Log.w(TAG, "No accelerometer found");
        }
    }

    public void stop() {
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;

        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];

        if (!baselineSet) {
            baseX = x;
            baseY = y;
            baseZ = z;
            baselineSet = true;
            return;
        }

        float dx = x - baseX;
        float dy = y - baseY;
        float dz = z - baseZ;
        float delta = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

        long now = System.currentTimeMillis();

        // Detect tap
        if (delta > TAP_THRESHOLD) {
            tapTimes[tapIndex % TAP_COUNT_REQUIRED] = now;
            tapIndex++;

            if (tapIndex >= TAP_COUNT_REQUIRED) {
                long oldest = tapTimes[(tapIndex) % TAP_COUNT_REQUIRED];
                if (now - oldest <= TAP_WINDOW_MS) {
                    Log.d(TAG, "Triple-tap detected");
                    callback.onTripleTap();
                    tapIndex = 0; // Reset to avoid re-triggering
                }
            }
        }

        // Detect motion
        if (delta > MOTION_THRESHOLD) {
            lastMotionTime = now;
            if (currentBrightness < 1.0f) {
                currentBrightness = 1.0f;
                callback.onBrightnessChange(1.0f);
            }
        }

        // Update baseline slowly
        baseX = baseX * 0.95f + x * 0.05f;
        baseY = baseY * 0.95f + y * 0.05f;
        baseZ = baseZ * 0.95f + z * 0.05f;
    }

    /**
     * Call periodically (e.g. from slideshow timer) to check idle timeouts.
     */
    public void checkIdleTimeout() {
        long idle = System.currentTimeMillis() - lastMotionTime;

        if (idle >= SLEEP_TIMEOUT_MS && currentBrightness != 0.01f) {
            currentBrightness = 0.01f;
            callback.onBrightnessChange(0.01f);
        } else if (idle >= DIM_TIMEOUT_MS && currentBrightness != 0.15f && currentBrightness != 0.01f) {
            currentBrightness = 0.15f;
            callback.onBrightnessChange(0.15f);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not needed
    }
}
