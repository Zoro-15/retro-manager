package com.retropack.manager.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retropack.manager.service.GameLogCaptureService
import com.retropack.manager.service.GameLogEntry
import com.retropack.manager.service.GameLogResult
import com.retropack.manager.service.InstalledGame
import com.retropack.manager.ui.theme.RetroDarkBackground
import com.retropack.manager.ui.theme.RetroDarkOutline
import com.retropack.manager.ui.theme.RetroDarkSurfaceElevated
import com.retropack.manager.ui.theme.RetroError
import com.retropack.manager.ui.theme.RetroPrimary
import com.retropack.manager.ui.theme.RetroPrimaryLight
import com.retropack.manager.ui.theme.RetroSuccess
import com.retropack.manager.ui.theme.RetroWarning
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameLogViewerBottomSheet(
    game: InstalledGame,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var logResult by remember { mutableStateOf<GameLogResult?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isRecording by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("ALL") }

    fun refreshLogs() {
        scope.launch {
            isLoading = true
            logResult = GameLogCaptureService.captureLogs(context, game)
            isLoading = false
        }
    }

    LaunchedEffect(game) {
        refreshLogs()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = RetroDarkBackground,
        dragHandle = null,
        modifier = modifier.fillMaxHeight(0.92f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Header Bar
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
                            .size(40.dp)
                            .background(RetroDarkSurfaceElevated, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = RetroPrimaryLight,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Diagnostic Logs",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Text(
                            text = "${game.gameTitle} (${game.packageName})",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .background(RetroDarkSurfaceElevated, CircleShape)
                        .size(36.dp)
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Status Summary Bar
            val currentLog = logResult
            if (currentLog != null) {
                val statusColor = if (currentLog.hasCrash) RetroError else if (currentLog.hasErrors) RetroWarning else RetroSuccess
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = statusColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (currentLog.hasCrash) Icons.Default.BugReport else Icons.Default.Terminal,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = currentLog.summary,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White
                        )
                    }
                }

                // Crash Diagnostic Deep Dive Card
                if (currentLog.crashDiagnostic != null) {
                    val diag = currentLog.crashDiagnostic
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = RetroError.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, RetroError.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "CRASH ANALYSIS: ${diag.signal}",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = RetroError
                            )
                            Text(
                                text = diag.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            if (!diag.faultAddress.isNullOrBlank()) {
                                Text(
                                    text = "Fault Address: ${diag.faultAddress}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                    color = Color(0xFFFFB4A9)
                                )
                            }
                            if (!diag.faultingModule.isNullOrBlank()) {
                                Text(
                                    text = "Module: ${diag.faultingModule}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                    color = Color(0xFFFFD8D3)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Toolbar (Launch & Record, Refresh, Copy, Share)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            isRecording = true
                            logResult = GameLogCaptureService.launchAndRecord(context, game)
                            isRecording = false
                        }
                    },
                    enabled = !isRecording,
                    colors = ButtonDefaults.buttonColors(containerColor = RetroPrimary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1.3f)
                ) {
                    if (isRecording) {
                        CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Recording...", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    } else {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Launch & Record", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedButton(
                    onClick = { refreshLogs() },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier.weight(0.9f)
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Refresh", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = {
                        val text = logResult?.rawText ?: ""
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("RetroPack Logs", text))
                        Toast.makeText(context, "Logs copied to clipboard!", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RetroPrimaryLight),
                    modifier = Modifier.weight(0.8f)
                ) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = {
                        val text = logResult?.rawText ?: ""
                        val file = GameLogCaptureService.exportLogToFile(context, game, text)
                        val shareIntent = GameLogCaptureService.createShareLogIntent(context, game, file)
                        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Diagnostic Log"))
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF93C5FD)),
                    modifier = Modifier.weight(0.8f)
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Chips
            val filterOptions = listOf("ALL", "CRASHES ONLY", "RETRO ENGINE", "ERRORS")
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(filterOptions) { filter ->
                    val isSelected = (selectedFilter == filter)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) RetroPrimaryLight.copy(alpha = 0.2f) else RetroDarkSurfaceElevated,
                        border = BorderStroke(1.dp, if (isSelected) RetroPrimaryLight else RetroDarkOutline.copy(alpha = 0.3f)),
                        modifier = Modifier.clickable { selectedFilter = filter }
                    ) {
                        Text(
                            text = filter,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (isSelected) RetroPrimaryLight else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Monospaced Log Output Terminal Window
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF0D1117),
                border = BorderStroke(1.dp, RetroDarkOutline.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = RetroPrimary)
                    }
                } else {
                    val entries = logResult?.entries ?: emptyList()
                    val filteredEntries = entries.filter { entry ->
                        when (selectedFilter) {
                            "CRASHES ONLY" -> entry.isCrash
                            "RETRO ENGINE" -> entry.isEngine
                            "ERRORS" -> entry.isError || entry.isCrash
                            else -> true
                        }
                    }

                    if (filteredEntries.isEmpty() && logResult?.rawText.isNullOrBlank()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = "No log output recorded for this filter.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    } else if (filteredEntries.isEmpty()) {
                        // Fallback to raw text
                        val raw = logResult?.rawText ?: ""
                        LazyColumn(modifier = Modifier.padding(12.dp)) {
                            item {
                                Text(
                                    text = raw,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = Color(0xFFE2E8F0),
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    } else {
                        val listState = rememberLazyListState()
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(filteredEntries) { entry ->
                                val color = when {
                                    entry.isCrash -> RetroError
                                    entry.isError -> RetroWarning
                                    entry.isEngine -> Color(0xFF38BDF8)
                                    else -> Color(0xFFCBD5E1)
                                }
                                Text(
                                    text = entry.message,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = color,
                                    lineHeight = 15.sp,
                                    modifier = Modifier.horizontalScroll(rememberScrollState())
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
