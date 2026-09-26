package android.os;

public class Build {
    public static final String MANUFACTURER = "Generic";
    public static final String MODEL = "Emulator";
    public static final String[] SUPPORTED_ABIS = new String[]{"arm64-v8a", "armeabi-v7a", "x86_64", "x86"};

    public static class VERSION {
        public static final int SDK_INT = 35;
    }

    public static class VERSION_CODES {
        public static final int O = 26;
        public static final int O_MR1 = 27;
        public static final int P = 28;
        public static final int Q = 29;
        public static final int R = 30;
        public static final int S = 31;
        public static final int TIRAMISU = 33;
        public static final int UPSIDE_DOWN_CAKE = 34;
        public static final int VANILLA_ICE_CREAM = 35;
    }
}
