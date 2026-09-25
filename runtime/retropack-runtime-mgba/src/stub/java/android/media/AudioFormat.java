package android.media;

public class AudioFormat {
    // AOSP values (AudioFormat.CHANNEL_OUT_MONO = 0x4, STEREO = 0xC).
    public static final int CHANNEL_OUT_MONO = 4;
    public static final int CHANNEL_OUT_STEREO = 12;
    public static final int ENCODING_PCM_16BIT = 2;

    public static class Builder {
        public Builder setSampleRate(int sampleRate) { return this; }
        public Builder setChannelMask(int channelMask) { return this; }
        public Builder setEncoding(int encoding) { return this; }
        public AudioFormat build() { return new AudioFormat(); }
    }
}
