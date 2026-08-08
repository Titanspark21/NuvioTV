package com.nuvio.tv.surpriseme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.ui.components.NuvioDialog
import com.nuvio.tv.ui.theme.NuvioTheme

/**
 * Fork addition. "Surprise me" - pick something to watch from the recommendation addons.
 *
 * A dialog rather than a screen on purpose: the whole point is a fast re-roll, and a
 * dialog keeps the owner where they were instead of pushing and popping a route each time.
 * It is built from NuvioDialog and the app's own Button so it inherits the existing
 * styling, focus behaviour and sizing rather than approximating them.
 */
@Composable
fun SurpriseMeDialog(
    onDismiss: () -> Unit,
    onOpenTitle: (MetaPreview) -> Unit,
    viewModel: SurpriseMeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val primaryFocusRequester = remember { FocusRequester() }

    val subtitle = when {
        !state.hasAnySource -> stringResource(R.string.surprise_me_no_sources)
        state.error != null -> state.error.orEmpty()
        state.isLoading -> stringResource(R.string.surprise_me_thinking)
        state.pick != null -> state.pickSourceName?.let {
            stringResource(R.string.surprise_me_from_source, it)
        }.orEmpty()
        else -> stringResource(R.string.surprise_me_subtitle)
    }

    NuvioDialog(
        onDismiss = onDismiss,
        title = state.pick?.name ?: stringResource(R.string.surprise_me_title),
        subtitle = subtitle.takeIf { it.isNotBlank() }
    ) {
        // What to be surprised by.
        ChoiceRow(
            leftLabel = stringResource(R.string.surprise_me_shows),
            rightLabel = stringResource(R.string.surprise_me_movies),
            leftSelected = state.kind == SurpriseKind.SHOW,
            onLeft = { viewModel.setKind(SurpriseKind.SHOW) },
            onRight = { viewModel.setKind(SurpriseKind.MOVIE) }
        )

        // Whether things already in the library are fair game.
        ChoiceRow(
            leftLabel = stringResource(R.string.surprise_me_only_new),
            rightLabel = stringResource(R.string.surprise_me_include_watched),
            leftSelected = !state.includeWatched,
            onLeft = { viewModel.setIncludeWatched(false) },
            onRight = { viewModel.setIncludeWatched(true) }
        )

        state.pick?.let { pick ->
            pick.description?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = NuvioTheme.extendedColors.textSecondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Button(
                onClick = { onOpenTitle(pick) },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(primaryFocusRequester),
                colors = ButtonDefaults.colors(
                    containerColor = NuvioTheme.colors.Primary,
                    contentColor = NuvioTheme.colors.OnPrimary
                )
            ) {
                Text(stringResource(R.string.surprise_me_open))
            }
        }

        Button(
            onClick = viewModel::roll,
            enabled = !state.isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (state.pick == null) Modifier.focusRequester(primaryFocusRequester) else Modifier
                ),
            colors = ButtonDefaults.colors(
                containerColor = NuvioTheme.colors.BackgroundCard,
                contentColor = NuvioTheme.colors.TextPrimary
            )
        ) {
            Text(
                stringResource(
                    if (state.pick == null) R.string.surprise_me_roll else R.string.surprise_me_another
                )
            )
        }
    }

    // Land focus on the action, so the first press of OK does the obvious thing.
    LaunchedEffect(state.pick == null) {
        runCatching { primaryFocusRequester.requestFocus() }
    }
}

/**
 * Two side-by-side options, the selected one filled. Left and right on the remote move
 * between them because they are simply neighbouring focusable buttons - no custom key
 * handling, which is what keeps it behaving like the rest of the app.
 */
@Composable
private fun ChoiceRow(
    leftLabel: String,
    rightLabel: String,
    leftSelected: Boolean,
    onLeft: () -> Unit,
    onRight: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm)
    ) {
        ChoiceButton(
            label = leftLabel,
            selected = leftSelected,
            onClick = onLeft,
            modifier = Modifier.weight(1f)
        )
        ChoiceButton(
            label = rightLabel,
            selected = !leftSelected,
            onClick = onRight,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ChoiceButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.colors(
            containerColor = if (selected) NuvioTheme.colors.Primary else NuvioTheme.colors.BackgroundCard,
            contentColor = if (selected) NuvioTheme.colors.OnPrimary else NuvioTheme.colors.TextPrimary
        )
    ) {
        Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
