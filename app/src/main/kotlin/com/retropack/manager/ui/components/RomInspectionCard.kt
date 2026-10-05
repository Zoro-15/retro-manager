package com.retropack.manager.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retropack.domain.model.RomIdentity
import com.retropack.manager.ui.theme.RetroDarkOutline
import com.retropack.manager.ui.theme.RetroDarkSurfaceElevated
import com.retropack.manager.ui.theme.RetroDarkSurfaceVariant
import com.retropack.manager.ui.theme.RetroPrimary
import com.retropack.manager.util.UriUtils

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RomInspectionCard(
    identity: RomIdentity,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val platformLabel = when (identity.platform.lowercase()) {
        "gba" -> "GAME BOY ADVANCE"
        "gbc" -> "GAME BOY COLOR"
        "gb" -> "GAME BOY"
        "snes", "sfc", "smc" -> "SUPER NINTENDO"
        "genesis", "md", "smd", "gen" -> "SEGA GENESIS"
        "sms" -> "MASTER SYSTEM"
        "gg" -> "GAME GEAR"
        "nes", "fds", "unf" -> "NES / FAMICOM"
        "pce", "tg16", "sgx" -> "PC ENGINE / TG-16"
        "arcade", "neogeo", "cps1", "cps2", "cps3", "fbneo" -> "ARCADE / FBNEO"
        "psx", "ps1", "ps" -> "PLAYSTATION 1"
        "n64", "z64", "v64" -> "NINTENDO 64"
        "psp" -> "PLAYSTATION PORTABLE"
        "nds", "dsi" -> "NINTENDO DS"
        else -> identity.platform.uppercase()
    }

    RetroCard(
        modifier = modifier,
        containerColor = RetroDarkSurfaceElevated.copy(alpha = 0.7f),
        borderColor = RetroDarkOutline.copy(alpha = 0.5f)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header: Enlarge Platform Badge + Verification Status
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
                            .background(Color.White, RoundedCornerShape(10.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = platformLabel,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.2.sp
                            ),
                            color = Color.Black
                        )
                    }

                    if (identity.hasBattery) {
                        Box(
                            modifier = Modifier
                                .background(RetroDarkSurfaceVariant, RoundedCornerShape(10.dp))
                                .border(BorderStroke(1.dp, RetroDarkOutline.copy(alpha = 0.6f)), RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Save,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "BATTERY SRAM",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                // Header Integrity Indicator
                val isFullyValid = identity.headerChecksumValid && identity.logoOrFixedValid
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (isFullyValid) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (isFullyValid) "VERIFIED" else "HEADER ISSUE",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Game Title & Details
            Text(
                text = identity.gameTitle,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.3).sp
                ),
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Metadata Row: Game Code / Maker / Version / Size
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val gameCode = identity.gameCode
                if (!gameCode.isNullOrBlank()) {
                    MetadataChip(label = "CODE", value = gameCode)
                }
                val makerCode = identity.makerCode
                if (!makerCode.isNullOrBlank()) {
                    MetadataChip(label = "MAKER", value = makerCode)
                }
                val mbcType = identity.mbcType
                if (mbcType != null) {
                    MetadataChip(label = "CHIP", value = mbcType)
                }
                MetadataChip(label = "VER", value = "v1.${identity.softwareVersion}")
                MetadataChip(label = "SIZE", value = UriUtils.formatFileSize(identity.fileSize))
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = RetroDarkOutline.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(14.dp))

            // Expanded Checksums Section
            Text(
                text = "CRYPTOGRAPHIC CHECKSUMS",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChecksumRow(label = "SHA-256", hash = identity.checksums.sha256, onCopy = { copyToClipboard(context, "SHA-256", identity.checksums.sha256) })
                ChecksumRow(label = "SHA-1", hash = identity.checksums.sha1, onCopy = { copyToClipboard(context, "SHA-1", identity.checksums.sha1) })
                ChecksumRow(label = "CRC-32", hash = identity.checksums.crc32, onCopy = { copyToClipboard(context, "CRC-32", identity.checksums.crc32) })
            }
        }
    }
}

@Composable
fun MetadataChip(
    label: String,
    value: String
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = RetroDarkSurfaceVariant,
        border = BorderStroke(1.dp, RetroDarkOutline.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                ),
                color = Color.White
            )
        }
    }
}

@Composable
fun ChecksumRow(
    label: String,
    hash: String,
    onCopy: () -> Unit
) {
    Surface(
        onClick = onCopy,
        shape = RoundedCornerShape(12.dp),
        color = RetroDarkSurfaceVariant.copy(alpha = 0.8f),
        border = BorderStroke(1.dp, RetroDarkOutline.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    ),
                    modifier = Modifier.width(64.dp)
                )
                Text(
                    text = hash,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    color = Color(0xFFE4E4E7),
                    maxLines = 1
                )
            }

            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy $label",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard?.setPrimaryClip(clip)
    Toast.makeText(context, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
}
