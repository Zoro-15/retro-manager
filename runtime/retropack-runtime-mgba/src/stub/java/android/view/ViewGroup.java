package android.view;

import android.content.Context;
import android.util.AttributeSet;

public abstract class ViewGroup extends View {
    public static class LayoutParams {
        public static final int MATCH_PARENT = -1;
        public static final int WRAP_CONTENT = -2;

        public int width;
        public int height;

        public LayoutParams(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }

    public ViewGroup(Context context) {
        super(context);
    }

    public ViewGroup(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
}
