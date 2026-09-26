package com.retropack.manager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retropack.manager.ui.screens.ControllerMappingScreen
import com.retropack.manager.ui.screens.InstalledGamesScreen
import com.retropack.manager.ui.screens.MainScreen
import com.retropack.manager.ui.theme.RetroDarkBackground
import com.retropack.manager.ui.theme.RetroDarkOutline
import com.retropack.manager.ui.theme.RetroDarkSurfaceElevated
import com.retropack.manager.ui.theme.RetroPackTheme
import com.retropack.manager.ui.theme.RetroPrimary
import com.retropack.manager.viewmodel.MainViewModel

enum class ManagerTab(val title: String, val shortLabel: String, val icon: ImageVector) {
    TRANSFORM("Transformer", "Transform", Icons.Default.AutoAwesome),
    LIBRARY("Library & Saves", "Library", Icons.Default.Save),
    CONTROLLERS("Controllers", "Gamepad", Icons.Default.Gamepad)
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            RetroPackTheme(darkTheme = true) {
                var currentTab by remember { mutableStateOf(ManagerTab.TRANSFORM) }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = RetroDarkBackground
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                    ) {
                        // Top Pill Navigation Tab Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp)
                                .background(RetroDarkSurfaceElevated.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                                .border(1.dp, RetroDarkOutline.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            ManagerTab.values().forEach { tab ->
                                val isSelected = currentTab == tab
                                val bgBrush = if (isSelected) {
                                    Brush.horizontalGradient(listOf(RetroPrimary, Color(0xFF06B6D4)))
                                } else {
                                    Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(bgBrush)
                                        .clickable { currentTab = tab }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = tab.icon,
                                            contentDescription = tab.title,
                                            tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = tab.shortLabel,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                letterSpacing = 0.3.sp
                                            ),
                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        // Tab Viewport with Smooth Crossfade Animation
                        Box(modifier = Modifier.fillMaxSize()) {
                            Crossfade(
                                targetState = currentTab,
                                animationSpec = tween(220),
                                label = "TabCrossfade"
                            ) { target ->
                                when (target) {
                                    ManagerTab.TRANSFORM -> MainScreen(viewModel = viewModel)
                                    ManagerTab.LIBRARY -> InstalledGamesScreen()
                                    ManagerTab.CONTROLLERS -> ControllerMappingScreen()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
