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

data class RomUiState(
    val selectedUri: Uri? = null,
    val fileName: String? = null,
    val fileSize: Long = 0,
    val romBytes: ByteArray? = null,
    val romIdentity: RomIdentity? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val patchUri: Uri? = null,
    val patchFileName: String? = null,
    val patchBytes: ByteArray? = null
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
    val iconBackgroundBytes: ByteArray? = null
)

data class RuntimeUiState(
    val templateId: String = "mgba-unified",
    val templateName: String = "mGBA Unified Core (v0.10.5)",
    val scaleMode: String = "integer_fit", // "integer_fit" or "aspect_fit"
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
                romState.romBytes != null &&
                identityState.gameTitle.isNotBlank() &&
                !buildState.isBuilding
}
