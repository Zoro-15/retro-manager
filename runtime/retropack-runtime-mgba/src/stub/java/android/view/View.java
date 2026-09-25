package android.view;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;

public class View {
    public static final int VISIBLE = 0;
    public static final int INVISIBLE = 4;
    public static final int GONE = 8;
    public static final int HAPTIC_FEEDBACK_KEY = 1;

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
}
