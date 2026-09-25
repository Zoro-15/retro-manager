package android.view;

import android.content.Context;
import android.util.AttributeSet;

public class SurfaceView extends View {
    public SurfaceView(Context context) { super(context); }
    public SurfaceView(Context context, AttributeSet attrs) { super(context, attrs); }
    public SurfaceHolder getHolder() { return null; }
}
