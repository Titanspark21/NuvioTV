package com.nuvio.tv.nightmode

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.ui.screens.player.PlayerOverlayScaffold
import com.nuvio.tv.ui.theme.NuvioTheme

@Composable
fun NightModePlayerOverlay(
    visible: Boolean,
    enabled: Boolean,
    strengthPercent: Int,
    onToggleEnabled: () -> Unit,
    onStrengthChange: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val toggleFocusRequester = remember { FocusRequester() }
    val minusFocusRequester = remember { FocusRequester() }
    val plusFocusRequester = remember { FocusRequester() }

    LaunchedEffect(visible) {
        if (visible) {
            kotlinx.coroutines.delay(120)
            runCatching { toggleFocusRequester.requestFocus() }
        }
    }

    // Closes itself after a spell with no input. Back closes it too (see handleBackPress
    // in PlayerScreen); this is the safety net for someone who nudges the dimming and
    // then simply carries on watching, which is the common case.
    var lastInteraction by remember { mutableStateOf(0) }
    LaunchedEffect(visible, lastInteraction) {
        if (!visible) return@LaunchedEffect
        kotlinx.coroutines.delay(AUTO_CLOSE_MS)
        onDismiss()
    }
    val markInteraction = { lastInteraction++ }

    PlayerOverlayScaffold(
        visible = visible,
        onDismiss = onDismiss,
        modifier = modifier,
        captureKeys = false,
        contentPadding = PaddingValues(start = 44.dp, end = 44.dp, top = 28.dp, bottom = 64.dp)
    ) {
        Column(
            modifier = Modifier
                .width(420.dp)
                .align(Alignment.BottomStart)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Bottom
        ) {
            Text(
                text = stringResource(R.string.night_mode_title),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                modifier = Modifier.padding(bottom = NuvioTheme.spacing.md)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = NuvioTheme.spacing.xl),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                var isToggleFocused by remember { mutableStateOf(false) }
                Card(
                    onClick = { markInteraction(); onToggleEnabled() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(toggleFocusRequester)
                        .onFocusChanged { isToggleFocused = it.isFocused },
                    colors = CardDefaults.colors(
                        containerColor = if (enabled) NuvioTheme.colors.Secondary else Color.White.copy(alpha = 0.1f),
                        focusedContainerColor = if (enabled) NuvioTheme.colors.Secondary else Color.White.copy(alpha = 0.2f)
                    ),
                    shape = CardDefaults.shape(RoundedCornerShape(NuvioTheme.radii.md)),
                    border = CardDefaults.border(
                        border = Border(
                            border = BorderStroke(NuvioTheme.spacing.xxs, Color.Transparent),
                            shape = RoundedCornerShape(NuvioTheme.radii.md)
                        ),
                        focusedBorder = Border(
                            border = BorderStroke(NuvioTheme.spacing.xxs, NuvioTheme.colors.FocusRing),
                            shape = RoundedCornerShape(NuvioTheme.radii.md)
                        )
                    ),
                    scale = CardDefaults.scale(focusedScale = 1f, pressedScale = 1f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = NuvioTheme.spacing.lg, vertical = NuvioTheme.spacing.md),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.night_mode_enable),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (enabled) NuvioTheme.colors.OnSecondary else Color.White
                        )
                        Text(
                            text = if (enabled) "ON" else "OFF",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (enabled) NuvioTheme.colors.OnSecondary else Color.White.copy(alpha = 0.7f)
                        )
                    }
                }

                if (enabled) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.xs)
                    ) {
                        Text(
                            text = stringResource(R.string.night_mode_strength),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.92f)
                        )
                        Text(
                            text = "$strengthPercent%",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val canDecrease = strengthPercent > 0
                            val canIncrease = strengthPercent < 70

                            StepCard(
                                icon = Icons.Default.Remove,
                                enabled = canDecrease,
                                focusRequester = minusFocusRequester,
                                onClick = {
                                    markInteraction()
                                    onStrengthChange((strengthPercent - 5).coerceAtLeast(0))
                                }
                            )
                            StepCard(
                                icon = Icons.Default.Add,
                                enabled = canIncrease,
                                focusRequester = plusFocusRequester,
                                onClick = {
                                    markInteraction()
                                    onStrengthChange((strengthPercent + 5).coerceAtMost(70))
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    focusRequester: FocusRequester,
    onClick: () -> Unit
) {
    Card(
        onClick = { if (enabled) onClick() },
        modifier = Modifier
            .width(54.dp)
            .height(42.dp)
            .focusRequester(focusRequester),
        colors = CardDefaults.colors(
            containerColor = Color.White.copy(alpha = if (enabled) 0.15f else 0.05f),
            focusedContainerColor = NuvioTheme.colors.FocusBackground
        ),
        shape = CardDefaults.shape(RoundedCornerShape(NuvioTheme.radii.sm)),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(NuvioTheme.spacing.xxs, NuvioTheme.colors.FocusRing),
                shape = RoundedCornerShape(NuvioTheme.radii.sm)
            )
        ),
        scale = CardDefaults.scale(focusedScale = 1f, pressedScale = 1f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(42.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = if (enabled) 1f else 0.3f)
            )
        }
    }
}

private const val AUTO_CLOSE_MS = 6_000L
