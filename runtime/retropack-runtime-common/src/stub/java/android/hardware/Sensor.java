package android.hardware;

public class Sensor {
    public static final int TYPE_ACCELEROMETER = 1;
    public static final int TYPE_GYROSCOPE = 4;
    public static final int TYPE_ROTATION_VECTOR = 11;

    private int type;
    private String name;

    public Sensor(int type, String name) {
        this.type = type;
        this.name = name;
    }

    public int getType() {
        return type;
    }

    public String getName() {
        return name;
    }
}
