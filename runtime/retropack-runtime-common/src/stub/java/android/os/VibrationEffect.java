package android.os;

public class VibrationEffect {
    public static final int DEFAULT_AMPLITUDE = -1;
    public static final int EFFECT_CLICK = 0;
    public static final int EFFECT_DOUBLE_CLICK = 1;
    public static final int EFFECT_TICK = 2;
    public static final int EFFECT_HEAVY_CLICK = 5;

    public static class Composition {
        public static final int PRIMITIVE_NOOP = 0;
        public static final int PRIMITIVE_CLICK = 1;
        public static final int PRIMITIVE_THUD = 2;
        public static final int PRIMITIVE_SPIN = 3;
        public static final int PRIMITIVE_QUICK_RISE = 4;
        public static final int PRIMITIVE_SLOW_RISE = 5;
        public static final int PRIMITIVE_QUICK_FALL = 6;
        public static final int PRIMITIVE_TICK = 7;
        public static final int PRIMITIVE_LOW_TICK = 8;
        public static final int PRIMITIVE_HEAVY_CLICK = 9;

        public Composition addPrimitive(int primitiveId) { return this; }
        public Composition addPrimitive(int primitiveId, float scale) { return this; }
        public Composition addPrimitive(int primitiveId, float scale, int delay) { return this; }
        public VibrationEffect compose() { return new VibrationEffect(); }
    }

    public static Composition startComposition() {
        return new Composition();
    }

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
