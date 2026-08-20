package com.nuvio.tv.ui.screens.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Dialogue Leveler — a real-time dynamic range compressor that sits in the ExoPlayer PCM
 * chain. It brings quiet speech up and tames loud music/effects so the perceived level stays
 * even without the viewer riding the volume. This is the classic "night mode" / DRC behaviour
 * found on AV receivers, implemented as a broadband feed-forward compressor with a soft knee,
 * attack/release smoothing, and make-up gain, followed by a hard safety limit.
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

    fun setLevel(newLevel: Int) {
        val clamped = newLevel.coerceIn(DIALOGUE_LEVELER_OFF, DIALOGUE_LEVELER_MAX)
        applyPreset(clamped)
        recomputeCoefficients()
        level = clamped
    }

    fun isLevelerEnabled(): Boolean = level != DIALOGUE_LEVELER_OFF

    private fun applyPreset(presetLevel: Int) {
        // threshold(dB), ratio, makeup(dB), knee(dB), release(ms). Attack is fixed (fast) below.
        val (threshold, ratio, makeup, knee, release) = when (presetLevel) {
            1 -> Preset(-20f, 2.0f, 3f, 8f, 250f)   // Light
            2 -> Preset(-24f, 2.5f, 6f, 8f, 200f)   // Medium
            3 -> Preset(-28f, 3.5f, 9f, 10f, 160f)  // Strong
            4 -> Preset(-32f, 5.0f, 12f, 12f, 120f) // Max
            else -> Preset(0f, 1f, 0f, 1f, 200f)    // Off (curve is inert)
        }
        thresholdDb = threshold
        ratioSlope = (1f / ratio) - 1f
        makeupDb = makeup
        kneeDb = knee.coerceAtLeast(0.01f)
        releaseMs = release
    }

    private data class Preset(
        val thresholdDb: Float,
        val ratio: Float,
        val makeupDb: Float,
        val kneeDb: Float,
        val releaseMs: Float
    )

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

        val channelCount = inputAudioFormat.channelCount.coerceAtLeast(1)
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

        val frame = FloatArray(channelCount)
        val frameBytes = channelCount * 2

        while (inputBuffer.remaining() >= frameBytes) {
            var peak = 0f
            for (c in 0 until channelCount) {
                val sample = inputBuffer.short.toInt() / 32768f
                frame[c] = sample
                val magnitude = abs(sample)
                if (magnitude > peak) peak = magnitude
            }
            val gain = computeFrameGain(peak)
            for (c in 0 until channelCount) {
                val amplified = (frame[c] * gain).coerceIn(-1f, 1f)
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

        val frame = FloatArray(channelCount)
        val frameBytes = channelCount * 4

        while (inputBuffer.remaining() >= frameBytes) {
            var peak = 0f
            for (c in 0 until channelCount) {
                val sample = inputBuffer.float
                frame[c] = sample
                val magnitude = abs(sample)
                if (magnitude > peak) peak = magnitude
            }
            val gain = computeFrameGain(peak)
            for (c in 0 until channelCount) {
                outputBuffer.putFloat((frame[c] * gain).coerceIn(-1f, 1f))
            }
        }

        if (inputBuffer.hasRemaining()) {
            outputBuffer.put(inputBuffer)
        }
    }

    /**
     * Feed-forward compressor gain for one (linked) frame: update the peak envelope with
     * attack/release smoothing, read the static soft-knee curve at that level, add make-up gain,
     * and return the linear multiplier. The result is clamped so a runaway preset can never
     * invert the signal.
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
        return 10.0.pow(totalDb / 20.0).toFloat()
    }

    private fun log10f(value: Float): Float = (ln(value.toDouble()) / LN_10).toFloat()

    private companion object {
        const val ATTACK_MS = 5f
        const val MIN_TOTAL_GAIN_DB = -24f
        const val MAX_TOTAL_GAIN_DB = 24f
        val LN_10 = ln(10.0)
    }
}
