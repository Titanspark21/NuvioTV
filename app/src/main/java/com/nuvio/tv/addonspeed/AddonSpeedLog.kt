package com.nuvio.tv.addonspeed

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** What kind of request was timed. */
enum class AddonCallKind { META, STREAM }

/** One timed addon request. */
data class AddonSpeedSample(
    val timestampMs: Long,
    val addonName: String,
    val kind: AddonCallKind,
    val durationMs: Long,
    val success: Boolean
)

/** Everything worth saying about one addon over the retained window. */
data class AddonSpeedStats(
    val addonName: String,
    val kind: AddonCallKind,
    val samples: Int,
    val failures: Int,
    val medianMs: Long,
    val p95Ms: Long,
    val fastestMs: Long,
    val slowestMs: Long
) {
    val failureRate: Float get() = if (samples == 0) 0f else failures.toFloat() / samples
}

/** One day's worth, for the history view. */
data class AddonSpeedDay(
    val dayEpoch: Long,
    val samples: Int,
    val medianMs: Long
)

/**
 * Fork addition. Records how long each addon takes to answer, and keeps 30 days of it.
 *
 * Stored as one line of plain text per request in the app's own files directory rather
 * than in a database: a sample is five small fields, appending is the only write, and the
 * whole point is that recording must never slow down the request it is measuring. A
 * month of heavy use is a few hundred kilobytes.
 *
 * Writes are fire-and-forget on the IO dispatcher. A dropped sample costs nothing; a
 * blocked playback request would cost everything.
 */
@Singleton
class AddonSpeedLog @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    private val file: File by lazy { File(context.filesDir, FILE_NAME) }

    /** Records a completed request. Safe to call from anywhere; never throws, never blocks. */
    fun record(addonName: String, kind: AddonCallKind, durationMs: Long, success: Boolean) {
        if (addonName.isBlank()) return
        scope.launch {
            runCatching {
                lock.withLock {
                    // The separator cannot appear in the fields: the name is sanitised and
                    // everything else is a number or a boolean.
                    val line = buildString {
                        append(System.currentTimeMillis()).append(SEP)
                        append(addonName.replace(SEP, ' ').trim()).append(SEP)
                        append(kind.name).append(SEP)
                        append(durationMs).append(SEP)
                        append(success)
                        append('\n')
                    }
                    file.appendText(line)
                    maybePrune()
                }
            }.onFailure { Log.w(TAG, "speed sample dropped: ${it.message}") }
        }
    }

    suspend fun readSamples(): List<AddonSpeedSample> = withContext(Dispatchers.IO) {
        lock.withLock { readSamplesLocked() }
    }

    /** Per-addon summary over the retained window, worst median first. */
    suspend fun stats(kind: AddonCallKind): List<AddonSpeedStats> {
        val cutoff = System.currentTimeMillis() - RETENTION_MS
        return readSamples()
            .filter { it.kind == kind && it.timestampMs >= cutoff }
            .groupBy { it.addonName }
            .map { (name, rows) -> summarise(name, kind, rows) }
            .sortedByDescending { it.medianMs }
    }

    /** Daily medians for one addon, oldest first - the 30 day history. */
    suspend fun history(addonName: String, kind: AddonCallKind): List<AddonSpeedDay> {
        val cutoff = System.currentTimeMillis() - RETENTION_MS
        return readSamples()
            .filter { it.kind == kind && it.addonName == addonName && it.timestampMs >= cutoff }
            .groupBy { it.timestampMs / DAY_MS }
            .map { (day, rows) ->
                AddonSpeedDay(
                    dayEpoch = day,
                    samples = rows.size,
                    medianMs = median(rows.map { it.durationMs })
                )
            }
            .sortedBy { it.dayEpoch }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        lock.withLock { runCatching { file.delete() } }
        Unit
    }

    private fun summarise(
        name: String,
        kind: AddonCallKind,
        rows: List<AddonSpeedSample>
    ): AddonSpeedStats {
        // Failures are counted but excluded from the timings: a request that errored out
        // in 200 ms is not evidence that an addon is fast.
        val ok = rows.filter { it.success }.map { it.durationMs }.sorted()
        return AddonSpeedStats(
            addonName = name,
            kind = kind,
            samples = rows.size,
            failures = rows.count { !it.success },
            medianMs = median(ok),
            p95Ms = percentile(ok, 0.95f),
            fastestMs = ok.firstOrNull() ?: 0L,
            slowestMs = ok.lastOrNull() ?: 0L
        )
    }

    private fun readSamplesLocked(): List<AddonSpeedSample> {
        if (!file.exists()) return emptyList()
        return file.useLines { lines ->
            lines.mapNotNull { parse(it) }.toList()
        }
    }

    private fun parse(line: String): AddonSpeedSample? {
        val parts = line.split(SEP)
        if (parts.size != 5) return null
        val timestamp = parts[0].toLongOrNull() ?: return null
        val kind = runCatching { AddonCallKind.valueOf(parts[2]) }.getOrNull() ?: return null
        val duration = parts[3].toLongOrNull() ?: return null
        return AddonSpeedSample(
            timestampMs = timestamp,
            addonName = parts[1],
            kind = kind,
            durationMs = duration,
            success = parts[4].toBooleanStrictOrNull() ?: return null
        )
    }

    /**
     * Rewrites the file without anything older than the window. Only runs once the file
     * is big enough to be worth it, so the common case is a bare append.
     */
    private fun maybePrune() {
        if (!file.exists() || file.length() < PRUNE_AT_BYTES) return
        val cutoff = System.currentTimeMillis() - RETENTION_MS
        val kept = readSamplesLocked().filter { it.timestampMs >= cutoff }
        val rebuilt = buildString {
            kept.forEach { s ->
                append(s.timestampMs).append(SEP)
                append(s.addonName).append(SEP)
                append(s.kind.name).append(SEP)
                append(s.durationMs).append(SEP)
                append(s.success).append('\n')
            }
        }
        file.writeText(rebuilt)
    }

    private companion object {
        const val TAG = "AddonSpeedLog"
        const val FILE_NAME = "addon_speed_log.tsv"
        const val SEP = '\t'
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val RETENTION_MS = 30 * DAY_MS
        const val PRUNE_AT_BYTES = 256L * 1024

        fun median(sorted: List<Long>): Long = percentile(sorted, 0.5f)

        fun percentile(sorted: List<Long>, fraction: Float): Long {
            if (sorted.isEmpty()) return 0L
            val ordered = if (sorted.zipWithNext().all { (a, b) -> a <= b }) sorted else sorted.sorted()
            val index = ((ordered.size - 1) * fraction).toInt().coerceIn(0, ordered.size - 1)
            return ordered[index]
        }
    }
}
