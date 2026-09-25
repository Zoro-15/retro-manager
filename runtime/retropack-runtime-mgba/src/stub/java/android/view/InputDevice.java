package android.view;

public class InputDevice {
    public static final int SOURCE_CLASS_MASK = 0x000000ff;
    public static final int SOURCE_CLASS_BUTTON = 0x00000001;
    public static final int SOURCE_CLASS_POINTER = 0x00000002;
    public static final int SOURCE_CLASS_TRACKBALL = 0x00000004;
    public static final int SOURCE_CLASS_POSITION = 0x00000008;
    public static final int SOURCE_CLASS_JOYSTICK = 0x00000010;

    public static final int SOURCE_KEYBOARD = 0x00000100 | SOURCE_CLASS_BUTTON;
    public static final int SOURCE_DPAD = 0x00000200 | SOURCE_CLASS_BUTTON;
    public static final int SOURCE_GAMEPAD = 0x00000400 | SOURCE_CLASS_BUTTON;
    public static final int SOURCE_TOUCHSCREEN = 0x00001000 | SOURCE_CLASS_POINTER;
    public static final int SOURCE_JOYSTICK = 0x01000000 | SOURCE_CLASS_JOYSTICK;

    private int sources;

    public InputDevice(int sources) {
        this.sources = sources;
    }

    public int getSources() {
        return sources;
    }
}
