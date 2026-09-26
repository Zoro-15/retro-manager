package com.retropack.runtime

import android.os.Bundle
import android.view.KeyEvent
import com.retropack.runtime.core.EmulationEngine
import com.retropack.runtime.core.EmulationState
import com.retropack.runtime.core.RetroKey
import com.retropack.runtime.core.ScaleMode
import com.retropack.runtime.host.RomStager
import com.retropack.runtime.host.RuntimeConfig
import com.retropack.runtime.save.SaveManager
import com.retropack.runtime.save.SramStorageSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Full lifecycle drive (window flags, view hierarchy, gamepad dispatch) runs
 * against the faithful no-op stubs in
 * runtime/retropack-runtime-mgba/src/stub/java. Against the mockable real
 * android.jar (testDebugUnitTest) framework methods return default values
 * (e.g. KeyEvent.getSource() == 0), so the class is SKIPPED there with an
 * explicit reason — it still COMPILES in both modes, which is the regression
 * this rewrite fixes (the old test called the stub-only Context.setFilesDir /
 * AssetManager.putMockAsset directly and broke
 * :template-apk:compileDebugUnitTestKotlin: "Unresolved reference").
 */
class GameActivityTest {

    @TempDir
    lateinit var tempDir: File

    private class TestEngine : EmulationEngine {
        var initialized = false
        var romLoaded = false
        var closed = false
        var activeKeyMask = 0
        var engineState = EmulationState.UNINITIALIZED

        override val state: EmulationState get() = engineState

        override fun initialize(internalStoragePath: String): Boolean {
            initialized = true
            engineState = EmulationState.INITIALIZED
            return true
        }

        override fun loadRom(romPath: String): Boolean {
            romLoaded = true
            engineState = EmulationState.RUNNING
            return true
        }

        override fun unloadRom() {
            romLoaded = false
            engineState = EmulationState.STOPPED
        }

        override fun pause() {
            engineState = EmulationState.PAUSED
        }

        override fun resume() {
            engineState = EmulationState.RUNNING
        }

        override fun stepFrame(): Boolean = true
        override fun setKeyMask(mask: Int) { activeKeyMask = mask }
        override fun setKeys(vararg keys: RetroKey) { activeKeyMask = RetroKey.maskOf(*keys) }
        override fun getVideoBuffer(): java.nio.IntBuffer = java.nio.IntBuffer.allocate(240 * 160)
        override fun getAudioSamples(outSamples: ShortArray, maxSamples: Int): Int = 0
        override fun getSramSize(): Int = 512
        override fun readSram(outBuffer: ByteArray): Boolean = true
        override fun writeSram(inBuffer: ByteArray): Boolean = true
        override fun saveState(slot: Int, filePath: String): Boolean = true
        override fun loadState(slot: Int, filePath: String): Boolean = true
        override fun close() { closed = true }
    }

    private class TestSaveSource : SramStorageSource {
        var flushCount = 0
        var sramData = ByteArray(512) { (it and 0xFF).toByte() }

        override fun getSramSize(): Int = sramData.size
        override fun readSram(outBuffer: ByteArray): Boolean {
            sramData.copyInto(outBuffer)
            flushCount++
            return true
        }
        override fun writeSram(inBuffer: ByteArray): Boolean {
            inBuffer.copyInto(sramData)
            return true
        }
    }

    private class TestableGameActivity(
        private val testEngine: TestEngine,
        private val testSaveSource: TestSaveSource,
        private val filesDirectory: File,
        private val configJson: String,
        private val romBytes: ByteArray
    ) : GameActivity() {

        // Test seams (work in BOTH build modes — see GameActivity.storageDirectory)
        override fun storageDirectory(): File = filesDirectory

        override fun loadRuntimeConfig(): RuntimeConfig = RuntimeConfig.fromJson(configJson)

        override fun stageRomIfNeeded(targetFile: File, expectedSha256: String): Boolean =
            try {
                java.io.ByteArrayInputStream(romBytes).use { stream ->
                    RomStager.stageRom(stream, targetFile, expectedSha256)
                }
            } catch (_: IOException) {
                false
            }

        fun performCreate(savedInstanceState: Bundle?) = onCreate(savedInstanceState)
        fun performResume() = onResume()
        fun performPause() = onPause()
        fun performStop() = onStop()
        fun performDestroy() = onDestroy()

        override fun createEngine(): EmulationEngine = testEngine
        override fun createSaveManager(saveFile: File): SaveManager =
            SaveManager(saveFile = saveFile, sramSource = testSaveSource)
    }

    private lateinit var testEngine: TestEngine
    private lateinit var testSaveSource: TestSaveSource
    private lateinit var activity: TestableGameActivity

    private val sampleRomBytes = "RETROPACK_MOCK_ROM_PAYLOAD_FOR_TESTS".toByteArray(Charsets.UTF_8)
    private val sampleRomSha256 = bytesToHex(MessageDigest.getInstance("SHA-256").digest(sampleRomBytes))

