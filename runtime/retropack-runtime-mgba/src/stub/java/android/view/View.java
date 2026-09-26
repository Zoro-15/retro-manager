package android.view;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;

public class View {
    public static final int VISIBLE = 0;
    public static final int INVISIBLE = 4;
    public static final int GONE = 8;
    public static final int HAPTIC_FEEDBACK_KEY = 1;

    public static final int SYSTEM_UI_FLAG_FULLSCREEN = 0x00000004;
    public static final int SYSTEM_UI_FLAG_HIDE_NAVIGATION = 0x00000002;
    public static final int SYSTEM_UI_FLAG_IMMERSIVE_STICKY = 0x00001000;
    public static final int SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN = 0x00000400;
    public static final int SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION = 0x00000200;
    public static final int SYSTEM_UI_FLAG_LAYOUT_STABLE = 0x00000100;

    private int systemUiVisibility = 0;
    public void setSystemUiVisibility(int visibility) { this.systemUiVisibility = visibility; }
    public int getSystemUiVisibility() { return systemUiVisibility; }

    private Context context;
    private int width = 1080;
    private int height = 1920;
    private int visibility = VISIBLE;
    private boolean hapticFeedbackEnabled = true;

    public View(Context context) { this.context = context; }
    public View(Context context, AttributeSet attrs) { this.context = context; }

    public Context getContext() { return context; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public void setDimensions(int w, int h) { this.width = w; this.height = h; }
    public void setVisibility(int visibility) { this.visibility = visibility; }
    public int getVisibility() { return visibility; }
    public void invalidate() {}
    public void postInvalidate() {}
    public boolean post(Runnable action) { action.run(); return true; }
    public boolean isHapticFeedbackEnabled() { return hapticFeedbackEnabled; }
    public void setHapticFeedbackEnabled(boolean hapticFeedbackEnabled) { this.hapticFeedbackEnabled = hapticFeedbackEnabled; }
    public boolean performHapticFeedback(int feedbackConstant) { return true; }
    protected void onDraw(Canvas canvas) {}
    public boolean onTouchEvent(MotionEvent event) { return false; }
    public void setBackgroundColor(int color) {}
    public void setPadding(int left, int top, int right, int bottom) {}

    public interface OnClickListener {
        void onClick(View v);
    }

    private OnClickListener onClickListener;
    public void setOnClickListener(OnClickListener l) {
        this.onClickListener = l;
    }
    public boolean performClick() {
        if (onClickListener != null) {
            onClickListener.onClick(this);
            return true;
        }
        return false;
    }
}
