package com.nuvio.tv.ui.screens.detail

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.ui.theme.NuvioTheme

/**
 * Fork addition. "Roll again", shown beside Play on a title the owner reached through
 * Surprise me.
 *
 * The die tumbles only while a roll is actually in flight, so the animation reports
 * something real - it is the progress indicator as well as the decoration, which is why
 * there is no separate spinner.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun RollAgainButton(
    text: String,
    isRolling: Boolean,
    onClick: () -> Unit,
    onFocused: () -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val transition = rememberInfiniteTransition(label = "rollAgain")
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing)
        ),
        label = "dieSpin"
    )

    val shape = RoundedCornerShape(50)
    val gradient = Brush.horizontalGradient(
        colors = listOf(NuvioTheme.colors.Secondary, NuvioTheme.colors.Primary)
    )

    Box(
        modifier = Modifier
            .height(NuvioTheme.spacing.xxxl)
            .clip(shape)
            .background(brush = gradient, shape = shape)
    ) {
        Button(
            onClick = onClick,
            enabled = !isRolling,
            interactionSource = interactionSource,
            modifier = Modifier
                .onFocusChanged { state -> if (state.isFocused) onFocused() }
                .focusProperties { up = FocusRequester.Cancel },
            // Transparent so the gradient behind shows through; the button still supplies
            // the focus ring, ripple and the row's shared button metrics.
            colors = ButtonDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                contentColor = Color.White,
                focusedContentColor = Color.White
            ),
            border = ButtonDefaults.border(
                focusedBorder = Border(
                    border = BorderStroke(NuvioTheme.spacing.xxs, NuvioTheme.colors.FocusRing),
                    shape = shape
                )
            ),
            shape = ButtonDefaults.shape(shape = shape)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm)
            ) {
                Icon(
                    imageVector = Icons.Default.Casino,
                    contentDescription = null,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(if (isRolling) spin else if (isFocused) 12f else 0f)
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(end = 2.dp)
                )
            }
        }
    }
}
