package com.retropack.manager.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.retropack.manager.ui.theme.RetroDarkBackground
import com.retropack.manager.ui.theme.RetroDarkOutline
import com.retropack.manager.ui.theme.RetroDarkSurfaceElevated
import com.retropack.manager.ui.theme.RetroDarkSurfaceVariant
import com.retropack.manager.ui.theme.RetroPrimary

/**
 * Interactive Adaptive Icon Live Preview Card with Libretro Boxart Scraping.
 * Scaled 140dp preview viewport with ergonomic touch targets.
 */
@Composable
fun IconPreviewCard(
    foregroundBytes: ByteArray?,
    backgroundBytes: ByteArray? = null,
    isScraping: Boolean = false,
    onPickImage: () -> Unit,
    onResetDefault: () -> Unit,
    onFetchOnline: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var selectedMask by remember { mutableStateOf(AdaptiveIconComposer.MaskShape.SQUIRCLE) }

    val previewBitmap = remember(foregroundBytes, backgroundBytes, selectedMask) {
        if (foregroundBytes == null) return@remember null
        try {
            if (backgroundBytes != null) {
                // Foreground and background layers are already synthesized; decode and composite directly without shrinking
                val bg = BitmapFactory.decodeByteArray(backgroundBytes, 0, backgroundBytes.size)
                val fg = BitmapFactory.decodeByteArray(foregroundBytes, 0, foregroundBytes.size)
                if (bg != null && fg != null) {
                    AdaptiveIconComposer.generateCompositePreview(bg, fg, selectedMask).asImageBitmap()
                } else null
            } else {
                // Raw image provided: compose adaptive icon layers
                val res = AdaptiveIconComposer.composeAdaptiveIcon(foregroundBytes)
                if (res != null) {
                    val bg = BitmapFactory.decodeByteArray(res.backgroundPng, 0, res.backgroundPng.size)
                    val fg = BitmapFactory.decodeByteArray(res.foregroundPng, 0, res.foregroundPng.size)
                    if (bg != null && fg != null) {
                        AdaptiveIconComposer.generateCompositePreview(bg, fg, selectedMask).asImageBitmap()
                    } else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    RetroCard(
        modifier = modifier,
        containerColor = RetroDarkSurfaceElevated.copy(alpha = 0.6f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Prominent 140dp Viewport for Adaptive Icon Preview
            val maskShape = when (selectedMask) {
                AdaptiveIconComposer.MaskShape.CIRCLE -> CircleShape
                AdaptiveIconComposer.MaskShape.ROUNDED_RECTANGLE -> RoundedCornerShape(22.dp)
                AdaptiveIconComposer.MaskShape.SQUIRCLE -> RoundedCornerShape(36.dp)
            }

            Box(
                modifier = Modifier
                    .size(140.dp)
                    .clip(maskShape)
                    .background(RetroDarkBackground)
                    .border(BorderStroke(1.5.dp, RetroDarkOutline.copy(alpha = 0.6f)), maskShape),
                contentAlignment = Alignment.Center
            ) {
                if (isScraping) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 3.dp
                        )
                        Text(
                            text = "Scraping CDN...",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                } else if (previewBitmap != null) {
                    Image(
                        bitmap = previewBitmap,
                        contentDescription = "Adaptive Launcher Icon",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SportsEsports,
                            contentDescription = "Default Retro Icon",
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Default Icon",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Title & Status
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (isScraping) {
                        "Scraping Libretro Thumbnails..."
                    } else if (foregroundBytes != null) {
                        "Official Boxart (Adaptive 108dp)"
                    } else {
                        "Launcher Icon & Boxart"
                    },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = if (isScraping) {
                        "Querying multi-tier Libretro CDN across 14-console architectures..."
                    } else if (foregroundBytes != null) {
                        "72dp safe-zone centered + 3D drop shadow synthesized"
                    } else {
                        "Tap below to fetch official CDN boxart or pick custom PNG"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Launcher Shape Selector Pills
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    AdaptiveIconComposer.MaskShape.SQUIRCLE to "Squircle",
                    AdaptiveIconComposer.MaskShape.CIRCLE to "Circle",
                    AdaptiveIconComposer.MaskShape.ROUNDED_RECTANGLE to "Square"
                ).forEach { (shape, label) ->
                    val isSelected = selectedMask == shape
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) Color.White else RetroDarkSurfaceVariant,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) Color.White else RetroDarkOutline.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.clickable { selectedMask = shape }
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal,
                                fontSize = 12.sp
                            ),
                            color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            // Prominent Action Touch Targets (Min 48dp Height)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onFetchOnline != null) {
                    OutlinedButton(
                        onClick = onFetchOnline,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.7f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        enabled = !isScraping
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Fetch Online Boxart",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 1
                        )
                    }
                }

                Button(
                    onClick = onPickImage,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RetroDarkSurfaceVariant,
                        contentColor = Color.White
                    ),
                    border = BorderStroke(1.dp, RetroDarkOutline.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    enabled = !isScraping
                ) {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (foregroundBytes != null) "Change Artwork" else "Pick Custom",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 1
                    )
                }

                if (foregroundBytes != null && !isScraping) {
                    IconButton(
                        onClick = onResetDefault,
                        modifier = Modifier
                            .size(48.dp)
                            .background(RetroDarkSurfaceVariant, RoundedCornerShape(14.dp))
                            .border(BorderStroke(1.dp, RetroDarkOutline.copy(alpha = 0.5f)), RoundedCornerShape(14.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Reset Icon to Default",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}
