package com.retropack.manager.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retropack.domain.model.BuildResult
import com.retropack.manager.ui.components.RetroCard
import com.retropack.manager.ui.components.TerminalView
import com.retropack.manager.ui.theme.RetroDarkBackground
import com.retropack.manager.ui.theme.RetroDarkOutline
import com.retropack.manager.ui.theme.RetroDarkSurface
import com.retropack.manager.ui.theme.RetroError
import com.retropack.manager.ui.theme.RetroPrimary
import com.retropack.manager.ui.theme.RetroSuccess
import com.retropack.manager.util.ApkInstaller
import com.retropack.manager.util.UriUtils
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalBottomSheet(
    visible: Boolean,
    logs: String,
    isBuilding: Boolean,
    currentStageIndex: Int,
    totalStages: Int,
    buildResult: BuildResult?,
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
) {
    if (!visible) return

    val context = LocalContext.current

    val exportLogLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
        onResult = { uri ->
            if (uri != null) {
                UriUtils.writeTextToUri(context, uri, logs)
                    .onSuccess {
                        Toast.makeText(context, "Terminal log saved successfully", Toast.LENGTH_SHORT).show()
                    }
                    .onFailure { err ->
                        Toast.makeText(context, "Failed to save log: ${err.message}", Toast.LENGTH_LONG).show()
                    }
            }
        }
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = RetroDarkSurface,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.65f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(RetroPrimary.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = RetroPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Live Build Terminal",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isBuilding) "Step $currentStageIndex of $totalStages in progress..."
                            else if (buildResult != null && buildResult.success) "Transformation complete (${buildResult.durationMs} ms)"
                            else if (buildResult != null && !buildResult.success) "Build failed: ${buildResult.errorMessage ?: "Unknown error"}"
                            else "Ready for execution",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (buildResult != null && !buildResult.success) RetroError else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Copy to clipboard
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(ClipData.newPlainText("RetroPack Build Log", logs))
                            Toast.makeText(context, "Log copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Log",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Save log file
                    IconButton(
                        onClick = {
                            val timestamp = System.currentTimeMillis()
                            exportLogLauncher.launch("retropack_build_$timestamp.log")
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Save Log",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Close sheet
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Real-Time Monospace Terminal Window
            TerminalView(
                logs = logs,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 220.dp, max = 340.dp)
            )

            // Success or Failure Action Cards
            if (buildResult != null && buildResult.success && buildResult.artifactFile != null) {
                Spacer(modifier = Modifier.height(16.dp))

                RetroCard(
                    containerColor = RetroSuccess.copy(alpha = 0.12f),
                    borderColor = RetroSuccess.copy(alpha = 0.5f)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = RetroSuccess,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "STANDALONE APK READY",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.sp
                                ),
                                color = RetroSuccess
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Package: ${buildResult.packageName} (v${buildResult.versionCode})",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = "Artifact: ${buildResult.artifactFile.name}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Install and Share Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    ApkInstaller.installApk(context, buildResult.artifactFile)
                                        .onFailure { err ->
                                            Toast.makeText(context, "Install trigger error: ${err.message}", Toast.LENGTH_LONG).show()
                                        }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = RetroSuccess,
                                    contentColor = MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "INSTALL APK",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold)
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    ApkInstaller.shareApk(context, buildResult.artifactFile)
                                        .onFailure { err ->
                                            Toast.makeText(context, "Share error: ${err.message}", Toast.LENGTH_LONG).show()
                                        }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, RetroSuccess.copy(alpha = 0.6f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null,
                                    tint = RetroSuccess,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "SHARE APK",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = RetroSuccess
                                )
                            }
                        }
                    }
                }
            } else if (buildResult != null && !buildResult.success) {
                Spacer(modifier = Modifier.height(16.dp))

                RetroCard(
                    containerColor = RetroError.copy(alpha = 0.12f),
                    borderColor = RetroError.copy(alpha = 0.5f)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                tint = RetroError,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "TRANSFORMATION FAILED",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = RetroError
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = buildResult.errorMessage ?: "Pipeline encountered an unrecoverable exception. Check logs above.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
