package com.retropack.manager.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SportsEsports
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
import com.retropack.manager.ui.theme.PlatformGameBoy
import com.retropack.manager.ui.theme.PlatformGameBoyAdvance
import com.retropack.manager.ui.theme.PlatformGameBoyColor
import com.retropack.manager.ui.theme.RetroDarkOutline
import com.retropack.manager.ui.theme.RetroDarkSurfaceElevated
import com.retropack.manager.ui.theme.RetroError
import com.retropack.manager.ui.theme.RetroPrimary
import com.retropack.manager.ui.theme.RetroSuccess
import com.retropack.manager.util.UriUtils

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RomInspectionCard(
    identity: RomIdentity,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val platformColor = when (identity.platform.lowercase()) {
        "gba" -> PlatformGameBoyAdvance
        "gbc" -> PlatformGameBoyColor
        else -> PlatformGameBoy
    }

    val platformLabel = when (identity.platform.lowercase()) {
        "gba" -> "GAME BOY ADVANCE"
        "gbc" -> "GAME BOY COLOR"
        else -> "GAME BOY"
    }

    RetroCard(
        modifier = modifier,
        containerColor = RetroDarkSurfaceElevated.copy(alpha = 0.7f),
        borderColor = platformColor.copy(alpha = 0.4f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Platform Badge + Verification Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(platformColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = platformLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.2.sp
                            ),
                            color = platformColor
                        )
                    }

                    if (identity.hasBattery) {
                        Box(
                            modifier = Modifier
                                .background(RetroSuccess.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Save,
                                    contentDescription = null,
                                    tint = RetroSuccess,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "BATTERY SRAM",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = RetroSuccess
                                )
                            }
                        }
                    }
                }

                // Header Integrity Indicator
                val isFullyValid = identity.headerChecksumValid && identity.logoOrFixedValid
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (isFullyValid) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = if (isFullyValid) RetroSuccess else RetroError,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (isFullyValid) "VERIFIED" else "HEADER ISSUE",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isFullyValid) RetroSuccess else RetroError
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Game Title & Details
            Text(
                text = identity.gameTitle,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Metadata Row: Game Code / Maker / Version / Size
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
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

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = RetroDarkOutline.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(12.dp))

            // Checksums Section
            Text(
                text = "CRYPTOGRAPHIC CHECKSUMS",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface
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
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = RetroPrimary
                    ),
                    modifier = Modifier.width(54.dp)
                )
                Text(
                    text = hash,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    maxLines = 1
                )
            }

            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy $label",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(14.dp)
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
