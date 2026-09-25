package android.view;

public class KeyEvent {
    public static final int ACTION_DOWN = 0;
    public static final int ACTION_UP = 1;

    public static final int KEYCODE_BUTTON_A = 96;
    public static final int KEYCODE_BUTTON_B = 97;
    public static final int KEYCODE_BUTTON_C = 98;
    public static final int KEYCODE_BUTTON_X = 99;
    public static final int KEYCODE_BUTTON_Y = 100;
    public static final int KEYCODE_BUTTON_Z = 101;
    public static final int KEYCODE_BUTTON_L1 = 102;
    public static final int KEYCODE_BUTTON_R1 = 103;
    public static final int KEYCODE_BUTTON_L2 = 104;
    public static final int KEYCODE_BUTTON_R2 = 105;
    public static final int KEYCODE_BUTTON_START = 108;
    public static final int KEYCODE_BUTTON_SELECT = 109;

    public static final int KEYCODE_DPAD_UP = 19;
    public static final int KEYCODE_DPAD_DOWN = 20;
    public static final int KEYCODE_DPAD_LEFT = 21;
    public static final int KEYCODE_DPAD_RIGHT = 22;
    public static final int KEYCODE_DPAD_CENTER = 23;

    public static final int KEYCODE_X = 52;
    public static final int KEYCODE_Z = 54;
    public static final int KEYCODE_ENTER = 66;
    public static final int KEYCODE_DEL = 67;
    public static final int KEYCODE_SPACE = 62;

    private int action;
    private int keyCode;
    private int repeatCount;
    private int source;

    public KeyEvent(int action, int keyCode) {
        this.action = action;
        this.keyCode = keyCode;
        this.repeatCount = 0;
        this.source = InputDevice.SOURCE_GAMEPAD;
    }

    public KeyEvent(int action, int keyCode, int source) {
        this.action = action;
        this.keyCode = keyCode;
        this.repeatCount = 0;
        this.source = source;
    }

    public int getAction() { return action; }
    public int getKeyCode() { return keyCode; }
    public int getRepeatCount() { return repeatCount; }
    public int getSource() { return source; }
    public boolean isFromSource(int source) { return (this.source & source) == source; }
}
