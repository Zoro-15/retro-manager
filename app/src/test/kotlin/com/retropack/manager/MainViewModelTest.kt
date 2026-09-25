package com.retropack.manager

import com.retropack.domain.model.ChecksumRecords
import com.retropack.domain.model.RomIdentity
import com.retropack.domain.rom.GbRomHeader
import com.retropack.manager.viewmodel.MainUiState
import com.retropack.manager.viewmodel.MainViewModel
import com.retropack.manager.viewmodel.RomUiState
import com.retropack.manager.viewmodel.StageStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialUiState() {
        val viewModel = MainViewModel()
        val state = viewModel.uiState.value

        assertNotNull(state.signingState.certFingerprint)
        assertEquals("RSA-2048", state.signingState.keyType)
        assertFalse(state.canStartPackaging)
        assertFalse(state.buildState.isBuilding)
        assertEquals(15, state.buildState.totalStages)
    }

    @Test
    fun testGameTitleAndPackageDerivation() {
        val viewModel = MainViewModel()

        val dummyChecksums = ChecksumRecords(
            crc32 = "12345678",
            md5 = "abcdef0123456789abcdef0123456789",
            sha1 = "1234567890abcdef1234567890abcdef12345678",
            sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        )

        val dummyRomIdentity = RomIdentity(
            platform = "gba",
            gameTitle = "POKEMON EMER",
            fileSize = 16777216L,
            checksums = dummyChecksums,
            headerChecksumValid = true,
            logoOrFixedValid = true
        )

        // Simulate ROM loaded
        val baseState = viewModel.uiState.value
        val stateWithRom = baseState.copy(
            romState = baseState.romState.copy(
                romIdentity = dummyRomIdentity,
                romBytes = ByteArray(1024)
            ),
            identityState = baseState.identityState.copy(
                gameTitle = "POKEMON EMER",
                derivedPackageName = dummyRomIdentity.derivePackageName()
            )
        )

        assertTrue(stateWithRom.canStartPackaging)
        assertTrue(stateWithRom.identityState.derivedPackageName.startsWith("com.retropack.game.pokemonemer_"))
    }

    @Test
    fun testCustomSlugAndVersionCodeUpdates() {
        val viewModel = MainViewModel()

        viewModel.onGameTitleChanged("Super Mario Advance")
        assertEquals("Super Mario Advance", viewModel.uiState.value.identityState.gameTitle)

        viewModel.onVersionCodeChanged(42)
        assertEquals(42, viewModel.uiState.value.identityState.versionCode)

        viewModel.onVersionNameChanged("2.0.0")
        assertEquals("2.0.0", viewModel.uiState.value.identityState.versionName)
    }

    @Test
    fun testRuntimeSettingsUpdates() {
        val viewModel = MainViewModel()

        viewModel.onScaleModeChanged("aspect_fit")
        assertEquals("aspect_fit", viewModel.uiState.value.runtimeState.scaleMode)

        viewModel.onTouchEnabledChanged(false)
        assertFalse(viewModel.uiState.value.runtimeState.touchEnabled)

        viewModel.onTouchOpacityChanged(0.85f)
        assertEquals(0.85f, viewModel.uiState.value.runtimeState.touchOpacity)

        viewModel.onTouchHapticsChanged(false)
        assertFalse(viewModel.uiState.value.runtimeState.touchHaptics)

        viewModel.onGamepadAutoHideChanged(false)
        assertFalse(viewModel.uiState.value.runtimeState.gamepadAutoHideTouch)
    }

    @Test
    fun testSigningKeyGeneration() {
        val viewModel = MainViewModel()

        viewModel.onGenerateNewSigningKey("EC_P256")
        assertEquals("EC P-256", viewModel.uiState.value.signingState.keyType)
        assertNotNull(viewModel.uiState.value.signingState.certFingerprint)

        viewModel.onGenerateNewSigningKey("RSA_2048")
        assertEquals("RSA-2048", viewModel.uiState.value.signingState.keyType)
        assertNotNull(viewModel.uiState.value.signingState.certFingerprint)
    }

    @Test
    fun testLogBufferLimit() {
        val viewModel = MainViewModel()
        for (i in 1..2500) {
            viewModel.appendLog("Log entry $i")
        }
        // Terminal log limit is 2000
        val logs = viewModel.uiState.value.terminalLogs
        assertEquals(2000, logs.size)
        assertTrue(logs.last().contains("Log entry 2500"))
    }
}
