package com.retropack.runtime.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/**
 * Concrete [AudioSink] implementation backed by Android's low-latency [AudioTrack] API.
 */
class AudioTrackSink(
    override val sampleRate: Int = 44_100,
    override val channelCount: Int = 2,
    bufferSizeBytes: Int = DEFAULT_BUFFER_SIZE
) : AudioSink {

    companion object {
        const val DEFAULT_BUFFER_SIZE = 8_192
    }

    private val track: AudioTrack?

    init {
        val channelMask = if (channelCount == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelMask, encoding)
        val finalBufferSize = maxOf(minBufferSize, bufferSizeBytes)

        track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(encoding)
                        .setChannelMask(channelMask)
                        .build()
                )
                .setBufferSizeInBytes(finalBufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (_: Throwable) {
            null
        }
    }

    override fun write(samples: ShortArray, offset: Int, count: Int): Int {
        val t = track ?: return 0
        return t.write(samples, offset, count, AudioTrack.WRITE_NON_BLOCKING)
    }

    override fun play() {
        track?.play()
    }

    override fun pause() {
        track?.pause()
    }

    override fun flush() {
        track?.flush()
    }

    override fun release() {
        track?.pause()
        track?.flush()
        track?.release()
    }

    override fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0.0f, 1.0f)
        track?.setVolume(clamped)
    }

    override fun getUnderrunCount(): Int {
        return track?.getUnderrunCount() ?: 0
    }
}
