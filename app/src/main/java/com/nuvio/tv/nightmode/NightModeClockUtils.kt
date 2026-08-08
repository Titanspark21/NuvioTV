package com.nuvio.tv.nightmode

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

object NightModeClockUtils {
    /**
     * Given the wall-clock instant [enabledAtMillis] when night mode was enabled,
     * calculates the epoch millis of the next 06:00 local time strictly after that instant.
     */
    fun calculateExpirationMillis(enabledAtMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): Long {
        if (enabledAtMillis <= 0L) return 0L
        val enabledInstant = Instant.ofEpochMilli(enabledAtMillis)
        val enabledZdt = ZonedDateTime.ofInstant(enabledInstant, zoneId)

        var target6Am = enabledZdt.toLocalDate().atTime(6, 0).atZone(zoneId)
        if (!target6Am.isAfter(enabledZdt)) {
            target6Am = target6Am.plusDays(1)
        }
        return target6Am.toInstant().toEpochMilli()
    }

    /**
     * Checks if night mode enabled at [enabledAtMillis] has passed its auto-off 6:00 AM threshold.
     */
    fun isExpired(
        enabledAtMillis: Long,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Boolean {
        if (enabledAtMillis <= 0L) return false
        val expireMillis = calculateExpirationMillis(enabledAtMillis, zoneId)
        return nowMillis >= expireMillis
    }
}
