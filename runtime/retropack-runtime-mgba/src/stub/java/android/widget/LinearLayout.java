package android.widget;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import java.util.ArrayList;
import java.util.List;

public class LinearLayout extends View {
    public static final int HORIZONTAL = 0;
    public static final int VERTICAL = 1;

    private int orientation = HORIZONTAL;
    private final List<View> children = new ArrayList<>();

    public LinearLayout(Context context) {
        super(context);
    }

    public void setOrientation(int orientation) {
        this.orientation = orientation;
    }

    public int getOrientation() {
        return orientation;
    }

    public void addView(View child) {
        children.add(child);
    }

    public void addView(View child, ViewGroup.LayoutParams params) {
        children.add(child);
    }
}
