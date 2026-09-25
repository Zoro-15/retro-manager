package com.retropack.manager.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.retropack.manager.ui.theme.RetroDarkOutline
import com.retropack.manager.ui.theme.RetroDarkSurfaceVariant
import com.retropack.manager.ui.theme.RetroPrimary

@Composable
fun ActionTile(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    badgeText: String? = null,
    selected: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (pressSource, pressModifier) = rememberPressScale()

    Surface(
        onClick = onClick,
        interactionSource = pressSource,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) RetroPrimary.copy(alpha = 0.12f) else RetroDarkSurfaceVariant.copy(alpha = 0.5f),
        border = if (selected) {
            BorderStroke(1.5.dp, RetroPrimary.copy(alpha = 0.8f))
        } else {
            BorderStroke(1.dp, RetroDarkOutline.copy(alpha = 0.3f))
        },
        modifier = modifier
            .fillMaxWidth()
            .then(pressModifier)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (icon != null) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(
                                if (selected) RetroPrimary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                                RoundedCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (selected) RetroPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (selected) RetroPrimary else MaterialTheme.colorScheme.onSurface
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            if (badgeText != null) {
                Box(
                    modifier = Modifier
                        .background(
                            if (selected) RetroPrimary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) RetroPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun FileSelectorCard(
    label: String,
    fileName: String?,
    fileSizeFormatted: String? = null,
    placeholder: String,
    badge: String? = null,
    icon: ImageVector = Icons.Default.FolderOpen,
    onSelect: () -> Unit,
    onClear: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val (pressSource, pressModifier) = rememberPressScale()

    Surface(
        onClick = onSelect,
        interactionSource = pressSource,
        shape = RoundedCornerShape(18.dp),
        color = RetroDarkSurfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, RetroDarkOutline.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .then(pressModifier)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            if (fileName != null) RetroPrimary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                            RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (fileName != null) RetroPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = RetroPrimary
                        )
                        if (badge != null) {
                            Box(
                                modifier = Modifier
                                    .background(RetroPrimary.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = badge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = RetroPrimary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = fileName ?: placeholder,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (fileName != null) FontWeight.Medium else FontWeight.Normal
                        ),
                        color = if (fileName != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (fileSizeFormatted != null && fileName != null) {
                        Text(
                            text = fileSizeFormatted,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
