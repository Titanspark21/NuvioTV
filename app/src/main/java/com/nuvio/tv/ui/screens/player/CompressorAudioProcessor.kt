package com.nuvio.tv.ui.screens.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

internal data class DialogueLevelerPreset(
    val thresholdDb: Float,
    val ratio: Float,
    val makeupDb: Float,
    val kneeDb: Float,
    val releaseMs: Float
)

internal fun dialogueLevelerPreset(level: Int): DialogueLevelerPreset = when (
    level.coerceIn(DIALOGUE_LEVELER_OFF, DIALOGUE_LEVELER_MAX)
) {
    1 -> DialogueLevelerPreset(-20f, 2.0f, 3f, 8f, 250f)   // Light
    2 -> DialogueLevelerPreset(-24f, 2.5f, 6f, 8f, 200f)   // Medium
    3 -> DialogueLevelerPreset(-28f, 3.5f, 9f, 10f, 160f)  // Strong
    4 -> DialogueLevelerPreset(-32f, 5.0f, 12f, 12f, 120f) // Max
    else -> DialogueLevelerPreset(0f, 1f, 0f, 1f, 200f)    // Off
}

/**
 * Dialogue Leveler — a real-time dynamic range compressor that sits in the ExoPlayer PCM
 * chain. It brings quiet speech up and tames loud music/effects so the perceived level stays
 * even without the viewer riding the volume. This is the classic "night mode" / DRC behaviour
 * found on AV receivers, implemented as a broadband feed-forward compressor with a soft knee,
 * attack/release smoothing, and make-up gain, followed by a peak safety limiter.
 *
 * The detector is *linked* across channels (one gain applied to the whole frame) so the stereo
 * image never shifts as the gain moves. It runs on both 16-bit and float PCM, matching the
 * encodings [GainAudioProcessor] already handles, and is inert (a straight pass-through, and
 * [isActive] false so the sink can drop it) whenever the level is Off.
 *
 * Levels map to progressively stronger presets; the mapping is deliberately the only knob the
 * player exposes, so a value chosen with a TV remote always lands on a sane, tested curve.
 */
internal class CompressorAudioProcessor : BaseAudioProcessor() {

    @Volatile
    private var level: Int = DIALOGUE_LEVELER_OFF

    // Static-curve parameters for the active preset. Individually @Volatile so the audio thread
    // picks up a level change promptly; a one-frame mix of old and new params during a user
    // adjustment is inaudible.
    @Volatile
    private var thresholdDb: Float = 0f
    @Volatile
    private var ratioSlope: Float = 0f // (1/ratio - 1); <= 0
    @Volatile
    private var makeupDb: Float = 0f
    @Volatile
    private var kneeDb: Float = 1f
    @Volatile
    private var releaseMs: Float = 200f

    // Per-configure derived state (written from onFlush() and setLevel(), read on the audio thread).
    @Volatile
    private var attackCoeff: Float = 0f
    @Volatile
    private var releaseCoeff: Float = 0f
    private var envelope: Float = 0f
    private var frameSamples = FloatArray(0)
    @Volatile
    private var resetEnvelopeRequested: Boolean = false

    fun setLevel(newLevel: Int) {
        val clamped = newLevel.coerceIn(DIALOGUE_LEVELER_OFF, DIALOGUE_LEVELER_MAX)
        if (clamped == level) return
        applyPreset(clamped)
        recomputeCoefficients()
        level = clamped
        // The audio thread owns the detector. Ask it to reset at the next buffer boundary so
        // enabling the leveler cannot inherit a stale envelope from an earlier preset/session.
        resetEnvelopeRequested = true
    }

    fun isLevelerEnabled(): Boolean = level != DIALOGUE_LEVELER_OFF

    private fun applyPreset(presetLevel: Int) {
        val preset = dialogueLevelerPreset(presetLevel)
        thresholdDb = preset.thresholdDb
        ratioSlope = (1f / preset.ratio) - 1f
        makeupDb = preset.makeupDb
        kneeDb = preset.kneeDb.coerceAtLeast(0.01f)
        releaseMs = preset.releaseMs
    }

