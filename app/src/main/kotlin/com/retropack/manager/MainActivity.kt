package com.retropack.manager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.retropack.manager.ui.screens.MainScreen
import com.retropack.manager.ui.theme.RetroPackTheme
import com.retropack.manager.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            RetroPackTheme(darkTheme = true) {
                MainScreen(viewModel = viewModel)
            }
        }
    }
}
