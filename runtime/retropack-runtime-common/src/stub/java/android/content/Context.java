package android.content;

import android.hardware.SensorManager;
import android.os.Vibrator;

public class Context {
    public static final String WINDOW_SERVICE = "window";
    public static final String SENSOR_SERVICE = "sensor";
    public static final String VIBRATOR_SERVICE = "vibrator";
    public static final String INPUT_SERVICE = "input";

    private java.io.File filesDir = new java.io.File(System.getProperty("java.io.tmpdir"), "retropack_test_files");
    private android.content.res.AssetManager assetManager = new android.content.res.AssetManager();
    private SensorManager sensorManager = new SensorManager();
    private Vibrator vibrator = new Vibrator();

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

    private android.content.res.Resources resources = new android.content.res.Resources();

    public android.content.res.Resources getResources() {
        return resources;
    }

    public String getPackageName() {
        return "com.retropack.runtime";
    }

    public Object getSystemService(String name) {
        if (SENSOR_SERVICE.equals(name)) return sensorManager;
        if (VIBRATOR_SERVICE.equals(name)) return vibrator;
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

    public static final int MODE_PRIVATE = 0;
    private static final java.util.Map<String, SharedPreferences> sharedPreferencesMap = new java.util.HashMap<>();

    public synchronized SharedPreferences getSharedPreferences(String name, int mode) {
        return sharedPreferencesMap.computeIfAbsent(name, k -> new InMemorySharedPreferences());
    }

    public static synchronized void resetSharedPreferences() {
        sharedPreferencesMap.clear();
    }

    public void startActivity(Intent intent) {}
}
