package android.view;

public interface WindowManager {
    Display getDefaultDisplay();

    public static class LayoutParams {
        public static final int FLAG_KEEP_SCREEN_ON = 0x00000080;
        public static final int FLAG_FULLSCREEN = 0x00000400;
        public static final int MATCH_PARENT = -1;
        public static final int WRAP_CONTENT = -2;

        public static final int LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT = 0;
        public static final int LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES = 1;
        public static final int LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER = 2;
        public static final int LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS = 3;

        public int flags = 0;
        public int layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
        public int preferredDisplayModeId = 0;
    }
}