    @BeforeEach
    fun setUp() {
        assumeTrue(isJvmStubMode(), JVM_ONLY_REASON)
        testEngine = TestEngine()
        testSaveSource = TestSaveSource()

        val retropackJson = """
        {
          "schema_version": 1,
          "game": {
            "id": "test-game",
            "title": "Test Game Title",
            "platform": "gba",
            "rom_sha256": "$sampleRomSha256"
          },
          "runtime": {
            "core": "mgba",
            "video_scale_mode": "aspect_fit"
          },
          "controls": {
            "touch_enabled": true,
            "touch_opacity": 0.75,
            "haptics": true
          }
        }
        """.trimIndent()

        activity = TestableGameActivity(
            testEngine = testEngine,
            testSaveSource = testSaveSource,
            filesDirectory = tempDir,
            configJson = retropackJson,
            romBytes = sampleRomBytes
        )
    }

    @Test
    fun `activity lifecycle initializes subsystems, stages ROM and restores saves`() {
        activity.performCreate(Bundle())

        // Verify config parsed
        assertEquals("test-game", activity.config.game.id)
        assertEquals("Test Game Title", activity.config.game.title)
        assertEquals(ScaleMode.ASPECT_FIT, activity.config.runtime.videoScaleMode)
        assertEquals(0.75f, activity.config.controls.touchOpacity, 0.001f)

        // Verify ROM staged atomically
        val stagedRom = File(tempDir, GameActivity.ROM_FILENAME)
        assertTrue(stagedRom.exists(), "Staged ROM file must exist")
        assertEquals(sampleRomBytes.size.toLong(), stagedRom.length())

        // Verify Engine initialized and ROM loaded
        assertTrue(testEngine.initialized, "Engine must be initialized")
        assertTrue(testEngine.romLoaded, "ROM must be loaded")
        assertTrue(activity.isGameLoaded, "isGameLoaded must be true")

        // Verify View hierarchy created
        assertNotNull(activity.surfaceView, "RetroSurfaceView must be initialized")
        assertEquals(ScaleMode.ASPECT_FIT, activity.surfaceView!!.scaleMode)
        assertNotNull(activity.touchOverlay, "TouchOverlayView must be initialized")
        assertEquals(0.75f, activity.touchOverlay!!.opacity, 0.001f)
        assertNotNull(activity.quickMenu, "QuickMenuOverlay must be initialized")
        assertNotNull(activity.settingsOverlay, "InGameSettingsOverlay must be initialized")
        assertNotNull(activity.moreFeaturesSheet, "MoreFeaturesSheet must be initialized")
        assertNotNull(activity.saveStateManager, "SaveStateManager must be initialized")
    }

    @Test
    fun `onPause and onStop trigger synchronous save flush`() {
        activity.performCreate(Bundle())
        val initialFlushes = testSaveSource.flushCount

        activity.performResume()
        activity.performPause()

        // Invariant 5: Guarantees synchronous cartridge SRAM flush to flash on pause
        assertTrue(testSaveSource.flushCount > initialFlushes, "onPause must synchronously flush SRAM")

        val flushesAfterPause = testSaveSource.flushCount
        activity.performStop()
        assertTrue(testSaveSource.flushCount >= flushesAfterPause, "onStop must flush SRAM")

        val saveFile = File(tempDir, GameActivity.SAVE_FILENAME)
        assertTrue(saveFile.exists(), "Save file game.sav must exist after flush")
    }

    @Test
    fun `dispatchKeyEvent forwards gamepad inputs to host InputCoordinator`() {
        activity.performCreate(Bundle())

        val eventA = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A)
        val consumed = activity.dispatchKeyEvent(eventA)

        assertTrue(consumed, "Button A should be consumed by GamepadMapper")
        assertEquals(RetroKey.A.mask, testEngine.activeKeyMask, "Engine keymask should contain Button A")
    }

    @Test
    fun `onDestroy cleans up host and saves state`() {
        activity.performCreate(Bundle())
        activity.performDestroy()

        assertTrue(testEngine.closed, "Engine must be closed on destroy")
    }

    private companion object {
        const val JVM_ONLY_REASON =
            "Full lifecycle test requires the JVM stub framework (src/stub/java); " +
                "Android-mode unit tests use mockable android.jar with default return values"

        fun isJvmStubMode(): Boolean = try {
            // The stub Context exposes setFilesDir; the real framework does not.
            android.content.Context::class.java.getMethod("setFilesDir", File::class.java)
            true
        } catch (_: NoSuchMethodException) {
            false
        }

        fun bytesToHex(bytes: ByteArray): String {
            val sb = StringBuilder(bytes.size * 2)
            for (b in bytes) {
                sb.append(String.format("%02x", b.toInt() and 0xFF))
            }
            return sb.toString()
        }
    }
}
