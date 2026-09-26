package android.content;

public class Context {
    public static final String WINDOW_SERVICE = "window";

    private java.io.File filesDir = new java.io.File(System.getProperty("java.io.tmpdir"), "retropack_test_files");
    private android.content.res.AssetManager assetManager = new android.content.res.AssetManager();

    public java.io.File getFilesDir() {
        if (!filesDir.exists()) filesDir.mkdirs();
        return filesDir;
    }

    public void setFilesDir(java.io.File dir) {
        this.filesDir = dir;
    }

    public android.content.res.AssetManager getAssets() {
        return assetManager;
    }

    public void setAssetManager(android.content.res.AssetManager am) {
        this.assetManager = am;
    }

    public String getPackageName() {
        return "com.retropack.runtime";
    }

    public Object getSystemService(String name) {
        return null;
    }

    public ComponentName startService(Intent service) {
        return null;
    }

    public boolean stopService(Intent service) {
        return true;
    }

    public java.io.File getExternalFilesDir(String type) {
        return getFilesDir();
    }

    public void startActivity(Intent intent) {}
}
