package com.retropack.manager.viewmodel

import android.net.Uri
import com.retropack.domain.model.BuildResult
import com.retropack.domain.model.BuildStageRecord
import com.retropack.domain.model.RomIdentity
import java.io.File

enum class StageStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED
}

data class BuildStageDisplay(
    val stageNumber: Int,
    val stageName: String,
    val description: String,
    val durationMs: Long = 0,
    val status: StageStatus = StageStatus.PENDING,
    val errorMessage: String? = null
)

data class DiscUiItem(
    val discIndex: Int,
    val label: String,
    val fileName: String,
    val uri: Uri? = null,
    val file: File? = null,
    val bytes: ByteArray? = null,
    val fileSize: Long = 0L,
    val sha256: String = ""
)

data class RomUiState(
    val selectedUri: Uri? = null,
    val fileName: String? = null,
    val fileSize: Long = 0,
    val romFile: File? = null,
    val romBytes: ByteArray? = null,
    val romIdentity: RomIdentity? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val patchUri: Uri? = null,
    val patchFileName: String? = null,
    val patchFile: File? = null,
    val patchBytes: ByteArray? = null,
    val discItems: List<DiscUiItem> = emptyList()
)

data class IdentityUiState(
    val gameTitle: String = "",
    val customSlug: String = "",
    val autoDerivePackage: Boolean = true,
    val derivedPackageName: String = "",
    val versionCode: Int = 1,
    val versionName: String = "1.0.0",
    val iconForegroundUri: Uri? = null,
    val iconForegroundBytes: ByteArray? = null,
    val iconBackgroundBytes: ByteArray? = null,
    val iconRasterBytes: ByteArray? = null,
    val isScrapingIcon: Boolean = false
)

data class RuntimeUiState(
    val templateId: String = "mgba-unified",
    val templateName: String = "mGBA Unified Core (v0.10.5)",
    val scaleMode: String = "integer_fit", // "integer_fit", "aspect_fit", or "stretch_full"
    val shaderMode: String = "none", // "none", "crt_scanlines", "lcd_dotmatrix", "color_boost", "sharp_bilinear", "dmg_pea_green"
    val bezelMode: String = "none_oled_black", // "none_oled_black", "auto", "gba_indigo", "gba_sp_platinum", "dmg_retro_offwhite", "gbc_atomic_purple", "crt_trinitron_90s"
    val touchEnabled: Boolean = true,
    val touchOpacity: Float = 0.65f,
    val touchHaptics: Boolean = true,
    val gamepadEnabled: Boolean = true,
    val gamepadAutoHideTouch: Boolean = true
)

data class SigningUiState(
    val keyAlias: String = "retropack_default",
    val keyType: String = "RSA-2048",
    val certFingerprint: String? = null,
    val certSubject: String = "CN=RetroPack Game Signer, OU=RetroPack, O=Self-Signed",
    val isManaged: Boolean = true,
    val outputDir: File? = null,
    val outputDirDisplayName: String = "Default App Storage"
)

data class BuildUiState(
    val isBuilding: Boolean = false,
    val currentStageIndex: Int = 0,
    val totalStages: Int = 15,
    val stages: List<BuildStageDisplay> = emptyList(),
    val rawTerminalLogs: String = "",
    val buildResult: BuildResult? = null,
    val errorMessage: String? = null,
    val isComplete: Boolean = false,
    val isSuccess: Boolean = false,
    val showTerminalSheet: Boolean = false
)

data class MainUiState(
    val romState: RomUiState = RomUiState(),
    val identityState: IdentityUiState = IdentityUiState(),
    val runtimeState: RuntimeUiState = RuntimeUiState(),
    val signingState: SigningUiState = SigningUiState(),
    val buildState: BuildUiState = BuildUiState(),
    val isKeystoreDialogOpen: Boolean = false,
    val toastMessage: String? = null
) {
    val canStartPackaging: Boolean
        get() = romState.romIdentity != null &&
                (romState.romFile != null || romState.romBytes != null) &&
                identityState.gameTitle.isNotBlank() &&
                !buildState.isBuilding
}
