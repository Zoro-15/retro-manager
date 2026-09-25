package android.view;

import android.content.Context;

public class Window {
    public static final int FEATURE_NO_TITLE = 1;

    private int flags = 0;
    private final View decorView;
    private WindowManager.LayoutParams attributes = new WindowManager.LayoutParams();

    public Window(Context context) {
        this.decorView = new View(context);
    }

    public void addFlags(int flags) {
        this.flags |= flags;
    }

    public void clearFlags(int flags) {
        this.flags &= ~flags;
    }

    public int getFlags() {
        return flags;
    }

    public View getDecorView() {
        return decorView;
    }

    public WindowManager.LayoutParams getAttributes() {
        return attributes;
    }

    public void setAttributes(WindowManager.LayoutParams a) {
        this.attributes = a;
    }

    public boolean requestFeature(int featureId) {
        return true;
    }
}
