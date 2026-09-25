package com.retropack.runtime.audio

/**
 * Abstraction for 16-bit PCM audio output, enabling test-driven verification on pure JVM.
 */
interface AudioSink {
    val sampleRate: Int
    val channelCount: Int

    /**
     * Writes 16-bit interleaved PCM samples to the audio device.
     *
     * @param samples Array containing PCM samples.
     * @param offset Starting index in [samples].
     * @param count Number of samples (shorts) to write.
     * @return Actual number of samples written.
     */
    fun write(samples: ShortArray, offset: Int, count: Int): Int

    fun play()
    fun pause()
    fun flush()
    fun release()
    fun setVolume(volume: Float)
    fun getUnderrunCount(): Int
}
