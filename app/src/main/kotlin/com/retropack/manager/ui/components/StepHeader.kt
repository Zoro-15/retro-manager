package com.retropack.manager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.retropack.manager.ui.theme.RetroPrimary
import com.retropack.manager.ui.theme.RetroSuccess

@Composable
fun StepHeader(
    number: String,
    title: String,
    subtitle: String? = null,
    isCompleted: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        val pillBg = if (isCompleted) RetroSuccess.copy(alpha = 0.15f) else RetroPrimary.copy(alpha = 0.15f)
        val pillText = if (isCompleted) RetroSuccess else RetroPrimary

        Box(
            modifier = Modifier
                .size(32.dp)
                .background(pillBg, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                ),
                color = pillText
            )
        }

        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun StepConnector(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(start = 15.dp, top = 4.dp, bottom = 4.dp)
            .width(2.dp)
            .height(20.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(1.dp))
    )
}
