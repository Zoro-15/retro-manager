package android.app;

import android.content.ComponentCallbacks2;
import android.content.Context;
import android.os.Bundle;
import android.view.Display;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

public class Activity extends Context implements ComponentCallbacks2 {
    public static final int TRIM_MEMORY_COMPLETE = ComponentCallbacks2.TRIM_MEMORY_COMPLETE;
    public static final int TRIM_MEMORY_MODERATE = ComponentCallbacks2.TRIM_MEMORY_MODERATE;
    public static final int TRIM_MEMORY_BACKGROUND = ComponentCallbacks2.TRIM_MEMORY_BACKGROUND;
    public static final int TRIM_MEMORY_UI_HIDDEN = ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN;
    public static final int TRIM_MEMORY_RUNNING_CRITICAL = ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL;
    public static final int TRIM_MEMORY_RUNNING_LOW = ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW;
    public static final int TRIM_MEMORY_RUNNING_MODERATE = ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE;
    private Window window;
    private View contentView;
    private boolean isFinishing = false;
    private boolean isDestroyed = false;

    public Activity() {
        this.window = new Window(this);
    }

    public Window getWindow() {
        return window;
    }

    public WindowManager getWindowManager() {
        return new WindowManager() {
            @Override
            public Display getDefaultDisplay() {
                return new Display();
            }
        };
    }

    public boolean requestWindowFeature(int featureId) {
        return window.requestFeature(featureId);
    }

    public void setContentView(View view) {
        this.contentView = view;
    }

    public View getContentView() {
        return contentView;
    }

    public void runOnUiThread(Runnable action) {
        action.run();
    }

    public void finish() {
        isFinishing = true;
    }

    public boolean isFinishing() {
        return isFinishing;
    }

    public boolean isDestroyed() {
        return isDestroyed;
    }

    protected void onCreate(Bundle savedInstanceState) {}
    protected void onResume() {}
    protected void onPause() {}
    protected void onStop() {}
    protected void onDestroy() {
        isDestroyed = true;
    }

    public void onWindowFocusChanged(boolean hasFocus) {}

    public void onConfigurationChanged(android.content.res.Configuration newConfig) {}
    public void onTrimMemory(int level) {}
    public void onLowMemory() {}

    public boolean dispatchKeyEvent(KeyEvent event) {
        return false;
    }

    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        return false;
    }
}
