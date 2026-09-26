package android.os;

public class VibrationEffect {
    public static final int DEFAULT_AMPLITUDE = -1;
    public static final int EFFECT_CLICK = 0;
    public static final int EFFECT_DOUBLE_CLICK = 1;
    public static final int EFFECT_TICK = 2;
    public static final int EFFECT_HEAVY_CLICK = 5;

    public static VibrationEffect createOneShot(long milliseconds, int amplitude) {
        return new VibrationEffect();
    }

    public static VibrationEffect createPredefined(int effectId) {
        return new VibrationEffect();
    }

    public static VibrationEffect createWaveform(long[] timings, int[] amplitudes, int repeat) {
        return new VibrationEffect();
    }
}
