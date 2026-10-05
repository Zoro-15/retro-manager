package com.retropack.manager.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retropack.manager.service.InstalledGame
import com.retropack.manager.service.InstalledGamesRepository
import com.retropack.manager.service.SaveSyncManager
import com.retropack.manager.ui.components.RetroCard
import com.retropack.manager.ui.theme.RetroDarkBackground
import com.retropack.manager.ui.theme.RetroDarkOutline
import com.retropack.manager.ui.theme.RetroDarkSurfaceElevated
import com.retropack.manager.ui.theme.RetroDarkSurfaceVariant
import com.retropack.manager.ui.theme.RetroPrimary
import com.retropack.manager.ui.theme.RetroPrimaryLight
import com.retropack.manager.ui.theme.RetroSuccess
import com.retropack.manager.util.UriUtils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dedicated "Library & Saves" Navigation Tab for RetroPack Manager.
 *
 * Implements Feature 3 specifications:
 * - Scans all generated RetroPack standalone games installed on the user device.
 * - Quick "Play" button launching game directly via explicit Intent.
 * - Battery save backup & export to Downloads/RetroPack/Saves/<GameTitle>/.
 * - Save Import tool injecting PC Emulator (.sav, .srm, .mcd) saves into games.
 */
@Composable
fun InstalledGamesScreen(
    repository: InstalledGamesRepository = remember { InstalledGamesRepository() },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var games by remember { mutableStateOf<List<InstalledGame>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilterPlatform by remember { mutableStateOf("ALL") }
    var targetImportGame by remember { mutableStateOf<InstalledGame?>(null) }

    fun refreshGames() {
        scope.launch {
            isLoading = true
            games = repository.scanInstalledGames(context)
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshGames()
    }

    val saveImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            val game = targetImportGame
            if (uri != null && game != null) {
                scope.launch {
                    val result = SaveSyncManager.importSave(context, game, uri)
                    result.onSuccess {
                        Toast.makeText(context, "Save imported successfully for ${game.gameTitle}!", Toast.LENGTH_LONG).show()
                        refreshGames()
                    }.onFailure { err ->
                        Toast.makeText(context, "Save import failed: ${err.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    )

    val platforms = listOf("ALL", "GBA", "GBC", "GB", "SNES", "GENESIS", "NES", "PCE")

    val filteredGames = games.filter { game ->
        val matchesSearch = game.gameTitle.contains(searchQuery, ignoreCase = true) ||
                game.packageName.contains(searchQuery, ignoreCase = true)
        val matchesPlatform = (selectedFilterPlatform == "ALL") ||
                game.platform.equals(selectedFilterPlatform, ignoreCase = true)
        matchesSearch && matchesPlatform
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RetroDarkBackground)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Screen Header Bar
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
                            Color.White,
                            RoundedCornerShape(14.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SportsEsports,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Column {
                    Text(
                        text = "Library & Saves",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp
                        ),
                        color = Color.White
                    )
                    Text(
                        text = "${games.size} Standalone Games Installed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(
                onClick = { refreshGames() },
                modifier = Modifier
                    .background(RetroDarkSurfaceElevated, CircleShape)
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh Library",
                    tint = RetroPrimaryLight
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search installed games by title or package...") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = RetroPrimary
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = RetroPrimary,
                unfocusedBorderColor = RetroDarkOutline.copy(alpha = 0.4f),
                focusedContainerColor = RetroDarkSurfaceElevated.copy(alpha = 0.3f),
                unfocusedContainerColor = RetroDarkSurfaceElevated.copy(alpha = 0.3f)
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Console Filter Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(platforms) { p ->
                val isSelected = (selectedFilterPlatform == p)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) RetroPrimary else RetroDarkSurfaceElevated,
                    border = BorderStroke(1.dp, if (isSelected) Color.White else RetroDarkOutline.copy(alpha = 0.3f)),
                    modifier = Modifier.clickable { selectedFilterPlatform = p }
                ) {
                    Text(
                        text = p,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Content Area
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(color = RetroPrimary)
                    Text("Scanning device for RetroPack standalone games...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else if (filteredGames.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Gamepad,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        text = "No Standalone Games Found",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Text(
                        text = "Use the Transformer tab to build and install your standalone retro games with embedded zero-lag runtimes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filteredGames) { game ->
                    InstalledGameCard(
                        game = game,
                        onPlay = {
                            val launchIntent = context.packageManager.getLaunchIntentForPackage(game.packageName)
                            if (launchIntent != null) {
                                context.startActivity(launchIntent)
                            } else {
                                Toast.makeText(context, "Failed to launch ${game.gameTitle}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onExportSave = {
                            scope.launch {
                                val result = SaveSyncManager.exportSave(context, game)
                                result.onSuccess { file ->
                                    Toast.makeText(context, "Save exported to Downloads/RetroPack/Saves!", Toast.LENGTH_SHORT).show()
                                    val shareIntent = SaveSyncManager.createShareIntent(context, game, file)
                                    context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Save File"))
                                }.onFailure { err ->
                                    Toast.makeText(context, "Export error: ${err.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onImportSave = {
                            targetImportGame = game
                            saveImportLauncher.launch(
                                arrayOf(
                                    "*/*",
                                    "application/octet-stream",
                                    "application/x-snes-save",
                                    "application/x-gameboy-save"
                                )
                            )
                        }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(100.dp))
                }
            }
        }
    }
}

@Composable
private fun InstalledGameCard(
    game: InstalledGame,
    onPlay: () -> Unit,
    onExportSave: () -> Unit,
    onImportSave: () -> Unit
) {
    RetroCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Icon + Title + Metadata
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // App Icon with Platform Badge
                    Box(modifier = Modifier.size(56.dp)) {
                        val bitmap = remember(game.iconDrawable) {
                            drawableToBitmap(game.iconDrawable)
                        }
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = game.gameTitle,
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(Color(0xFF1E293B), RoundedCornerShape(14.dp))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(RetroDarkSurfaceElevated, RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SportsEsports,
                                    contentDescription = null,
                                    tint = RetroPrimary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        // Small console badge
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .background(RetroPrimary, RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = game.platform.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 8.sp
                                ),
                                color = Color.White
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = game.gameTitle,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = game.packageName,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(3.dp))

                        val formattedSize = UriUtils.formatFileSize(game.appSizeBytes)
                        val formattedDate = SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(game.installDate))
                        Text(
                            text = "$formattedSize • Installed $formattedDate",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = RetroPrimaryLight
                        )
                    }
                }

                // Quick Play Action Button
                Button(
                    onClick = onPlay,
                    colors = ButtonDefaults.buttonColors(containerColor = RetroPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("PLAY", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Save Battery Status and Action Bar
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = RetroDarkSurfaceElevated.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, RetroDarkOutline.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            tint = if (game.hasSave) RetroSuccess else Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (game.hasSave) "Battery Save: ${UriUtils.formatFileSize(game.saveSizeBytes)}" else "No Save Detected",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = if (game.hasSave) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize = 11.sp
                            ),
                            color = if (game.hasSave) RetroSuccess else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Export Save
                        if (game.hasSave) {
                            OutlinedButton(
                                onClick = onExportSave,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF93C5FD)),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Icon(imageVector = Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Backup", fontSize = 11.sp)
                            }
                        }

                        // Import Save
                        OutlinedButton(
                            onClick = onImportSave,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = RetroPrimaryLight),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(imageVector = Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Import Save", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun drawableToBitmap(drawable: Drawable?): Bitmap? {
    if (drawable == null) return null
    if (drawable is BitmapDrawable) return drawable.bitmap

    val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 128
    val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 128
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}
