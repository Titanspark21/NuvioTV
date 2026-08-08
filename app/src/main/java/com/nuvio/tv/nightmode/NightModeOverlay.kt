package com.nuvio.tv.nightmode

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Purely decorative night mode dimming overlay.
 * Must NOT use clickable, focusable, pointerInput, or any modifiers that consume input events.
 */
@Composable
fun NightModeOverlay(
    enabled: Boolean,
    strengthPercent: Int,
    modifier: Modifier = Modifier
) {
    if (!enabled || strengthPercent <= 0) return

    val alpha = (strengthPercent.coerceIn(0, 70) / 100f)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = alpha))
    )
}
