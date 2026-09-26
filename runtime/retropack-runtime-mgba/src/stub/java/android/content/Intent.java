package android.content;

import java.io.Serializable;

public class Intent {
    public static final String ACTION_SEND = "android.intent.action.SEND";
    public static final String EXTRA_TEXT = "android.intent.extra.TEXT";

    private String action;
    private String type;

    public Intent() {}
    public Intent(String action) { this.action = action; }
    public Intent(Context packageContext, Class<?> cls) {}

    public Intent setAction(String action) { this.action = action; return this; }
    public String getAction() { return action; }

    public Intent setType(String type) { this.type = type; return this; }
    public String getType() { return type; }

    public Intent putExtra(String name, String value) { return this; }
    public Intent putExtra(String name, Serializable value) { return this; }
    public Intent putExtra(String name, boolean value) { return this; }
    public Intent putExtra(String name, int value) { return this; }

    public static Intent createChooser(Intent target, CharSequence title) {
        return target;
    }
}
