package com.nuvio.tv.ui.screens.player

import java.util.Locale
import kotlin.math.pow

internal const val MPV_DIALOGUE_LEVELER_FILTER_LABEL = "nuvio_dialogue_leveler"

/** Builds the MPV/libavfilter equivalent of [CompressorAudioProcessor]'s preset. */
internal fun buildMpvDialogueLevelerFilter(level: Int): String? {
    val clampedLevel = level.coerceIn(DIALOGUE_LEVELER_OFF, DIALOGUE_LEVELER_MAX)
    if (clampedLevel == DIALOGUE_LEVELER_OFF) return null

    val preset = dialogueLevelerPreset(clampedLevel)
    val threshold = dbToLinear(preset.thresholdDb)
    val makeup = dbToLinear(preset.makeupDb)
    // FFmpeg expresses knee width as a linear factor (1..8), while the shared presets use dB.
    val knee = dbToLinear(preset.kneeDb).coerceIn(1.0, 8.0)

    return buildString {
        append('@').append(MPV_DIALOGUE_LEVELER_FILTER_LABEL).append(":lavfi=[")
        append("acompressor=")
        append("threshold=").append(formatFilterNumber(threshold))
        append(":ratio=").append(formatFilterNumber(preset.ratio.toDouble()))
        append(":attack=5")
        append(":release=").append(formatFilterNumber(preset.releaseMs.toDouble()))
        append(":makeup=").append(formatFilterNumber(makeup))
        append(":knee=").append(formatFilterNumber(knee))
        append(":link=maximum:detection=peak,")
        // alimiter uses lookahead on MPV. Disable its auto-level option so it does not undo the
        // 0.98 ceiling by normalizing the result back to full scale.
        append("alimiter=limit=0.98:attack=5:release=50:level=0:latency=1")
        append(']')
    }
}

private fun dbToLinear(db: Float): Double = 10.0.pow(db / 20.0)

private fun formatFilterNumber(value: Double): String =
    String.format(Locale.US, "%.6f", value)
