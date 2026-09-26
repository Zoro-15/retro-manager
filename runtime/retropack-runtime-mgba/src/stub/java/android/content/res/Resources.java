package android.content.res;

import android.util.DisplayMetrics;

public class Resources {
    private final DisplayMetrics displayMetrics = new DisplayMetrics();
    private final Configuration configuration = new Configuration();

    public DisplayMetrics getDisplayMetrics() {
        return displayMetrics;
    }

    public Configuration getConfiguration() {
        return configuration;
    }
}
