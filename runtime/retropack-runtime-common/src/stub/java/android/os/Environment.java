package android.os;

import java.io.File;

public class Environment {
    public static final String DIRECTORY_DOWNLOADS = "Download";
    public static final String DIRECTORY_DOCUMENTS = "Documents";
    public static final String DIRECTORY_PICTURES = "Pictures";
    public static final String DIRECTORY_DCIM = "DCIM";
    public static final String DIRECTORY_MOVIES = "Movies";
    public static final String DIRECTORY_MUSIC = "Music";

    public static File getExternalStorageDirectory() {
        return new File(System.getProperty("user.home", "/sdcard"));
    }

    public static File getExternalStoragePublicDirectory(String type) {
        return new File(getExternalStorageDirectory(), type);
    }
}
