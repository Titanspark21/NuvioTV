package com.nuvio.tv.addonspeed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.ui.theme.NuvioTheme

/**
 * Fork addition. How fast each addon answers - both what has been observed in normal use
 * over the last 30 days, and a test you can run on demand.
 *
 * The two answer different questions. The history says which addon is slow for the things
 * you actually watch; the test says which is slow right now, on identical input, including
 * addons that normal use never reaches because a higher-priority one always answers first.
 */
@Composable
fun AddonSpeedScreen(
    viewModel: AddonSpeedViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(NuvioTheme.spacing.xxl)
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md)
        ) {
            item(key = "title") {
                Text(
                    text = "Addon speed",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = NuvioTheme.colors.TextPrimary
                )
            }

            item(key = "actions") {
                Row(horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm)) {
                    Button(
                        onClick = { if (state.isTesting) viewModel.cancelTest() else viewModel.runTest() },
                        colors = ButtonDefaults.colors(
                            containerColor = NuvioTheme.colors.Primary,
                            contentColor = NuvioTheme.colors.OnPrimary
                        )
                    ) {
                        Text(if (state.isTesting) "Stop test" else "Run speed test")
                    }
                    Button(
                        onClick = viewModel::clearHistory,
                        colors = ButtonDefaults.colors(
                            containerColor = NuvioTheme.colors.BackgroundCard,
                            contentColor = NuvioTheme.colors.TextPrimary
                        )
                    ) {
                        Text("Clear history")
                    }
                }
            }

            state.testProgress?.let { progress ->
                item(key = "progress") {
                    Text(
                        text = "Testing: $progress",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NuvioTheme.extendedColors.textSecondary
                    )
                }
            }

            if (state.testRows.isNotEmpty()) {
                item(key = "test_header") { SectionHeader("Test results") }
                items(state.testRows, key = { "${it.addonName}|${it.item.id}|${it.kind}" }) { row ->
                    TestRow(row)
                }
            }

            item(key = "meta_header") { SectionHeader("Metadata addons - last 30 days") }
            if (state.metaStats.isEmpty()) {
                item(key = "meta_empty") { EmptyNote() }
            } else {
                items(state.metaStats, key = { "meta_${it.addonName}" }) { StatRow(it) }
            }

            item(key = "stream_header") { SectionHeader("Stream addons - last 30 days") }
            if (state.streamStats.isEmpty()) {
                item(key = "stream_empty") { EmptyNote() }
            } else {
                items(state.streamStats, key = { "stream_${it.addonName}" }) { StatRow(it) }
            }

            item(key = "note") {
                Text(
                    text = "Times exclude failed requests, so an addon that errors quickly " +
                        "is not counted as fast. Median is the typical wait; 95th percentile " +
                        "is the bad case you notice.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NuvioTheme.extendedColors.textSecondary,
                    modifier = Modifier.padding(top = NuvioTheme.spacing.md)
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = NuvioTheme.colors.TextPrimary,
        modifier = Modifier.padding(top = NuvioTheme.spacing.md)
    )
}

@Composable
private fun EmptyNote() {
    Text(
        text = "Nothing recorded yet. Watch something, or run the speed test.",
        style = MaterialTheme.typography.bodyMedium,
        color = NuvioTheme.extendedColors.textSecondary
    )
}

@Composable
private fun StatRow(stats: AddonSpeedStats) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = stats.addonName,
            style = MaterialTheme.typography.titleSmall,
            color = NuvioTheme.colors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Metric("typical", formatMs(stats.medianMs))
            Metric("worst 5%", formatMs(stats.p95Ms))
            Metric("requests", stats.samples.toString())
            if (stats.failures > 0) {
                Metric("failed", "${stats.failures}")
            }
        }
    }
}

@Composable
private fun TestRow(row: SpeedTestRow) {
    val outcome = when (row.outcome) {
        SpeedTestRow.Outcome.ANSWERED -> formatMs(row.durationMs)
        SpeedTestRow.Outcome.EMPTY -> "no results (${formatMs(row.durationMs)})"
        SpeedTestRow.Outcome.FAILED -> "failed"
        SpeedTestRow.Outcome.TIMED_OUT -> "timed out"
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "${row.addonName} - ${row.item.label}",
            style = MaterialTheme.typography.bodyMedium,
            color = NuvioTheme.colors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(0.7f)
        )
        Text(
            text = outcome,
            style = MaterialTheme.typography.bodyMedium,
            color = NuvioTheme.extendedColors.textSecondary
        )
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = NuvioTheme.colors.TextPrimary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = NuvioTheme.extendedColors.textSecondary
        )
    }
}

private fun formatMs(ms: Long): String =
    if (ms >= 1000) String.format("%.1fs", ms / 1000f) else "${ms}ms"
