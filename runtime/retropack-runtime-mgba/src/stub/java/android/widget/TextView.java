package android.widget;

import android.content.Context;
import android.view.View;

public class TextView extends View {
    private CharSequence text = "";

    public TextView(Context context) {
        super(context);
    }

    public void setText(CharSequence text) {
        this.text = text;
    }

    public CharSequence getText() {
        return text;
    }

    public void setTextSize(float size) {}
    public float getTextSize() { return 14f; }
    public void setTextColor(int color) {}
    public int getTextColor() { return 0; }
    public void setTypeface(Object tf, int style) {}
}
