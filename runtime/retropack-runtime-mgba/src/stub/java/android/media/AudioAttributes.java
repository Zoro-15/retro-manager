package android.media;

public class AudioAttributes {
    public static final int USAGE_GAME = 14;
    public static final int CONTENT_TYPE_MUSIC = 2;

    public static class Builder {
        public Builder setUsage(int usage) { return this; }
        public Builder setContentType(int contentType) { return this; }
        public AudioAttributes build() { return new AudioAttributes(); }
    }
}
