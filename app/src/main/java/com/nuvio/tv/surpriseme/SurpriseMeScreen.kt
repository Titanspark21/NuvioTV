package com.nuvio.tv.surpriseme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.R
import com.nuvio.tv.ui.theme.NuvioTheme

/**
 * Fork addition. The "Surprise me" picker.
 *
 * Two full-height choices per step rather than a list: on a remote, a left/right pair is
 * one press and no reading. The first step is backed by real poster art pulled from the
 * owner's own catalogues, so the movie side is covered in films they could actually be
 * shown and the show side in series - nothing is bundled, and it cannot go stale.
 *
 * The result is deliberately NOT shown here. Rolling sends the owner to the title's own
 * detail page, where everything already works - episodes, cast, play - and a "Roll again"
 * button appears in the hero for as long as they are on a rolled title.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SurpriseMeScreen(
    onOpenTitle: (itemId: String, itemType: String) -> Unit,
    onBack: () -> Unit,
    viewModel: SurpriseMeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(state.step) {
        runCatching { firstFocus.requestFocus() }
    }

    LaunchedEffect(state.navigateTo) {
        state.navigateTo?.let { pick ->
            viewModel.onNavigationHandled()
            onOpenTitle(pick.id, pick.rawType)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NuvioTheme.colors.Background)
            .padding(NuvioTheme.spacing.xxl),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.lg)
        ) {
            Text(
                text = stringResource(
                    when (state.step) {
                        SurpriseStep.KIND -> R.string.surprise_me_step_kind
                        SurpriseStep.FILTER -> R.string.surprise_me_step_filter
                        SurpriseStep.ROLLING -> R.string.surprise_me_thinking
                    }
                ),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = NuvioTheme.colors.TextPrimary
            )

            state.message?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = NuvioTheme.extendedColors.textSecondary
                )
            }

            when (state.step) {
                SurpriseStep.KIND -> Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.lg)
                ) {
                    ChoiceTile(
                        title = stringResource(R.string.surprise_me_movies),
                        subtitle = stringResource(R.string.surprise_me_movies_hint),
                        posters = state.moviePosters,
                        tint = NuvioTheme.colors.Primary,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .focusRequester(firstFocus),
                        onClick = { viewModel.chooseKind(SurpriseKind.MOVIE) }
                    )
                    ChoiceTile(
                        title = stringResource(R.string.surprise_me_shows),
                        subtitle = stringResource(R.string.surprise_me_shows_hint),
                        posters = state.showPosters,
                        tint = NuvioTheme.colors.Secondary,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        onClick = { viewModel.chooseKind(SurpriseKind.SHOW) }
                    )
                }

                SurpriseStep.FILTER -> Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.lg)
                ) {
                    // No artwork here on purpose: this step is a rule, not a mood, and
                    // covering it in posters would say nothing true about either option.
                    ChoiceTile(
                        title = stringResource(R.string.surprise_me_only_new),
                        subtitle = stringResource(R.string.surprise_me_only_new_hint),
                        posters = emptyList(),
                        tint = NuvioTheme.colors.Primary,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .focusRequester(firstFocus),
                        onClick = { viewModel.chooseFilter(includeWatched = false) }
                    )
                    ChoiceTile(
                        title = stringResource(R.string.surprise_me_include_watched),
                        subtitle = stringResource(R.string.surprise_me_include_watched_hint),
                        posters = emptyList(),
                        tint = NuvioTheme.colors.Secondary,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        onClick = { viewModel.chooseFilter(includeWatched = true) }
                    )
                }

                SurpriseStep.ROLLING -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.surprise_me_thinking),
                        style = MaterialTheme.typography.titleLarge,
                        color = NuvioTheme.extendedColors.textSecondary
                    )
                }
            }
        }
    }
}

/**
 * One full-height choice. The poster wall behind it is a real grid of artwork under a
 * heavy scrim, so the label always stays legible no matter what art comes back.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ChoiceTile(
    title: String,
    subtitle: String,
    posters: List<String>,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.02f else 1f,
        animationSpec = tween(durationMillis = 180),
        label = "tileScale"
    )
    val shape = RoundedCornerShape(NuvioTheme.radii.xl)

    Card(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier.scale(scale),
        shape = CardDefaults.shape(shape = shape),
        colors = CardDefaults.colors(containerColor = NuvioTheme.colors.BackgroundCard),
        scale = CardDefaults.scale(focusedScale = 1f)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (posters.isNotEmpty()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(shape),
                    userScrollEnabled = false
                ) {
                    items(posters) { poster ->
                        AsyncImage(
                            model = poster,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(2f / 3f)
                                .padding(1.dp)
                        )
                    }
                }
            }

            // Scrim: darkens the art and leans it towards this tile's accent, which is
            // what keeps two walls of arbitrary posters looking like one designed screen.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.55f),
                                tint.copy(alpha = if (isFocused) 0.55f else 0.35f),
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(NuvioTheme.spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
