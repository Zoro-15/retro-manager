package com.retropack.runtime.host

import com.retropack.runtime.audio.RetroAudioPlayer
import com.retropack.runtime.core.EmulationEngine
import com.retropack.runtime.save.SaveManager
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Dedicated 60 FPS emulation loop thread coordinating frame stepping,
 * audio playback synchronization, periodic SRAM flushes, and frame pacing.
 */
class EmulationLoop(
    private val engine: EmulationEngine,
    private val audioPlayer: RetroAudioPlayer? = null,
    private val saveManagerSupplier: () -> SaveManager? = { null },
    var onFrameComplete: (() -> Unit)? = null
) : Runnable {

    constructor(
        engine: EmulationEngine,
        audioPlayer: RetroAudioPlayer?,
        saveManager: SaveManager?,
        onFrameComplete: (() -> Unit)? = null
    ) : this(engine, audioPlayer, { saveManager }, onFrameComplete)

    companion object {
        /** GBA hardware refresh rate: ~59.7275 Hz (16,742,706 nanoseconds per frame). */
        const val FRAME_DURATION_NANOS: Long = 16_742_706L
    }

    private val lock = ReentrantLock()
    private val pauseCondition = lock.newCondition()

    @Volatile
    private var isRunning: Boolean = false

    @Volatile
    private var isPaused: Boolean = false

    private var workerThread: Thread? = null

    val running: Boolean get() = isRunning
    val paused: Boolean get() = isPaused

    /**
     * Executes a single frame step synchronously.
     * Useful for deterministic testing or frame-by-frame debug.
     */
    fun stepSingleFrame(): Boolean {
        val success = engine.stepFrame()
        if (success) {
            audioPlayer?.pumpAudio()
            saveManagerSupplier()?.periodicFlush(System.currentTimeMillis())
            onFrameComplete?.invoke()
        }
        return success
    }

    /**
     * Starts the background emulation loop thread.
     */
    fun start() {
        lock.withLock {
            if (isRunning) return
            isRunning = true
            isPaused = false
            workerThread = Thread(this, "RetroPack-EmulationThread").apply {
                isDaemon = true
                start()
            }
        }
    }

    /**
     * Pauses the loop execution, putting the worker thread to sleep.
     */
    fun pause() {
        lock.withLock {
            isPaused = true
        }
    }

    /**
     * Resumes the loop execution if paused.
     */
    fun resume() {
        lock.withLock {
            if (isPaused) {
                isPaused = false
                pauseCondition.signalAll()
            }
        }
    }

    /**
     * Halts the emulation loop and joins the worker thread.
     */
    fun stop() {
        lock.withLock {
            isRunning = false
            isPaused = false
            pauseCondition.signalAll()
        }
        workerThread?.interrupt()
        try {
            workerThread?.join(500)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        workerThread = null
    }

    override fun run() {
        var nextFrameNanos = System.nanoTime()

        while (isRunning) {
            lock.withLock {
                while (isPaused && isRunning) {
                    try {
                        pauseCondition.await()
                    } catch (_: InterruptedException) {
                        if (!isRunning) return
                    }
                    nextFrameNanos = System.nanoTime()
                }
            }

            if (!isRunning) break

            val frameSuccess = stepSingleFrame()
            if (!frameSuccess) {
                // If frame failed (e.g. engine error or stopped), sleep briefly
                try {
                    Thread.sleep(10)
                } catch (_: InterruptedException) {
                    break
                }
                continue
            }

            // Frame pacing
            nextFrameNanos += FRAME_DURATION_NANOS
            val nowNanos = System.nanoTime()
            val sleepNanos = nextFrameNanos - nowNanos

            if (sleepNanos > 1_000_000L) { // More than 1ms
                val millis = sleepNanos / 1_000_000L
                val nanos = (sleepNanos % 1_000_000L).toInt()
                try {
                    Thread.sleep(millis, nanos)
                } catch (_: InterruptedException) {
                    break
                }
            } else if (sleepNanos < -FRAME_DURATION_NANOS * 3) {
                // Fallen more than 3 frames behind; reset timing baseline to prevent drift spiraling
                nextFrameNanos = nowNanos
            }
        }
    }
}
