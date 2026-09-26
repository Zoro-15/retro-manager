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

    @Volatile
    var speedMultiplier: Int = 1
        set(value) {
            field = value.coerceIn(1, 32)
        }

    @Volatile
    var muteAudioOnFastForward: Boolean = true

    private var workerThread: Thread? = null

    /**
     * Executes a single frame step synchronously.
     * Useful for deterministic testing or frame-by-frame debug.
     */
    fun stepSingleFrame(): Boolean {
        val success = engine.stepFrame()
        if (success) {
            // Mute audio during fast-forward if requested to prevent screeching audio
            if (!(muteAudioOnFastForward && speedMultiplier > 1)) {
                audioPlayer?.pumpAudio()
            }
            saveManagerSupplier()?.periodicFlush(System.currentTimeMillis())
            onFrameComplete?.invoke()
        }
        return success
    }

    /**
     * Starts the background emulation loop thread.
     * If a previous worker is still alive (e.g. a stuck native frame outlived
     * [stop]), it is joined first so two steppers can never run concurrently.
     */
    fun start() {
        lock.withLock {
            if (isRunning) return
            workerThread?.let { stale ->
                if (stale.isAlive) {
                    // Best effort: do not hold the loop lock while joining.
                    lock.unlock()
                    try {
                        stale.join(2000)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                    } finally {
                        lock.lock()
                    }
                    if (stale.isAlive) return
                }
                workerThread = null
            }
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
            if (!isRunning) {
                start()
                return
            }
            if (isPaused) {
                isPaused = false
                pauseCondition.signalAll()
            }
        }
    }

    /**
     * Halts the emulation loop and joins the worker thread.
     * Escalates from interrupt to a bounded second join instead of dropping
     * the handle after 500ms (which leaked stuck workers and allowed a second
     * stepper on restart). The handle is cleared only once the thread is dead;
     * otherwise it is retained so a later [start] can join it first.
     */
    fun stop() {
        val worker: Thread?
        lock.withLock {
            isRunning = false
            isPaused = false
            pauseCondition.signalAll()
            worker = workerThread
        }
        worker?.interrupt()
        try {
            worker?.join(2000)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        if (worker == null) return
        if (worker.isAlive) {
            // Native frame still holding out: interrupt once more and wait.
            worker.interrupt()
            try {
                worker.join(2000)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        lock.withLock {
            if (workerThread === worker && !worker.isAlive) {
                workerThread = null
            }
            // Otherwise: a stuck worker keeps its handle so start() joins it
            // first, and a concurrently started worker is left alone.
        }
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
            val targetFrameNanos = when {
                speedMultiplier >= 16 -> 0L // Unthrottled
                speedMultiplier > 1 -> FRAME_DURATION_NANOS / speedMultiplier
                else -> FRAME_DURATION_NANOS
            }

            if (targetFrameNanos > 0L) {
                nextFrameNanos += targetFrameNanos
                val nowNanos = System.nanoTime()
                val sleepNanos = nextFrameNanos - nowNanos

                if (sleepNanos > 2_000_000L) { // More than 2ms: coarse sleep saving CPU cycles
                    val millis = (sleepNanos - 1_000_000L) / 1_000_000L
                    try {
                        Thread.sleep(millis)
                    } catch (_: InterruptedException) {
                        break
                    }
                }

                // Sub-millisecond high-precision wait to eliminate OS scheduler oversleep jitter
                while (isRunning && !isPaused && System.nanoTime() < nextFrameNanos) {
                    Thread.onSpinWait()
                }

                if (System.nanoTime() - nextFrameNanos > targetFrameNanos * 3) {
                    // Fallen more than 3 frames behind; reset timing baseline to prevent drift spiraling
                    nextFrameNanos = System.nanoTime()
                }
            } else {
                nextFrameNanos = System.nanoTime()
            }
        }
    }
}
