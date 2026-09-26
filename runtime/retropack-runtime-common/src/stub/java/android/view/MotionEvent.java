package android.view;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MotionEvent {
    public static final int ACTION_MASK = 0xff;
    public static final int ACTION_DOWN = 0;
    public static final int ACTION_UP = 1;
    public static final int ACTION_MOVE = 2;
    public static final int ACTION_CANCEL = 3;
    public static final int ACTION_OUTSIDE = 4;
    public static final int ACTION_POINTER_DOWN = 5;
    public static final int ACTION_POINTER_UP = 6;
    public static final int ACTION_POINTER_INDEX_MASK = 0xff00;
    public static final int ACTION_POINTER_INDEX_SHIFT = 8;

    public static final int AXIS_X = 0;
    public static final int AXIS_Y = 1;
    public static final int AXIS_PRESSURE = 2;
    public static final int AXIS_SIZE = 3;
    public static final int AXIS_TOUCH_MAJOR = 4;
    public static final int AXIS_TOUCH_MINOR = 5;
    public static final int AXIS_TOOL_MAJOR = 6;
    public static final int AXIS_TOOL_MINOR = 7;
    public static final int AXIS_ORIENTATION = 8;
    public static final int AXIS_VSCROLL = 9;
    public static final int AXIS_HSCROLL = 10;
    public static final int AXIS_Z = 11;
    public static final int AXIS_RX = 12;
    public static final int AXIS_RY = 13;
    public static final int AXIS_RZ = 14;
    public static final int AXIS_HAT_X = 15;
    public static final int AXIS_HAT_Y = 16;
    public static final int AXIS_LTRIGGER = 17;
    public static final int AXIS_RTRIGGER = 18;
    public static final int AXIS_THROTTLE = 19;
    public static final int AXIS_RUDDER = 20;
    public static final int AXIS_WHEEL = 21;
    public static final int AXIS_GAS = 22;
    public static final int AXIS_BRAKE = 23;
    public static final int AXIS_DISTANCE = 24;
    public static final int AXIS_TILT = 25;

    public static class PointerCoords {
        public int id;
        public float x;
        public float y;
        public Map<Integer, Float> axes = new HashMap<>();

        public PointerCoords(int id, float x, float y) {
            this.id = id;
            this.x = x;
            this.y = y;
        }
    }

    private int action;
    private int source;
    private List<PointerCoords> pointers = new ArrayList<>();

    public MotionEvent(int action, float x, float y) {
        this.action = action;
        this.source = InputDevice.SOURCE_TOUCHSCREEN;
        this.pointers.add(new PointerCoords(0, x, y));
    }

    public MotionEvent(int action, int source, List<PointerCoords> pointers) {
        this.action = action;
        this.source = source;
        this.pointers = new ArrayList<>(pointers);
    }

    public int getAction() {
        return action;
    }

    public int getActionMasked() {
        return action & ACTION_MASK;
    }

    public int getActionIndex() {
        return (action & ACTION_POINTER_INDEX_MASK) >> ACTION_POINTER_INDEX_SHIFT;
    }

    public int getPointerCount() {
        return pointers.size();
    }

    public int getPointerId(int pointerIndex) {
        if (pointerIndex >= 0 && pointerIndex < pointers.size()) {
            return pointers.get(pointerIndex).id;
        }
        return -1;
    }

    public int findPointerIndex(int pointerId) {
        for (int i = 0; i < pointers.size(); i++) {
            if (pointers.get(i).id == pointerId) return i;
        }
        return -1;
    }

    public float getX() {
        return getX(0);
    }

    public float getY() {
        return getY(0);
    }

    public float getX(int pointerIndex) {
        if (pointerIndex >= 0 && pointerIndex < pointers.size()) {
            return pointers.get(pointerIndex).x;
        }
        return 0f;
    }

    public float getY(int pointerIndex) {
        if (pointerIndex >= 0 && pointerIndex < pointers.size()) {
            return pointers.get(pointerIndex).y;
        }
        return 0f;
    }

    public float getAxisValue(int axis) {
        return getAxisValue(axis, 0);
    }

    public float getAxisValue(int axis, int pointerIndex) {
        if (pointerIndex >= 0 && pointerIndex < pointers.size()) {
            PointerCoords p = pointers.get(pointerIndex);
            if (axis == AXIS_X) return p.x;
            if (axis == AXIS_Y) return p.y;
            Float val = p.axes.get(axis);
            return val != null ? val : 0f;
        }
        return 0f;
    }

    public int getSource() {
        return source;
    }

    public boolean isFromSource(int source) {
        return (this.source & source) == source;
    }

    public static MotionEvent createTouch(int action, float x, float y) {
        return new MotionEvent(action, x, y);
    }

    public static MotionEvent createMultiTouch(int actionMasked, int actionIndex, List<PointerCoords> pointers) {
        int action = actionMasked | (actionIndex << ACTION_POINTER_INDEX_SHIFT);
        return new MotionEvent(action, InputDevice.SOURCE_TOUCHSCREEN, pointers);
    }

    public static MotionEvent createJoystick(Map<Integer, Float> axes) {
        PointerCoords p = new PointerCoords(0, axes.getOrDefault(AXIS_X, 0f), axes.getOrDefault(AXIS_Y, 0f));
        p.axes.putAll(axes);
        List<PointerCoords> list = new ArrayList<>();
        list.add(p);
        return new MotionEvent(ACTION_MOVE, InputDevice.SOURCE_JOYSTICK, list);
    }

    public static MotionEvent obtain(long downTime, long eventTime, int action, float x, float y, int metaState) {
        return createTouch(action, x, y);
    }

    public static MotionEvent obtain(long downTime, long eventTime, int action, kotlin.Pair<Float, Float>[] pointers) {
        List<PointerCoords> list = new ArrayList<>();
        if (pointers != null) {
            for (int i = 0; i < pointers.length; i++) {
                list.add(new PointerCoords(i, pointers[i].getFirst(), pointers[i].getSecond()));
            }
        }
        return new MotionEvent(action, InputDevice.SOURCE_TOUCHSCREEN, list);
    }
}
