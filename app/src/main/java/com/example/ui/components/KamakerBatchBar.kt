package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun KamakerColorPickerDialog(
    title: String,
    initialColorHex: Long,
    onDismiss: () -> Unit,
    onColorSelected: (Long) -> Unit
) {
    val swatches = listOf(
        0xFFFFFFFFL, 0xFF000000L, 0xFFFF6D3BL, 0xFF00E5FFL,
        0xFFFFB300L, 0xFF00E676L, 0xFFFF5252L, 0xFFA78BFAL,
        0xFFFDE047L, 0xFF94A3B8L
    )
    val initialInt = initialColorHex.toInt()
    var r by remember { mutableFloatStateOf(android.graphics.Color.red(initialInt).toFloat()) }
    var g by remember { mutableFloatStateOf(android.graphics.Color.green(initialInt).toFloat()) }
    var b by remember { mutableFloatStateOf(android.graphics.Color.blue(initialInt).toFloat()) }

    val previewHex = (0xFFL shl 24) or
        ((r.toInt().coerceIn(0, 255).toLong()) shl 16) or
        ((g.toInt().coerceIn(0, 255).toLong()) shl 8) or
        (b.toInt().coerceIn(0, 255).toLong())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(previewHex))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    swatches.take(5).forEach { hex ->
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(hex))
                                .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                .clickable {
                                    val c = hex.toInt()
                                    r = android.graphics.Color.red(c).toFloat()
                                    g = android.graphics.Color.green(c).toFloat()
                                    b = android.graphics.Color.blue(c).toFloat()
                                }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    swatches.drop(5).forEach { hex ->
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(hex))
                                .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                .clickable {
                                    val c = hex.toInt()
                                    r = android.graphics.Color.red(c).toFloat()
                                    g = android.graphics.Color.green(c).toFloat()
                                    b = android.graphics.Color.blue(c).toFloat()
                                }
                        )
                    }
                }
                KamakerRgbSlider("Red", r, Color(0xFFFF5252)) { r = it }
                KamakerRgbSlider("Green", g, Color(0xFF00E676)) { g = it }
                KamakerRgbSlider("Blue", b, Color(0xFF38BDF8)) { b = it }
            }
        },
        confirmButton = {
            Button(
                onClick = { onColorSelected(previewHex) },
                colors = ButtonDefaults.buttonColors(containerColor = KamakerAccentOrange, contentColor = Color.White)
            ) {
                Text("Apply Colour")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun KamakerRgbSlider(
    label: String,
    value: Float,
    tint: Color,
    onChange: (Float) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(42.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = 0f..255f,
            colors = SliderDefaults.colors(thumbColor = tint, activeTrackColor = tint),
            modifier = Modifier.weight(1f)
        )
        Text("${value.toInt()}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(28.dp))
    }
}
