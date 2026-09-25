package com.retropack.manager.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.retropack.manager.ui.theme.RetroDarkOutline
import com.retropack.manager.ui.theme.RetroDarkSurfaceElevated
import com.retropack.manager.ui.theme.RetroPrimary

@Composable
fun IconPreviewCard(
    foregroundBytes: ByteArray?,
    onPickImage: () -> Unit,
    onResetDefault: () -> Unit,
    modifier: Modifier = Modifier
) {
    val foregroundBitmap = remember(foregroundBytes) {
        foregroundBytes?.let {
            try {
                BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }

    RetroCard(
        modifier = modifier,
        containerColor = RetroDarkSurfaceElevated.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Adaptive Icon Preview (Squircle Mask)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0F141C))
                        .then(
                            if (foregroundBitmap != null) Modifier else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (foregroundBitmap != null) {
                        Image(
                            bitmap = foregroundBitmap,
                            contentDescription = "Launcher Icon Foreground",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
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
                    Text(
                        text = if (foregroundBitmap != null) "Custom Boxart Icon" else "Default Retro Icon",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (foregroundBitmap != null) "Adaptive layer generated" else "Tap to choose custom PNG/JPG",
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
                if (foregroundBitmap != null) {
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
                    modifier = Modifier.height(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = null,
                        tint = RetroPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (foregroundBitmap != null) "Change" else "Pick Icon",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = RetroPrimary
                    )
                }
            }
        }
    }
}
