package com.retropack.manager.ui.components

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retropack.manager.service.AdaptiveIconComposer
import com.retropack.manager.ui.theme.RetroDarkOutline
import com.retropack.manager.ui.theme.RetroDarkSurfaceElevated
import com.retropack.manager.ui.theme.RetroPrimary

/**
 * Interactive Adaptive Icon Live Preview Card with Libretro Boxart Scraping.
 *
 * Implements Feature 5 specifications:
 * - Live Adaptive Icon compositor with 72dp safe-zone centering & 3D drop shadow.
 * - Interactive Launcher Mask Preview Switcher (Squircle, Circle, Rounded Rectangle).
 * - Real-time scraping status & online/device artwork selection.
 */
@Composable
fun IconPreviewCard(
    foregroundBytes: ByteArray?,
    isScraping: Boolean = false,
    onPickImage: () -> Unit,
    onResetDefault: () -> Unit,
    onFetchOnline: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var selectedMask by remember { mutableStateOf(AdaptiveIconComposer.MaskShape.SQUIRCLE) }

    val adaptiveResult = remember(foregroundBytes) {
        foregroundBytes?.let {
            try {
                AdaptiveIconComposer.composeAdaptiveIcon(it)
            } catch (_: Exception) {
                null
            }
        }
    }

    val previewBitmap = remember(adaptiveResult, selectedMask) {
        adaptiveResult?.let { res ->
            try {
                val bg = BitmapFactory.decodeByteArray(res.backgroundPng, 0, res.backgroundPng.size)
                val fg = BitmapFactory.decodeByteArray(res.foregroundPng, 0, res.foregroundPng.size)
                if (bg != null && fg != null) {
                    AdaptiveIconComposer.generateCompositePreview(bg, fg, selectedMask).asImageBitmap()
                } else null
            } catch (_: Exception) {
                null
            }
        }
    }

    RetroCard(
        modifier = modifier,
        containerColor = RetroDarkSurfaceElevated.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Adaptive Icon Preview
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(
                                when (selectedMask) {
                                    AdaptiveIconComposer.MaskShape.CIRCLE -> CircleShape
                                    AdaptiveIconComposer.MaskShape.ROUNDED_RECTANGLE -> RoundedCornerShape(8.dp)
                                    AdaptiveIconComposer.MaskShape.SQUIRCLE -> RoundedCornerShape(16.dp)
                                }
                            )
                            .background(Color(0xFF0F141C)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isScraping) {
                            CircularProgressIndicator(
                                color = RetroPrimary,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else if (previewBitmap != null) {
                            Image(
                                bitmap = previewBitmap,
                                contentDescription = "Adaptive Launcher Icon",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.SportsEsports,
                                contentDescription = "Default Retro Icon",
                                tint = RetroPrimary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = if (isScraping) {
                                    "Scraping Libretro Thumbnails..."
                                } else if (foregroundBytes != null) {
                                    "Official Boxart (Adaptive)"
                                } else {
                                    "Default Retro Icon"
                                },
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = if (isScraping) {
                                "Querying multi-tier Libretro CDN..."
                            } else if (foregroundBytes != null) {
                                "108dp adaptive layers + 3D shadow synthesized"
                            } else {
                                "Tap to scrape official boxart or pick custom PNG"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Actions
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (onFetchOnline != null && !isScraping) {
                        IconButton(
                            onClick = onFetchOnline,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = "Fetch Libretro Boxart",
                                tint = RetroPrimary
                            )
                        }
                    }

                    if (foregroundBytes != null && !isScraping) {
                        IconButton(
                            onClick = onResetDefault,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Reset Icon",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onPickImage,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, RetroPrimary.copy(alpha = 0.5f)),
                        modifier = Modifier.height(38.dp),
                        enabled = !isScraping
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = null,
                            tint = RetroPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (foregroundBytes != null) "Change" else "Pick Icon",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = RetroPrimary
                        )
                    }
                }
            }

            // Mask Shape Selector Pills when boxart is active
            if (foregroundBytes != null && !isScraping) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Launcher Shape:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    listOf(
                        AdaptiveIconComposer.MaskShape.SQUIRCLE to "Squircle",
                        AdaptiveIconComposer.MaskShape.CIRCLE to "Circle",
                        AdaptiveIconComposer.MaskShape.ROUNDED_RECTANGLE to "Square"
                    ).forEach { (shape, label) ->
                        val isSelected = selectedMask == shape
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) RetroPrimary.copy(alpha = 0.2f) else Color.Transparent,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) RetroPrimary else RetroDarkOutline.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.clickable { selectedMask = shape }
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 11.sp
                                ),
                                color = if (isSelected) RetroPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
