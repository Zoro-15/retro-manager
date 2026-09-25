package android.app;

import android.content.Context;
import android.os.IBinder;

public abstract class Service extends Context {
    public static final int START_STICKY = 1;
    public static final int START_NOT_STICKY = 2;

    public void onCreate() {}
    public int onStartCommand(Object intent, int flags, int startId) {
        return START_NOT_STICKY;
    }
    public void onDestroy() {}
    public abstract IBinder onBind(Object intent);
}
