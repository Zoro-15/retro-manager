package android.hardware;

public class SensorEvent {
    public Sensor sensor;
    public int accuracy;
    public long timestamp;
    public final float[] values;

    public SensorEvent(int valueSize) {
        this.values = new float[valueSize];
    }
}
