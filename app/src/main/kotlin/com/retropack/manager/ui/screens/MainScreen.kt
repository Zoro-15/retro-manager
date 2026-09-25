package com.retropack.manager.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retropack.manager.ui.components.ActionTile
import com.retropack.manager.ui.components.AdvancedOptionsSection
import com.retropack.manager.ui.components.FileSelectorCard
import com.retropack.manager.ui.components.FloatingDock
import com.retropack.manager.ui.components.IconPreviewCard
import com.retropack.manager.ui.components.RetroCard
import com.retropack.manager.ui.components.RomInspectionCard
import com.retropack.manager.ui.components.StepConnector
import com.retropack.manager.ui.components.StepHeader
import com.retropack.manager.ui.theme.RetroDarkBackground
import com.retropack.manager.ui.theme.RetroDarkOutline
import com.retropack.manager.ui.theme.RetroDarkSurfaceElevated
import com.retropack.manager.ui.theme.RetroDarkSurfaceVariant
import com.retropack.manager.ui.theme.RetroError
import com.retropack.manager.ui.theme.RetroPrimary
import com.retropack.manager.ui.theme.RetroPrimaryLight
import com.retropack.manager.ui.theme.RetroSuccess
import com.retropack.manager.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    // File Pickers
    val romPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let { viewModel.onSelectRom(context, it) } }
    )

    val patchPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let { viewModel.onSelectPatch(context, it) } }
    )

    val iconPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri -> uri?.let { viewModel.onSelectIcon(context, it) } }
    )

    // Handle Toast events
    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.onClearToast()
        }
    }

    Scaffold(
        containerColor = RetroDarkBackground,
        bottomBar = {
            FloatingDock(
                isBuilding = uiState.buildState.isBuilding,
                canBuild = uiState.canStartPackaging,
                currentStageIndex = uiState.buildState.currentStageIndex,
                totalStages = uiState.buildState.totalStages,
                currentStageName = uiState.buildState.stages.getOrNull(uiState.buildState.currentStageIndex - 1)?.stageName,
                isComplete = uiState.buildState.isComplete,
                isSuccess = uiState.buildState.isSuccess,
                onStartBuild = { viewModel.startPackaging(context) },
                onOpenLogs = { viewModel.onToggleTerminalSheet(true) },
                modifier = Modifier.navigationBarsPadding()
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(scrollState)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // App Title Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                Brush.linearGradient(listOf(RetroPrimary, Color(0xFF06B6D4))),
                                RoundedCornerShape(14.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SportsEsports,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "RetroPack",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.5).sp
                            ),
                            color = Color.White
                        )
                        Text(
                            text = "Standalone ROM-to-APK Transformer",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Core Badge
                Box(
                    modifier = Modifier
                        .background(RetroPrimary.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "mGBA 0.10.5",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = RetroPrimaryLight
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ==========================================
            // STEP 01: Select & Verify ROM File
            // ==========================================
            StepHeader(
                number = "01",
                title = "Select ROM File",
                subtitle = "GB / GBC / GBA with real-time header inspection",
                isCompleted = uiState.romState.romIdentity != null
            )

            Spacer(modifier = Modifier.height(12.dp))

            FileSelectorCard(
                label = "ROM Content File",
                fileName = uiState.romState.fileName,
                fileSizeFormatted = uiState.romState.romIdentity?.fileSize?.let { com.retropack.manager.util.UriUtils.formatFileSize(it) },
                placeholder = "Tap to choose .gb, .gbc, or .gba ROM",
                badge = uiState.romState.romIdentity?.platform?.uppercase(),
                icon = Icons.Default.Gamepad,
                onSelect = {
                    romPickerLauncher.launch(
                        arrayOf(
                            "application/octet-stream",
                            "application/x-gameboy-rom",
                            "application/x-gba-rom",
                            "application/zip",
                            "*/*"
                        )
                    )
                }
            )

            // ROM Loading Indicator
            AnimatedVisibility(visible = uiState.romState.isLoading) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = RetroDarkSurfaceElevated.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            color = RetroPrimary,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "Analyzing ROM headers & computing checksums...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // ROM Inspection Error
            AnimatedVisibility(visible = uiState.romState.errorMessage != null) {
                uiState.romState.errorMessage?.let { error ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = RetroError.copy(alpha = 0.15f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                tint = RetroError,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Verified ROM Inspection Card
            AnimatedVisibility(visible = uiState.romState.romIdentity != null) {
                uiState.romState.romIdentity?.let { identity ->
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        RomInspectionCard(identity = identity)

                        Spacer(modifier = Modifier.height(10.dp))

                        // Optional Patch Slot
                        FileSelectorCard(
                            label = "Optional IPS/UPS Binary Patch",
                            fileName = uiState.romState.patchFileName,
                            placeholder = "Select .ips or .ups patch (optional)",
                            icon = Icons.Default.Build,
                            onSelect = { patchPickerLauncher.launch(arrayOf("*/*")) },
                            onClear = { viewModel.onClearPatch() }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            StepConnector()
            Spacer(modifier = Modifier.height(8.dp))

            // ==========================================
            // STEP 02: Configure Package Identity & Runtime
            // ==========================================
            StepHeader(
                number = "02",
                title = "Package Identity & Runtime",
                subtitle = "App title, deterministic package name, and icon",
                isCompleted = uiState.identityState.gameTitle.isNotBlank() && uiState.romState.romIdentity != null
            )

            Spacer(modifier = Modifier.height(12.dp))

            RetroCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Game Title Input
                    OutlinedTextField(
                        value = uiState.identityState.gameTitle,
                        onValueChange = { viewModel.onGameTitleChanged(it) },
                        label = { Text("Application Name") },
                        placeholder = { Text("e.g. Pokemon Emerald Version") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RetroPrimary,
                            unfocusedBorderColor = RetroDarkOutline.copy(alpha = 0.5f),
                            focusedContainerColor = RetroDarkSurfaceElevated.copy(alpha = 0.3f),
                            unfocusedContainerColor = RetroDarkSurfaceElevated.copy(alpha = 0.3f)
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Deterministic Package Name Preview
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = RetroPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "DETERMINISTIC PACKAGE NAME",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    ),
                                    color = RetroPrimary
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = uiState.identityState.derivedPackageName.ifBlank { "com.retropack.game.<slug>_<hash10>" },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Version Code & Name Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = uiState.identityState.versionCode.toString(),
                            onValueChange = { it.toIntOrNull()?.let { code -> viewModel.onVersionCodeChanged(code) } },
                            label = { Text("Version Code") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = RetroPrimary,
                                unfocusedBorderColor = RetroDarkOutline.copy(alpha = 0.5f)
                            )
                        )

                        OutlinedTextField(
                            value = uiState.identityState.versionName,
                            onValueChange = { viewModel.onVersionNameChanged(it) },
                            label = { Text("Version Name") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = RetroPrimary,
                                unfocusedBorderColor = RetroDarkOutline.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Boxart / Launcher Icon Picker
            IconPreviewCard(
                foregroundBytes = uiState.identityState.iconForegroundBytes,
                onPickImage = { iconPickerLauncher.launch("image/*") },
                onResetDefault = { viewModel.onResetDefaultIcon() }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Advanced Runtime Settings
            AdvancedOptionsSection(
                scaleMode = uiState.runtimeState.scaleMode,
                onScaleModeChange = { viewModel.onScaleModeChanged(it) },
                touchEnabled = uiState.runtimeState.touchEnabled,
                onTouchEnabledChange = { viewModel.onTouchEnabledChanged(it) },
                touchOpacity = uiState.runtimeState.touchOpacity,
                onTouchOpacityChange = { viewModel.onTouchOpacityChanged(it) },
                touchHaptics = uiState.runtimeState.touchHaptics,
                onTouchHapticsChange = { viewModel.onTouchHapticsChanged(it) },
                gamepadEnabled = uiState.runtimeState.gamepadEnabled,
                onGamepadEnabledChange = { viewModel.onGamepadEnabledChanged(it) },
                gamepadAutoHide = uiState.runtimeState.gamepadAutoHideTouch,
                onGamepadAutoHideChange = { viewModel.onGamepadAutoHideChanged(it) }
            )

            Spacer(modifier = Modifier.height(8.dp))
            StepConnector()
            Spacer(modifier = Modifier.height(8.dp))

            // ==========================================
            // STEP 03: Select Signing Key & Output Path
            // ==========================================
            StepHeader(
                number = "03",
                title = "Signing Identity & Output",
                subtitle = "Cryptographic signing and target directory",
                isCompleted = uiState.signingState.certFingerprint != null
            )

            Spacer(modifier = Modifier.height(12.dp))

            ActionTile(
                title = "Hybrid Keystore (${uiState.signingState.keyType})",
                subtitle = "Alias: ${uiState.signingState.keyAlias} (Tap to manage)",
                icon = Icons.Default.VpnKey,
                badgeText = "MANAGED",
                selected = true,
                onClick = { viewModel.onOpenKeystoreDialog() }
            )

            Spacer(modifier = Modifier.height(10.dp))

            FileSelectorCard(
                label = "Output Storage Destination",
                fileName = uiState.signingState.outputDirDisplayName,
                placeholder = "App External Files / RetroPack",
                icon = Icons.Default.Folder,
                badge = "16 KB ALIGNED",
                onSelect = {
                    Toast.makeText(context, "Output stored in App External Files / RetroPack", Toast.LENGTH_SHORT).show()
                }
            )

            Spacer(modifier = Modifier.height(120.dp)) // Clearance for FloatingDock
        }
    }

    // Modal Bottom Sheet: Live Monospace Terminal Log
    TerminalBottomSheet(
        visible = uiState.buildState.showTerminalSheet,
        logs = uiState.buildState.rawTerminalLogs,
        isBuilding = uiState.buildState.isBuilding,
        currentStageIndex = uiState.buildState.currentStageIndex,
        totalStages = uiState.buildState.totalStages,
        buildResult = uiState.buildState.buildResult,
        onDismiss = { viewModel.onToggleTerminalSheet(false) }
    )

    // Modal Dialog: Keystore Management
    KeystoreDialog(
        isOpen = uiState.isKeystoreDialogOpen,
        keyAlias = uiState.signingState.keyAlias,
        keyType = uiState.signingState.keyType,
        certSubject = uiState.signingState.certSubject,
        certFingerprint = uiState.signingState.certFingerprint,
        onGenerateNewKey = { keyType -> viewModel.onGenerateNewSigningKey(keyType) },
        onExportKeystore = { /* Export callback */ },
        onDismiss = { viewModel.onDismissKeystoreDialog() }
    )
}
