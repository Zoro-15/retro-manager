package android.media;

public class AudioTrack {
    public static final int MODE_STREAM = 1;
    public static final int STATE_INITIALIZED = 1;
    public static final int WRITE_BLOCKING = 0;
    public static final int WRITE_NON_BLOCKING = 1;
    public static final int SUCCESS = 0;

    public static int getMinBufferSize(int sampleRateInHz, int channelConfig, int audioFormat) {
        return 4096;
    }

    public static class Builder {
        public Builder setAudioAttributes(AudioAttributes attributes) { return this; }
        public Builder setAudioFormat(AudioFormat format) { return this; }
        public Builder setBufferSizeInBytes(int bufferSizeInBytes) { return this; }
        public Builder setTransferMode(int mode) { return this; }
        public AudioTrack build() { return new AudioTrack(); }
    }

    public int getState() { return STATE_INITIALIZED; }
    public void play() {}
    public void pause() {}
    public void stop() {}
    public void flush() {}
    public void release() {}
    public int setVolume(float gain) { return SUCCESS; }
    public int write(short[] audioData, int offsetInShorts, int sizeInShorts, int writeMode) {
        return sizeInShorts;
    }
    public int getUnderrunCount() { return 0; }
}
