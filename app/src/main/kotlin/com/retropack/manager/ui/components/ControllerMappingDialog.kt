package com.retropack.manager.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.retropack.domain.model.ControllerProfile
import com.retropack.manager.ui.theme.RetroDarkBackground
import com.retropack.manager.ui.theme.RetroDarkOutline
import com.retropack.manager.ui.theme.RetroDarkSurfaceElevated
import com.retropack.manager.ui.theme.RetroDarkSurfaceVariant
import com.retropack.manager.ui.theme.RetroPrimary
import com.retropack.manager.ui.theme.RetroPrimaryLight
import com.retropack.runtime.input.GamepadMapper

/**
 * Interactive Visual Controller Remapping Modal Dialog for RetroPack.
 *
 * Implements Feature 4 specifications:
 * - Visual controller graphic with interactive remappable button slots.
 * - Per-console profiles (GBA, SNES, Genesis, NES, PS1, N64, PCE).
 * - Analog stick deadzone slider (0% to 30%).
 * - Interactive listening state: prompts user "Press button on controller to bind...".
 */
@Composable
fun ControllerMappingDialog(
    isOpen: Boolean,
    initialPlatform: String = "gba",
    activeDeviceName: String = "Physical Gamepad",
    onSaveProfile: (ControllerProfile) -> Unit,
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    var selectedPlatform by remember { mutableStateOf(initialPlatform) }
    var deadzone by remember { mutableStateOf(15) }
    var buttonMap by remember(selectedPlatform) {
        mutableStateOf(ControllerProfile.defaultBindingsForPlatform(selectedPlatform).toMutableMap())
    }
    var activeListeningButton by remember { mutableStateOf<String?>(null) }

    val platforms = listOf(
        "gba" to "Game Boy Advance",
        "snes" to "Super Nintendo",
        "genesis" to "Sega Genesis",
        "nes" to "NES / Famicom",
        "psx" to "PlayStation 1",
        "n64" to "Nintendo 64",
        "pce" to "PC Engine"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = RetroDarkBackground),
            border = BorderStroke(1.5.dp, RetroPrimary.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
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
                                .size(40.dp)
                                .background(
                                    Brush.linearGradient(listOf(RetroPrimary, Color(0xFF06B6D4))),
                                    RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.VideogameAsset,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "Controller Button Mapping",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Text(
                                text = activeDeviceName,
                                style = MaterialTheme.typography.bodySmall,
                                color = RetroPrimaryLight
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Console Profile Tabs
                Text(
                    text = "TARGET CONSOLE PROFILE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = RetroPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(platforms) { (id, label) ->
                        val isSelected = (selectedPlatform.lowercase() == id.lowercase())
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) RetroPrimary else RetroDarkSurfaceElevated,
                            border = BorderStroke(1.dp, if (isSelected) Color.White else RetroDarkOutline.copy(alpha = 0.3f)),
                            modifier = Modifier.clickable {
                                selectedPlatform = id
                                buttonMap = ControllerProfile.defaultBindingsForPlatform(id).toMutableMap()
                                activeListeningButton = null
                            }
                        ) {
                            Text(
                                text = id.uppercase(),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Active Listening Banner
                AnimatedVisibility(
                    visible = activeListeningButton != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    activeListeningButton?.let { targetBtn ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF7C2D12),
                            border = BorderStroke(1.dp, Color(0xFFFB923C)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "BINDING: $targetBtn",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFFFED7AA)
                                    )
                                    Text(
                                        text = "Press any button or trigger on your physical controller...",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = Color.White
                                    )
                                }

                                OutlinedButton(
                                    onClick = { activeListeningButton = null },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                                ) {
                                    Text("Cancel", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // Interactive Button Mapping Grid
                Text(
                    text = "ACTION & DIRECTIONAL BUTTONS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = RetroPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))

                val buttons = ControllerProfile.getConsoleButtonList(selectedPlatform)
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    buttons.chunked(2).forEach { rowButtons ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowButtons.forEach { btn ->
                                val keyCode = buttonMap[btn] ?: 0
                                val keyLabel = GamepadMapper.getKeyLabel(keyCode)
                                val isListening = (activeListeningButton == btn)

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isListening) Color(0xFF4338CA) else RetroDarkSurfaceElevated,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isListening) Color(0xFFA5B4FC) else RetroDarkOutline.copy(alpha = 0.35f)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            activeListeningButton = if (activeListeningButton == btn) null else btn
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(
                                                text = btn,
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontWeight = FontWeight.ExtraBold
                                                ),
                                                color = if (isListening) Color.White else RetroPrimaryLight
                                            )
                                            Text(
                                                text = if (isListening) "PRESS KEY..." else keyLabel,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 11.sp
                                                ),
                                                color = if (isListening) Color(0xFFFDE047) else Color.White
                                            )
                                        }

                                        Icon(
                                            imageVector = Icons.Default.SportsEsports,
                                            contentDescription = null,
                                            tint = if (isListening) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                            if (rowButtons.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Analog Stick Deadzone Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "ANALOG STICK DEADZONE: $deadzone%",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = RetroPrimary
                    )
                }

                Slider(
                    value = deadzone.toFloat(),
                    onValueChange = { deadzone = it.toInt() },
                    valueRange = 0f..30f,
                    steps = 29,
                    colors = SliderDefaults.colors(
                        thumbColor = RetroPrimary,
                        activeTrackColor = RetroPrimary,
                        inactiveTrackColor = RetroDarkOutline.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Action Footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            buttonMap = ControllerProfile.defaultBindingsForPlatform(selectedPlatform).toMutableMap()
                            deadzone = 15
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reset")
                    }

                    Button(
                        onClick = {
                            val profile = ControllerProfile(
                                profileName = "${selectedPlatform.uppercase()} Custom Profile",
                                platform = selectedPlatform,
                                deviceDescriptor = activeDeviceName,
                                buttonBindings = buttonMap,
                                deadzonePercent = deadzone
                            )
                            onSaveProfile(profile)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RetroPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.5f)
                    ) {
                        Text("Save Profile", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
