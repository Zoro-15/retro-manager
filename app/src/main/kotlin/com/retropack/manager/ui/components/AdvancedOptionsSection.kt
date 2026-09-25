package com.retropack.manager.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.retropack.manager.ui.theme.RetroDarkOutline
import com.retropack.manager.ui.theme.RetroDarkSurfaceVariant
import com.retropack.manager.ui.theme.RetroPrimary

@Composable
fun AdvancedOptionsSection(
    scaleMode: String,
    onScaleModeChange: (String) -> Unit,
    touchEnabled: Boolean,
    onTouchEnabledChange: (Boolean) -> Unit,
    touchOpacity: Float,
    onTouchOpacityChange: (Float) -> Unit,
    touchHaptics: Boolean,
    onTouchHapticsChange: (Boolean) -> Unit,
    gamepadEnabled: Boolean,
    onGamepadEnabledChange: (Boolean) -> Unit,
    gamepadAutoHide: Boolean,
    onGamepadAutoHideChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        val headerShape = if (expanded) {
            RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
        } else {
            RoundedCornerShape(18.dp)
        }

        Surface(
            onClick = { expanded = !expanded },
            shape = headerShape,
            color = RetroDarkSurfaceVariant.copy(alpha = 0.45f),
            border = BorderStroke(1.dp, RetroDarkOutline.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 18.dp, vertical = 14.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = RetroPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Runtime & Controls Configuration",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessLow)),
            exit = shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessLow))
        ) {
            Card(
                shape = RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = RetroDarkSurfaceVariant.copy(alpha = 0.25f)
                ),
                border = BorderStroke(1.dp, RetroDarkOutline.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {

                    // 1. Scaling Mode Selector
                    Text(
                        text = "VIDEO SCALING MODE",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = RetroPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ActionTile(
                            title = "Integer Fit",
                            subtitle = "Pixel-perfect scaling",
                            selected = scaleMode == "integer_fit",
                            onClick = { onScaleModeChange("integer_fit") },
                            modifier = Modifier.weight(1f)
                        )
                        ActionTile(
                            title = "Aspect Fit",
                            subtitle = "Fills display bounds",
                            selected = scaleMode == "aspect_fit",
                            onClick = { onScaleModeChange("aspect_fit") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = RetroDarkOutline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // 2. Virtual Touch Controls Switch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Virtual Touch Overlay",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Render on-screen D-pad and action buttons",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = touchEnabled,
                            onCheckedChange = onTouchEnabledChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = RetroPrimary
                            )
                        )
                    }

                    if (touchEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))

                        // Touch Opacity Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Touch Overlay Opacity",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${(touchOpacity * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = RetroPrimary
                            )
                        }
                        Slider(
                            value = touchOpacity,
                            onValueChange = onTouchOpacityChange,
                            valueRange = 0.1f..1.0f,
                            steps = 9,
                            colors = SliderDefaults.colors(
                                thumbColor = RetroPrimary,
                                activeTrackColor = RetroPrimary
                            )
                        )

                        // Haptic Feedback Switch
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Touch Haptic Feedback",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Vibrate briefly on virtual button press",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = touchHaptics,
                                onCheckedChange = onTouchHapticsChange,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = RetroPrimary
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = RetroDarkOutline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // 3. Gamepad Settings
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Physical Gamepad HID Support",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Map Bluetooth / USB controllers directly",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = gamepadEnabled,
                            onCheckedChange = onGamepadEnabledChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = RetroPrimary
                            )
                        )
                    }

                    if (gamepadEnabled) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Auto-Hide Virtual Controls",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Fade out virtual touch buttons when controller is used",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = gamepadAutoHide,
                                onCheckedChange = onGamepadAutoHideChange,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = RetroPrimary
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
