package android.graphics;

public class Paint {
    public static final int ANTI_ALIAS_FLAG = 1;

    public enum Style {
        FILL,
        STROKE,
        FILL_AND_STROKE
    }

    public enum Align {
        LEFT,
        CENTER,
        RIGHT
    }

    private int color = Color.BLACK;
    private int alpha = 255;
    private Style style = Style.FILL;
    private float strokeWidth = 1.0f;
    private float textSize = 12.0f;
    private Align textAlign = Align.LEFT;
    private boolean antiAlias = true;

    public Paint() {}
    public Paint(int flags) {}

    public void setAntiAlias(boolean aa) { this.antiAlias = aa; }
    public boolean isAntiAlias() { return antiAlias; }

    public int getColor() { return color; }
    public void setColor(int color) { this.color = color; }

    public int getAlpha() { return alpha; }
    public void setAlpha(int a) { this.alpha = a; }

    public Style getStyle() { return style; }
    public void setStyle(Style style) { this.style = style; }

    public float getStrokeWidth() { return strokeWidth; }
    public void setStrokeWidth(float width) { this.strokeWidth = width; }

    public float getTextSize() { return textSize; }
    public void setTextSize(float textSize) { this.textSize = textSize; }

    public Align getTextAlign() { return textAlign; }
    public void setTextAlign(Align align) { this.textAlign = align; }
}
