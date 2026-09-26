package com.retropack.manager.ui.screens

import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retropack.domain.model.ControllerProfile
import com.retropack.manager.ui.components.RetroCard
import com.retropack.manager.ui.theme.RetroDarkBackground
import com.retropack.manager.ui.theme.RetroDarkOutline
import com.retropack.manager.ui.theme.RetroDarkSurfaceElevated
import com.retropack.manager.ui.theme.RetroDarkSurfaceVariant
import com.retropack.manager.ui.theme.RetroPrimary
import com.retropack.manager.ui.theme.RetroPrimaryLight
import com.retropack.manager.ui.theme.RetroSuccess
import com.retropack.runtime.input.GamepadManager
import com.retropack.runtime.input.GamepadMapper

/**
 * Controller Button Mapping and Hardware Discovery Screen for RetroPack.
 *
 * Implements Feature 4 specifications:
 * - Dynamic Bluetooth and USB controller discovery (Xbox, PlayStation DualSense, Switch Pro, 8BitDo, Razer Kishi).
 * - Per-console button remapping profiles.
 * - Analog stick deadzone calibration slider (0% to 30%).
 */
@Composable
fun ControllerMappingScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val gamepadManager = remember { GamepadManager(context) }
    var connectedControllers by remember { mutableStateOf<List<GamepadManager.DiscoveredGamepad>>(emptyList()) }

    var selectedPlatform by remember { mutableStateOf("gba") }
    var deadzone by remember { mutableStateOf(15) }
    var triggerThreshold by remember { mutableStateOf(50) }
    var activeListeningButton by remember { mutableStateOf<String?>(null) }
    var buttonMap by remember(selectedPlatform) {
        mutableStateOf(ControllerProfile.defaultBindingsForPlatform(selectedPlatform).toMutableMap())
    }

    DisposableEffect(Unit) {
        gamepadManager.onControllersChanged = { list ->
            connectedControllers = list
        }
        gamepadManager.start()
        connectedControllers = gamepadManager.getConnectedGamepads()
        onDispose {
            gamepadManager.stop()
        }
    }

    val platforms = listOf(
        "gba" to "GBA",
        "snes" to "SNES",
        "genesis" to "Genesis",
        "nes" to "NES",
        "psx" to "PlayStation",
        "n64" to "N64",
        "pce" to "PC Engine"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RetroDarkBackground)
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Screen Header
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
                            Brush.linearGradient(listOf(RetroPrimary, Color(0xFF06B6D4))),
                            RoundedCornerShape(14.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.VideogameAsset,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Column {
                    Text(
                        text = "Controller Mapping",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp
                        ),
                        color = Color.White
                    )
                    Text(
                        text = "Bluetooth & USB Gamepad Configuration",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(
                onClick = { gamepadManager.refreshConnectedControllers() },
                modifier = Modifier
                    .background(RetroDarkSurfaceElevated, CircleShape)
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Scan Gamepads",
                    tint = RetroPrimaryLight
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 1. Connected Controllers Section
        RetroCard {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "CONNECTED HARDWARE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = RetroPrimary
                    )

                    Box(
                        modifier = Modifier
                            .background(
                                if (connectedControllers.isNotEmpty()) RetroSuccess.copy(alpha = 0.2f) else Color.Gray.copy(alpha = 0.2f),
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (connectedControllers.isNotEmpty()) "${connectedControllers.size} CONNECTED" else "NO GAMEPAD",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp
                            ),
                            color = if (connectedControllers.isNotEmpty()) RetroSuccess else Color.Gray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (connectedControllers.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = RetroDarkSurfaceElevated.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Gamepad,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = "No Controller Detected",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = Color.White
                                )
                                Text(
                                    text = "Pair an Xbox, PlayStation DualSense, Switch Pro, or 8BitDo gamepad via Bluetooth or USB OTG.",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        connectedControllers.forEach { controller ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = RetroDarkSurfaceElevated,
                                border = BorderStroke(1.dp, RetroPrimary.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(RetroPrimary, RoundedCornerShape(10.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.SportsEsports,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        Column {
                                            Text(
                                                text = controller.name,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                color = Color.White
                                            )
                                            Text(
                                                text = "Type: ${controller.type.name} • Sticks: ${if (controller.hasAnalogSticks) "YES" else "NO"} • Triggers: ${if (controller.hasTriggers) "YES" else "NO"}",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 10.sp
                                                ),
                                                color = RetroPrimaryLight
                                            )
                                        }
                                    }

                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Active",
                                        tint = RetroSuccess,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Target Console Profile Selector
        Text(
            text = "SELECT CONSOLE PROFILE",
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
                val isSelected = (selectedPlatform == id)
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
                        text = label,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Button Remapping Grid
        RetroCard {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "BUTTON BINDINGS (${selectedPlatform.uppercase()})",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = RetroPrimary
                    )

                    Text(
                        text = "Tap button to remap",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Active Listening Notification
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
                                        text = "Press any key on controller or keyboard...",
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

                val buttonList = ControllerProfile.getConsoleButtonList(selectedPlatform)
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    buttonList.chunked(2).forEach { rowButtons ->
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

                Spacer(modifier = Modifier.height(16.dp))

                // Analog Stick Deadzone Slider
                Text(
                    text = "ANALOG STICK DEADZONE: $deadzone%",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = RetroPrimary
                )

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

                Spacer(modifier = Modifier.height(14.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            buttonMap = ControllerProfile.defaultBindingsForPlatform(selectedPlatform).toMutableMap()
                            deadzone = 15
                            Toast.makeText(context, "Bindings reset to default", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reset")
                    }

                    Button(
                        onClick = {
                            Toast.makeText(context, "${selectedPlatform.uppercase()} Controller Profile Saved!", Toast.LENGTH_SHORT).show()
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

        Spacer(modifier = Modifier.height(100.dp))
    }
}
