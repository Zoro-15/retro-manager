package com.retropack.manager.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.retropack.domain.model.AudioSettings
import com.retropack.domain.model.BuildRequest
import com.retropack.domain.model.BuildResult
import com.retropack.domain.model.BuildStageRecord
import com.retropack.domain.model.ControlsPayload
import com.retropack.domain.model.GameIdentity
import com.retropack.domain.model.GamepadSettings
import com.retropack.domain.model.RomIdentity
import com.retropack.domain.model.RuntimeConfigPayload
import com.retropack.domain.model.SigningPayload
import com.retropack.domain.model.StoragePayload
import com.retropack.domain.model.TouchControlsSettings
import com.retropack.domain.model.VideoSettings
import com.retropack.domain.rom.RomParser
import com.retropack.domain.runtime.RuntimeProvisionResult
import com.retropack.domain.runtime.RuntimeProvisioner
import com.retropack.domain.runtime.RuntimeRegistry
import com.retropack.manager.runtime.AndroidAssetSource
import com.retropack.manager.util.IconSynthesizer
import com.retropack.manager.util.TerminalLogBuffer
import com.retropack.manager.util.UriUtils
import com.retropack.packaging.BuildEngine
import com.retropack.packaging.BuildTerminalReporter
import com.retropack.security.AesGcmMasterKeyProvider
import com.retropack.security.HybridKeystore
import com.retropack.security.KeyType
import com.retropack.security.SigningIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var activeSigningIdentity: SigningIdentity? = null

    init {
        initializeDefaultSigningIdentity()
    }

    private fun initializeDefaultSigningIdentity() {
        try {
            val identity = HybridKeystore.generateIdentity(
                alias = "retropack_default",
                keyType = KeyType.RSA_2048
            )
            activeSigningIdentity = identity
            val fingerprint = computeFingerprint(identity)
            _uiState.update { current ->
                current.copy(
                    signingState = current.signingState.copy(
                        keyAlias = identity.alias,
                        keyType = "RSA-2048",
                        certFingerprint = fingerprint
                    )
                )
            }
        } catch (e: Exception) {
            // Log or retain placeholder
        }
    }

    fun onSelectRom(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(romState = it.romState.copy(isLoading = true, errorMessage = null)) }

            val (fileName, fileSize) = UriUtils.getFileNameAndSize(context, uri)
            val romBytes = withContext(Dispatchers.IO) {
                UriUtils.readBytesFromUri(context, uri)
            }

            if (romBytes == null || romBytes.isEmpty()) {
                _uiState.update {
                    it.copy(
                        romState = it.romState.copy(
                            isLoading = false,
                            errorMessage = "Failed to read ROM content from selected file."
                        )
                    )
                }
                return@launch
            }

            val parseResult = withContext(Dispatchers.Default) {
                runCatching { RomParser.parse(romBytes) }
            }

            parseResult.onSuccess { identity ->
                val derivedPkg = identity.derivePackageName()
                _uiState.update { current ->
                    current.copy(
                        romState = current.romState.copy(
                            selectedUri = uri,
                            fileName = fileName,
                            fileSize = fileSize.takeIf { it > 0 } ?: romBytes.size.toLong(),
                            romBytes = romBytes,
                            romIdentity = identity,
                            isLoading = false,
                            errorMessage = null
                        ),
                        identityState = current.identityState.copy(
                            gameTitle = identity.gameTitle,
                            derivedPackageName = derivedPkg
                        )
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        romState = it.romState.copy(
                            isLoading = false,
                            errorMessage = "ROM Header Analysis Failed: ${err.message}"
                        )
                    )
                }
            }
        }
    }

    fun onSelectPatch(context: Context, uri: Uri) {
        viewModelScope.launch {
            val (name, _) = UriUtils.getFileNameAndSize(context, uri)
            val patchBytes = withContext(Dispatchers.IO) {
                UriUtils.readBytesFromUri(context, uri)
            }
            _uiState.update {
                it.copy(
                    romState = it.romState.copy(
                        patchUri = uri,
                        patchFileName = name,
                        patchBytes = patchBytes
                    )
                )
            }
        }
    }

    fun onClearPatch() {
        _uiState.update {
            it.copy(
                romState = it.romState.copy(
                    patchUri = null,
                    patchFileName = null,
                    patchBytes = null
                )
            )
        }
    }

    fun onGameTitleChanged(newTitle: String) {
        _uiState.update { current ->
            val updatedIdentity = current.identityState.copy(gameTitle = newTitle)
            val newPkg = if (updatedIdentity.autoDerivePackage && current.romState.romIdentity != null) {
                current.romState.romIdentity!!.derivePackageName(
                    customSlug = updatedIdentity.customSlug.ifBlank { newTitle }
                )
            } else {
                updatedIdentity.derivedPackageName
            }
            current.copy(
                identityState = updatedIdentity.copy(derivedPackageName = newPkg)
            )
        }
    }

    fun onCustomSlugChanged(newSlug: String) {
        _uiState.update { current ->
            val updatedIdentity = current.identityState.copy(customSlug = newSlug)
            val newPkg = if (current.romState.romIdentity != null) {
                current.romState.romIdentity!!.derivePackageName(
                    customSlug = newSlug.ifBlank { updatedIdentity.gameTitle }
                )
            } else {
                updatedIdentity.derivedPackageName
            }
            current.copy(
                identityState = updatedIdentity.copy(derivedPackageName = newPkg)
            )
        }
    }

    fun onVersionCodeChanged(code: Int) {
        _uiState.update { it.copy(identityState = it.identityState.copy(versionCode = code)) }
    }

    fun onVersionNameChanged(name: String) {
        _uiState.update { it.copy(identityState = it.identityState.copy(versionName = name)) }
    }

    fun onSelectIcon(context: Context, uri: Uri) {
        viewModelScope.launch {
            val imageBytes = withContext(Dispatchers.IO) {
                UriUtils.readBytesFromUri(context, uri)
            } ?: return@launch

            val layers = withContext(Dispatchers.Default) {
                IconSynthesizer.synthesizeLayers(imageBytes)
            }

            if (layers != null) {
                _uiState.update {
                    it.copy(
                        identityState = it.identityState.copy(
                            iconForegroundUri = uri,
                            iconForegroundBytes = layers.first,
                            iconBackgroundBytes = layers.second
                        )
                    )
                }
            }
        }
    }

    fun onResetDefaultIcon() {
        _uiState.update {
            it.copy(
                identityState = it.identityState.copy(
                    iconForegroundUri = null,
                    iconForegroundBytes = null,
                    iconBackgroundBytes = null
                )
            )
        }
    }

    fun onScaleModeChanged(mode: String) {
        _uiState.update { it.copy(runtimeState = it.runtimeState.copy(scaleMode = mode)) }
    }

    fun onTouchEnabledChanged(enabled: Boolean) {
        _uiState.update { it.copy(runtimeState = it.runtimeState.copy(touchEnabled = enabled)) }
    }

    fun onTouchOpacityChanged(opacity: Float) {
        _uiState.update { it.copy(runtimeState = it.runtimeState.copy(touchOpacity = opacity)) }
    }

    fun onTouchHapticsChanged(haptics: Boolean) {
        _uiState.update { it.copy(runtimeState = it.runtimeState.copy(touchHaptics = haptics)) }
    }

    fun onGamepadEnabledChanged(enabled: Boolean) {
        _uiState.update { it.copy(runtimeState = it.runtimeState.copy(gamepadEnabled = enabled)) }
    }

    fun onGamepadAutoHideChanged(autoHide: Boolean) {
        _uiState.update { it.copy(runtimeState = it.runtimeState.copy(gamepadAutoHideTouch = autoHide)) }
    }

    fun onOpenKeystoreDialog() {
        _uiState.update { it.copy(isKeystoreDialogOpen = true) }
    }

    fun onDismissKeystoreDialog() {
        _uiState.update { it.copy(isKeystoreDialogOpen = false) }
    }

    fun onGenerateNewSigningKey(keyTypeStr: String) {
        try {
            val keyType = if (keyTypeStr == "EC_P256") KeyType.EC_P256 else KeyType.RSA_2048
            val identity = HybridKeystore.generateIdentity(
                alias = "retropack_${System.currentTimeMillis()}",
                keyType = keyType
            )
            activeSigningIdentity = identity
            val fingerprint = computeFingerprint(identity)
            _uiState.update {
                it.copy(
                    signingState = it.signingState.copy(
                        keyAlias = identity.alias,
                        keyType = if (keyType == KeyType.EC_P256) "EC P-256" else "RSA-2048",
                        certFingerprint = fingerprint
                    ),
                    toastMessage = "Fresh ${if (keyType == KeyType.EC_P256) "EC P-256" else "RSA-2048"} signing identity generated"
                )
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(toastMessage = "Key generation failed: ${e.message}") }
        }
    }

    fun onToggleTerminalSheet(visible: Boolean) {
        _uiState.update { it.copy(buildState = it.buildState.copy(showTerminalSheet = visible)) }
    }

    fun onClearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    /**
     * Executes the 15-step transformation pipeline via BuildEngine.
     */
    fun startPackaging(context: Context) {
        val state = _uiState.value
        val romIdentity = state.romState.romIdentity ?: return
        val romBytes = state.romState.romBytes ?: return
        val signingIdentity = activeSigningIdentity ?: return

        viewModelScope.launch {
            val logBuffer = TerminalLogBuffer()
            val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

            fun appendLog(line: String) {
                val time = dateFormat.format(Date())
                logBuffer.append("[$time] $line")
                _uiState.update { current ->
                    current.copy(
                        buildState = current.buildState.copy(
                            rawTerminalLogs = logBuffer.snapshot()
                        )
                    )
                }
            }

            // Prepare Initial UI State for Build
            logBuffer.clear()
            _uiState.update { current ->
                current.copy(
                    buildState = current.buildState.copy(
                        isBuilding = true,
                        currentStageIndex = 0,
                        totalStages = 15,
                        rawTerminalLogs = "",
                        buildResult = null,
                        errorMessage = null,
                        isComplete = false,
                        isSuccess = false,
                        showTerminalSheet = true
                    )
                )
            }

            appendLog("$ retropack transform --rom \"${state.romState.fileName}\" --platform \"${romIdentity.platform}\"")
            appendLog("--> Initializing 15-Step Verified Transformation Engine (v${BuildEngine.MANAGER_VERSION})")
            appendLog("--> Ingesting ROM: ${state.identityState.gameTitle} (${romIdentity.checksums.sha256.take(16)}...)")

            // Construct Declarative BuildRequest
            val buildRequest = BuildRequest(
                version = 1,
                identity = GameIdentity(
                    gameId = romIdentity.checksums.sha256.take(16),
                    gameTitle = state.identityState.gameTitle,
                    packageName = state.identityState.derivedPackageName,
                    versionCode = state.identityState.versionCode,
                    versionName = state.identityState.versionName
                ),
                content = romIdentity.toContentPayload(
                    sourceRomName = state.romState.fileName ?: "game.rom",
                    appliedPatch = state.romState.patchFileName
                ),
                runtime = RuntimeConfigPayload(
                    templateId = state.runtimeState.templateId,
                    video = VideoSettings(scaleMode = state.runtimeState.scaleMode)
                ),
                controls = ControlsPayload(
                    touch = TouchControlsSettings(
                        enabled = state.runtimeState.touchEnabled,
                        opacity = state.runtimeState.touchOpacity,
                        haptics = state.runtimeState.touchHaptics
                    ),
                    gamepad = GamepadSettings(
                        enabled = state.runtimeState.gamepadEnabled,
                        autoHideTouch = state.runtimeState.gamepadAutoHideTouch
                    )
                ),
                storage = StoragePayload(
                    saveType = if (romIdentity.hasBattery) "battery_sram" else "flash_auto"
                ),
                signing = SigningPayload(profileId = state.signingState.keyAlias)
            )

            // Resolve target output directory
            val outputDir = state.signingState.outputDir
                ?: File(context.getExternalFilesDir(null) ?: context.filesDir, "RetroPack").also { it.mkdirs() }

            val result: Result<BuildResult> = withContext(Dispatchers.IO) {
                runCatching {
                    ensureRuntimesLoaded(context) { line -> appendLog(line) }
                    BuildEngine.build(
                        request = buildRequest,
                        romBytes = romBytes,
                        signingIdentity = signingIdentity,
                        outputDir = outputDir,
                        iconForegroundBytes = state.identityState.iconForegroundBytes,
                        iconBackgroundBytes = state.identityState.iconBackgroundBytes,
                        stageListener = { stageRecord ->
                            _uiState.update { current ->
                                current.copy(
                                    buildState = current.buildState.copy(
                                        currentStageIndex = stageRecord.stageNumber
                                    )
                                )
                            }
                            if (stageRecord.passed) {
                                appendLog("[✓] Stage ${stageRecord.stageNumber}/15: ${stageRecord.stageName} (${stageRecord.durationMs} ms)")
                            } else {
                                appendLog("[✗] Stage ${stageRecord.stageNumber}/15: ${stageRecord.stageName} (FAILED: ${stageRecord.description})")
                            }
                        }
                    )
                }
            }

            result.onSuccess { buildResult ->
                // Structural anti-false-positive guard: success requires BOTH the
                // engine's success flag AND a materialized artifact file on disk.
                // Anything else is rendered through the failure path.
                val genuineSuccess = buildResult.success && buildResult.artifactFile?.isFile == true
                if (genuineSuccess) {
                    BuildTerminalReporter.successSummary(buildResult).forEach { appendLog(it) }

                    _uiState.update { current ->
                        current.copy(
                            buildState = current.buildState.copy(
                                isBuilding = false,
                                isComplete = true,
                                isSuccess = true,
                                errorMessage = null,
                                buildResult = buildResult
                            )
                        )
                    }
                } else {
                    val errorMsg = buildResult.errorMessage
                        ?: "Pipeline execution failed at an intermediate stage"
                    BuildTerminalReporter.failureSummary(errorMsg).forEach { appendLog(it) }

                    _uiState.update { current ->
                        current.copy(
                            buildState = current.buildState.copy(
                                isBuilding = false,
                                isComplete = true,
                                isSuccess = false,
                                errorMessage = errorMsg,
                                buildResult = buildResult
                            )
                        )
                    }
                }
            }.onFailure { error ->
                BuildTerminalReporter.abortSummary(error.message).forEach { appendLog(it) }

                _uiState.update { current ->
                    current.copy(
                        buildState = current.buildState.copy(
                            isBuilding = false,
                            isComplete = true,
                            isSuccess = false,
                            errorMessage = error.message,
                            buildResult = BuildResult(
                                success = false,
                                artifactFile = null,
                                packageName = state.identityState.derivedPackageName,
                                versionCode = state.identityState.versionCode,
                                certificateSha256Fingerprint = "",
                                stageProvenance = emptyList(),
                                durationMs = 0,
                                errorMessage = error.message
                            )
                        )
                    )
                }
            }
        }
    }

    /**
     * Provisions the pinned runtime bundle (assets -> filesDir) and registers it
     * in RuntimeRegistry. Failures are REPORTED through [appendLog] — never
     * silently swallowed — so the terminal sheet shows the true root cause
     * (missing bundle asset, stale trust anchor, corrupt descriptor) BEFORE
     * the pipeline records its Stage 2 failure.
     *
     * Returns true when a usable template is registered.
     */
    private fun ensureRuntimesLoaded(context: Context, appendLog: (String) -> Unit): Boolean {
        val assets = try {
            context.assets
        } catch (_: Throwable) {
            null
        }
        if (assets == null) {
            appendLog("[i] Runtime provisioning skipped: asset manager unavailable (headless/unit-test environment)")
            return false
        }

        val targetRuntimesDir = File(context.filesDir, RuntimeProvisioner.BUNDLE_ROOT_DIRNAME)
        val outcome = RuntimeProvisioner.provision(
            targetRoot = targetRuntimesDir,
            source = AndroidAssetSource(assets),
            log = appendLog
        )
        return when (outcome) {
            is RuntimeProvisionResult.Provisioned -> {
                appendLog(
                    "[✓] Runtime template ready: ${outcome.template.templateApk.name} " +
                        "(${outcome.template.templateApk.length()} bytes)"
                )
                true
            }
            is RuntimeProvisionResult.MissingTemplate -> {
                appendLog("[✗] Runtime template MISSING: ${outcome.guidance}")
                false
            }
            is RuntimeProvisionResult.IntegrityMismatch -> {
                appendLog("[✗] Runtime template INTEGRITY FAILURE: ${outcome.details}")
                false
            }
            is RuntimeProvisionResult.ExtractionFailure -> {
                appendLog("[✗] Runtime bundle provisioning failed: ${outcome.cause}")
                false
            }
        }
    }

    private fun computeFingerprint(identity: SigningIdentity): String {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(identity.certificate.encoded)
            digest.joinToString(":") { "%02X".format(it) }
        } catch (e: Exception) {
            "UNKNOWN"
        }
    }
}
