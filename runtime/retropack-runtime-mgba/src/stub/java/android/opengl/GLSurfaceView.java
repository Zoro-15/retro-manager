package android.opengl;

import android.content.Context;
import android.util.AttributeSet;
import android.view.SurfaceView;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class GLSurfaceView extends SurfaceView {
    public static final int RENDERMODE_WHEN_DIRTY = 0;
    public static final int RENDERMODE_CONTINUOUSLY = 1;

    public interface Renderer {
        void onSurfaceCreated(GL10 gl, EGLConfig config);
        void onSurfaceChanged(GL10 gl, int width, int height);
        void onDrawFrame(GL10 gl);
    }

    public GLSurfaceView(Context context) { super(context); }
    public GLSurfaceView(Context context, AttributeSet attrs) { super(context, attrs); }

    public void setEGLContextClientVersion(int version) {}
    public void setRenderer(Renderer renderer) {}
    public void setRenderMode(int renderMode) {}
    public int getRenderMode() { return RENDERMODE_WHEN_DIRTY; }
    public void requestRender() {}
    public void queueEvent(Runnable r) { r.run(); }
    public void onPause() {}
    public void onResume() {}
}
