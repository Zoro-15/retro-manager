package android.graphics;

public class Bitmap {
    public enum Config {
        ARGB_8888,
        RGB_565
    }

    private int width;
    private int height;
    private Config config;

    public Bitmap(int width, int height, Config config) {
        this.width = width;
        this.height = height;
        this.config = config;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public Config getConfig() {
        return config;
    }

    public static Bitmap createBitmap(int width, int height, Config config) {
        return new Bitmap(width, height, config);
    }
}
