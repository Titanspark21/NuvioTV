package com.nuvio.tv.ui.screens.detail

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shuffle
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.onPreviewKeyEvent
import com.nuvio.tv.ui.util.rememberLongPressKeyTracker
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.Border
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import com.nuvio.tv.ui.theme.NuvioTheme

/**
 * The same keys the episode cards and the play button treat as "select". Declared here
 * because the identical predicate in EpisodesSection is private to that file.
 */
private fun isShuffleSelectKey(keyCode: Int): Boolean {
    return keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
        keyCode == AndroidKeyEvent.KEYCODE_ENTER ||
        keyCode == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER
}

/**
 * Fork addition. The "play a random episode" control in the hero row.
 *
 * It carries a gradient and a little motion because it is the one button there that does
 * something unpredictable, and it should not read as another utility icon sitting beside
 * "add to library".
 *
 * The motion is deliberately restrained. The gradient drifts slowly at all times; the icon
 * only turns while the button holds focus. A permanently spinning element on a screen
 * someone is sitting back from gets noticed for the wrong reasons.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ShuffleActionButton(
    contentDescription: String,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    onFocused: () -> Unit = {}
) {
    var longPressTriggered by remember { mutableStateOf(false) }
    val longPressKeyTracker = rememberLongPressKeyTracker()
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val transition = rememberInfiniteTransition(label = "shuffleButton")

    // Drifts the gradient across the circle rather than sweeping it around, so there is
    // no seam where the rotation would wrap.
    val gradientShift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shuffleGradient"
    )

    val iconTurn by transition.animateFloat(
        initialValue = -12f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shuffleTurn"
    )

    // The hero row's other buttons grow on focus via the card scale; match that feel.
    val focusScale by animateFloatAsState(
        targetValue = if (isFocused) 1.06f else 1f,
        animationSpec = tween(durationMillis = 160),
        label = "shuffleScale"
    )

    val size = NuvioTheme.spacing.xxxl
    val span = with(androidx.compose.ui.platform.LocalDensity.current) { size.toPx() }
    val gradient = Brush.linearGradient(
        colors = listOf(
            NuvioTheme.colors.Primary,
            NuvioTheme.colors.Secondary,
            NuvioTheme.colors.Primary
        ),
        start = Offset(x = -span + gradientShift * span, y = 0f),
        end = Offset(x = span + gradientShift * span, y = span)
    )

    Box(
        modifier = Modifier
            .scale(focusScale)
            .size(size)
            .background(brush = gradient, shape = CircleShape),
        contentAlignment = Alignment.Center
    ) {
        // The gradient is the Box's background, so the button contributes only its icon,
        // focus ring and ripple - hence the transparent container colours.
        IconButton(
            onClick = {
                if (longPressTriggered) longPressTriggered = false else onClick()
            },
            interactionSource = interactionSource,
            modifier = Modifier
                .size(size)
                .onFocusChanged { state -> if (state.isFocused) onFocused() }
                // Long press picks a random episode but stops at the stream list, matching
                // how a long press on the Play button and on an episode card already work.
                .onPreviewKeyEvent { event ->
                    val native = event.nativeKeyEvent
                    if (onLongPress != null &&
                        longPressKeyTracker.handle(native, ::isShuffleSelectKey) {
                            longPressTriggered = true
                            onLongPress()
                        }
                    ) {
                        if (native.action == AndroidKeyEvent.ACTION_UP) longPressTriggered = false
                        return@onPreviewKeyEvent true
                    }
                    false
                }
                .focusProperties { up = FocusRequester.Cancel },
            colors = IconButtonDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                contentColor = Color.White,
                focusedContentColor = Color.White
            ),
            border = IconButtonDefaults.border(
                focusedBorder = Border(
                    border = BorderStroke(NuvioTheme.spacing.xxs, NuvioTheme.colors.FocusRing),
                    shape = CircleShape
                )
            ),
            shape = IconButtonDefaults.shape(shape = CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Shuffle,
                contentDescription = contentDescription,
                modifier = Modifier
                    .size(NuvioTheme.spacing.xl)
                    .rotate(if (isFocused) iconTurn else 0f)
            )
        }
    }
}
