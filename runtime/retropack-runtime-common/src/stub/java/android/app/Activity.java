package android.app;

import android.content.Context;
import android.os.Bundle;
import android.view.Display;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

public class Activity extends Context {
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

    public boolean dispatchKeyEvent(KeyEvent event) {
        return false;
    }

    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        return false;
    }
}
