package android.hardware.input;

import android.os.Handler;
import android.view.InputDevice;

public class InputManager {
    public interface InputDeviceListener {
        void onInputDeviceAdded(int deviceId);
        void onInputDeviceRemoved(int deviceId);
        void onInputDeviceChanged(int deviceId);
    }

    public void registerInputDeviceListener(InputDeviceListener listener, Handler handler) {}
    public void unregisterInputDeviceListener(InputDeviceListener listener) {}

    public int[] getInputDeviceIds() {
        return new int[]{1};
    }

    public InputDevice getInputDevice(int id) {
        return InputDevice.getDevice(id);
    }
}
