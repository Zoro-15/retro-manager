package android.content;

public class ComponentName {
    private final String pkg;
    private final String cls;

    public ComponentName(String pkg, String cls) {
        this.pkg = pkg;
        this.cls = cls;
    }

    public ComponentName(Context pkg, String cls) {
        this(pkg.getPackageName(), cls);
    }

    public ComponentName(Context pkg, Class<?> cls) {
        this(pkg.getPackageName(), cls.getName());
    }

    public String getPackageName() { return pkg; }
    public String getClassName() { return cls; }
}
