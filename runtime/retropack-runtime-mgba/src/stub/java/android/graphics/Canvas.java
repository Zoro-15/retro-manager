package android.graphics;

public class Canvas {
    public void drawColor(int color) {}
    public void drawCircle(float cx, float cy, float radius, Paint paint) {}
    public void drawRect(RectF rect, Paint paint) {}
    public void drawRect(float left, float top, float right, float bottom, Paint paint) {}
    public void drawRoundRect(RectF rect, float rx, float ry, Paint paint) {}
    public void drawRoundRect(float left, float top, float right, float bottom, float rx, float ry, Paint paint) {}
    public void drawLine(float startX, float startY, float stopX, float stopY, Paint paint) {}
    public void drawLines(float[] pts, Paint paint) {}
    public void drawLines(float[] pts, int offset, int count, Paint paint) {}
    public void drawText(String text, float x, float y, Paint paint) {}
    public int save() { return 0; }
    public void restore() {}
    public void translate(float dx, float dy) {}
    public void scale(float sx, float sy) {}
    public void scale(float sx, float sy, float px, float py) {}
    public void rotate(float degrees) {}
    public void rotate(float degrees, float px, float py) {}
    public boolean clipRect(RectF rect) { return true; }
    public boolean clipRect(float left, float top, float right, float bottom) { return true; }
}