    override fun isActive(): Boolean = super.isActive() && isLevelerEnabled()

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        // Coefficients are (re)computed in onFlush(), which the sink always calls after configure
        // and before feeding audio — that is where BaseAudioProcessor publishes inputAudioFormat.
        return when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT,
            C.ENCODING_PCM_FLOAT -> inputAudioFormat
            else -> AudioProcessor.AudioFormat.NOT_SET
        }
    }

    override fun onFlush() {
        // Now that inputAudioFormat is published, size the attack/release for the real sample
        // rate, and drop the detector state so a seek does not carry an old envelope into new audio.
        recomputeCoefficients()
        envelope = 0f
        resetEnvelopeRequested = false
    }

    private fun recomputeCoefficients() {
        val sampleRate = inputAudioFormat.sampleRate
        if (sampleRate <= 0) {
            attackCoeff = 0f
            releaseCoeff = 0f
            return
        }
        attackCoeff = timeConstantToCoeff(ATTACK_MS, sampleRate)
        releaseCoeff = timeConstantToCoeff(releaseMs, sampleRate)
    }

    private fun timeConstantToCoeff(timeMs: Float, sampleRate: Int): Float {
        if (timeMs <= 0f) return 0f
        return exp(-1.0 / ((timeMs / 1000.0) * sampleRate)).toFloat()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return

        if (resetEnvelopeRequested) {
            envelope = 0f
            resetEnvelopeRequested = false
        }

        val channelCount = inputAudioFormat.channelCount.coerceAtLeast(1)
        if (frameSamples.size < channelCount) {
            frameSamples = FloatArray(channelCount)
        }
        val outputBuffer = replaceOutputBuffer(inputBuffer.remaining())

        when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT -> processPcm16(inputBuffer, outputBuffer, channelCount)
            C.ENCODING_PCM_FLOAT -> processPcmFloat(inputBuffer, outputBuffer, channelCount)
            else -> outputBuffer.put(inputBuffer)
        }

        outputBuffer.flip()
    }

    private fun processPcm16(inputBuffer: ByteBuffer, outputBuffer: ByteBuffer, channelCount: Int) {
        inputBuffer.order(ByteOrder.nativeOrder())
        outputBuffer.order(ByteOrder.nativeOrder())

        val frameBytes = channelCount * 2

        while (inputBuffer.remaining() >= frameBytes) {
            var peak = 0f
            for (c in 0 until channelCount) {
                val sample = inputBuffer.short.toInt() / 32768f
                frameSamples[c] = sample
                val magnitude = abs(sample)
                if (magnitude > peak) peak = magnitude
            }
            val gain = computeFrameGain(peak)
            for (c in 0 until channelCount) {
                val amplified = frameSamples[c] * gain
                outputBuffer.putShort(
                    (amplified * 32767f).roundToInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        .toShort()
                )
            }
        }

        if (inputBuffer.hasRemaining()) {
            outputBuffer.put(inputBuffer)
        }
    }

    private fun processPcmFloat(inputBuffer: ByteBuffer, outputBuffer: ByteBuffer, channelCount: Int) {
        inputBuffer.order(ByteOrder.nativeOrder())
        outputBuffer.order(ByteOrder.nativeOrder())

        val frameBytes = channelCount * 4

        while (inputBuffer.remaining() >= frameBytes) {
            var peak = 0f
            for (c in 0 until channelCount) {
                val sample = inputBuffer.float
                frameSamples[c] = sample
                val magnitude = abs(sample)
                if (magnitude > peak) peak = magnitude
            }
            val gain = computeFrameGain(peak)
            for (c in 0 until channelCount) {
                outputBuffer.putFloat(frameSamples[c] * gain)
            }
        }

        if (inputBuffer.hasRemaining()) {
            outputBuffer.put(inputBuffer)
        }
    }

    /**
     * Feed-forward compressor gain for one (linked) frame: update the peak envelope with
     * attack/release smoothing, read the static soft-knee curve at that level, add make-up gain,
     * and return the linear multiplier. A zero-lookahead peak ceiling is applied after the
     * smoothed compressor curve. Without it, make-up gain can hard-clip the leading edge of a
     * loud effect during the attack window before the detector has caught up.
     */
    private fun computeFrameGain(peak: Float): Float {
        envelope = if (peak > envelope) {
            attackCoeff * envelope + (1f - attackCoeff) * peak
        } else {
            releaseCoeff * envelope + (1f - releaseCoeff) * peak
        }

        val envDb = 20f * log10f(envelope.coerceAtLeast(1e-6f))
        val over = envDb - thresholdDb
        val reductionDb = when {
            2f * over < -kneeDb -> 0f
            2f * over > kneeDb -> ratioSlope * over
            else -> {
                val kneed = over + kneeDb / 2f
                ratioSlope * (kneed * kneed) / (2f * kneeDb)
            }
        }
        val totalDb = (reductionDb + makeupDb).coerceIn(MIN_TOTAL_GAIN_DB, MAX_TOTAL_GAIN_DB)
        val compressorGain = 10.0.pow(totalDb / 20.0).toFloat()
        val peakSafeGain = if (peak > 0f) MAX_OUTPUT_PEAK / peak else compressorGain
        return min(compressorGain, peakSafeGain)
    }

    private fun log10f(value: Float): Float = (ln(value.toDouble()) / LN_10).toFloat()

    private companion object {
        const val ATTACK_MS = 5f
        const val MAX_OUTPUT_PEAK = 0.98f
        const val MIN_TOTAL_GAIN_DB = -24f
        const val MAX_TOTAL_GAIN_DB = 24f
        val LN_10 = ln(10.0)
    }
}
