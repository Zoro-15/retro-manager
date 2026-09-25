package com.retropack.manager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retropack.manager.ui.theme.RetroTerminalBg
import com.retropack.manager.ui.theme.RetroTerminalCommand
import com.retropack.manager.ui.theme.RetroTerminalError
import com.retropack.manager.ui.theme.RetroTerminalStage
import com.retropack.manager.ui.theme.RetroTerminalSuccess
import com.retropack.manager.ui.theme.RetroTerminalText
import com.retropack.manager.ui.theme.RetroTerminalWarning

@Composable
fun TerminalView(
    logs: String,
    modifier: Modifier = Modifier,
    fillHeight: Boolean = false
) {
    if (logs.isBlank()) return

    val scrollState = rememberScrollState()

    val annotatedText = buildAnnotatedString {
        val lines = logs.split('\n')
        lines.forEachIndexed { index, line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("$") -> {
                    withStyle(SpanStyle(color = RetroTerminalCommand, fontWeight = FontWeight.Bold)) {
                        append(line)
                    }
                }
                trimmed.startsWith("[✓]") || trimmed.contains("SUCCESS") || trimmed.contains("PASSED") -> {
                    withStyle(SpanStyle(color = RetroTerminalSuccess, fontWeight = FontWeight.SemiBold)) {
                        append(line)
                    }
                }
                trimmed.startsWith("[✗]") || trimmed.contains("FAILED") || trimmed.contains("ERROR") -> {
                    withStyle(SpanStyle(color = RetroTerminalError, fontWeight = FontWeight.Bold)) {
                        append(line)
                    }
                }
                trimmed.startsWith("--> Stage") || trimmed.startsWith("[STAGE") -> {
                    withStyle(SpanStyle(color = RetroTerminalStage, fontWeight = FontWeight.Bold)) {
                        append(line)
                    }
                }
                trimmed.startsWith("[!]") || trimmed.contains("WARN") -> {
                    withStyle(SpanStyle(color = RetroTerminalWarning, fontWeight = FontWeight.Medium)) {
                        append(line)
                    }
                }
                else -> {
                    withStyle(SpanStyle(color = RetroTerminalText)) {
                        append(line)
                    }
                }
            }

            if (index < lines.size - 1) {
                append("\n")
            }
        }
    }

    LaunchedEffect(logs) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (fillHeight) Modifier.fillMaxHeight() else Modifier.heightIn(max = 420.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(RetroTerminalBg)
            .padding(14.dp)
            .verticalScroll(scrollState)
    ) {
        SelectionContainer {
            Text(
                text = annotatedText,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            )
        }
    }
}
