package android.hardware;

import java.util.Collections;
import java.util.List;

public class SensorManager {
    public static final int SENSOR_DELAY_FASTEST = 0;
    public static final int SENSOR_DELAY_GAME = 1;
    public static final int SENSOR_DELAY_UI = 2;
    public static final int SENSOR_DELAY_NORMAL = 3;

    public static final float GRAVITY_EARTH = 9.80665f;

    private Sensor defaultAccelerometer = new Sensor(Sensor.TYPE_ACCELEROMETER, "Mock Accelerometer");
    private Sensor defaultGyroscope = new Sensor(Sensor.TYPE_GYROSCOPE, "Mock Gyroscope");

    public Sensor getDefaultSensor(int type) {
        if (type == Sensor.TYPE_ACCELEROMETER) return defaultAccelerometer;
        if (type == Sensor.TYPE_GYROSCOPE) return defaultGyroscope;
        return null;
    }

    public List<Sensor> getSensorList(int type) {
        Sensor s = getDefaultSensor(type);
        return s != null ? Collections.singletonList(s) : Collections.emptyList();
    }

    public boolean registerListener(SensorEventListener listener, Sensor sensor, int samplingPeriodUs) {
        return true;
    }

    public void unregisterListener(SensorEventListener listener, Sensor sensor) {}

    public void unregisterListener(SensorEventListener listener) {}
}
