package android.view;

import java.util.ArrayList;
import java.util.List;

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

    private int id = 1;
    private int sources;
    private String name = "Xbox Wireless Controller";
    private String descriptor = "xbox_wireless_descriptor";

    public InputDevice(int sources) {
        this.sources = sources;
    }

    public InputDevice(int id, int sources, String name, String descriptor) {
        this.id = id;
        this.sources = sources;
        this.name = name;
        this.descriptor = descriptor;
    }

    public int getId() {
        return id;
    }

    public int getSources() {
        return sources;
    }

    public String getName() {
        return name;
    }

    public String getDescriptor() {
        return descriptor;
    }

    public boolean isVirtual() {
        return false;
    }

    public static int[] getDeviceIds() {
        return new int[]{1};
    }

    public static InputDevice getDevice(int id) {
        return new InputDevice(id, SOURCE_GAMEPAD | SOURCE_JOYSTICK, "Xbox Wireless Controller", "xbox_wireless_descriptor");
    }
}
