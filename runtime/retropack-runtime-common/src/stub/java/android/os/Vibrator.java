package android.os;

public class Vibrator {
    public boolean hasVibrator() {
        return true;
    }

    public boolean hasAmplitudeControl() {
        return true;
    }

    public boolean areAllPrimitivesSupported(int... primitiveIds) {
        return true;
    }

    public void vibrate(long milliseconds) {}

    public void vibrate(long[] pattern, int repeat) {}

    public void vibrate(VibrationEffect effect) {}

    public void vibrate(CombinedVibration effect) {}

    public void cancel() {}
}
