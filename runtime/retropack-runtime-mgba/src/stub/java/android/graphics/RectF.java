package android.graphics;

public class RectF {
    public float left;
    public float top;
    public float right;
    public float bottom;

    public RectF() {}
    public RectF(float left, float top, float right, float bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    public boolean contains(float x, float y) {
        return x >= left && x <= right && y >= top && y <= bottom;
    }
}
