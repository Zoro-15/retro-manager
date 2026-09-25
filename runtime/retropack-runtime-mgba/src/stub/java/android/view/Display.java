package android.view;

public class Display {
    public static class Mode {
        private final int modeId;
        private final int width;
        private final int height;
        private final float refreshRate;

        public Mode(int modeId, int width, int height, float refreshRate) {
            this.modeId = modeId;
            this.width = width;
            this.height = height;
            this.refreshRate = refreshRate;
        }

        public int getModeId() { return modeId; }
        public int getPhysicalWidth() { return width; }
        public int getPhysicalHeight() { return height; }
        public float getRefreshRate() { return refreshRate; }
    }

    private Mode mode = new Mode(1, 1080, 2400, 60.0f);
    private Mode[] supportedModes = new Mode[] {
        new Mode(1, 1080, 2400, 60.0f),
        new Mode(2, 1080, 2400, 120.0f)
    };

    public Mode getMode() { return mode; }
    public Mode[] getSupportedModes() { return supportedModes; }
}
