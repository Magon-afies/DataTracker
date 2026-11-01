package com.mycompany.datatracker.clock

import android.content.Context
import com.mycompany.datatracker.data.Db
import com.mycompany.datatracker.data.PlanSnapshot
import com.mycompany.datatracker.data.PlanStore
import com.mycompany.datatracker.usage.UsageRepository

data class DeathClockState(
    val kind: Kind,
    val deathMs: Long = 0L,
    val remainingBytes: Long = 0L,
    val bytesPerHour: Double = 0.0,
    val expiryMs: Long = 0L,
    val outlastsPlan: Boolean = false
) {
    enum class Kind { NO_PLAN, DEAD, IDLE, COUNTDOWN }
}

object DeathClock {
    private const val HOUR = 3_600_000L
    private const val MAX_ETA_MS = 3.15e11

    fun compute(context: Context, now: Long): DeathClockState {
        val app = context.applicationContext
        val store = PlanStore(app)
        val plan = store.active(now) ?: return DeathClockState(DeathClockState.Kind.NO_PLAN)

        val used = UsageRepository(app).mobileUsage(plan.startMs, minOf(now, plan.expiryMs))
            ?: store.latestSnapshot(plan.id)?.usageBytes
            ?: 0L

        val remaining = plan.sizeBytes - used
        if (remaining <= 0L) {
            return DeathClockState(
                DeathClockState.Kind.DEAD, deathMs = now, expiryMs = plan.expiryMs
            )
        }

        val snaps = snapshots(app, plan.id, now - 24 * HOUR)
        val r6 = windowRate(snaps, now, used, 6 * HOUR, HOUR / 2)
        val r24 = windowRate(snaps, now, used, 24 * HOUR, HOUR)
        var rate = when {
            r6 != null && r24 != null -> 0.6 * r6 + 0.4 * r24
            r6 != null -> r6
            r24 != null -> r24
            else -> 0.0
        }
        if (rate <= 0.0) {
            rate = used.toDouble() / (now - plan.startMs).coerceAtLeast(HOUR).toDouble()
        }
        if (rate <= 0.0) {
            return DeathClockState(
                DeathClockState.Kind.IDLE, remainingBytes = remaining, expiryMs = plan.expiryMs
            )
        }

        val eta = (remaining / rate).coerceAtMost(MAX_ETA_MS).toLong()
        val deathMs = now + eta
        return DeathClockState(
            kind = DeathClockState.Kind.COUNTDOWN,
            deathMs = deathMs,
            remainingBytes = remaining,
            bytesPerHour = rate * HOUR,
            expiryMs = plan.expiryMs,
            outlastsPlan = deathMs > plan.expiryMs
        )
    }

    private fun windowRate(
        snaps: List<PlanSnapshot>,
        now: Long,
        used: Long,
        windowMs: Long,
        minSpanMs: Long
    ): Double? {
        val first = snaps.firstOrNull { it.capturedMs >= now - windowMs } ?: return null
        val span = now - first.capturedMs
        if (span < minSpanMs) return null
        return (used - first.usageBytes).coerceAtLeast(0L).toDouble() / span.toDouble()
    }

    private fun snapshots(context: Context, planId: Long, since: Long): List<PlanSnapshot> {
        val out = ArrayList<PlanSnapshot>()
        Db.get(context).readableDatabase.query(
            "plan_snapshots",
            arrayOf("usage_bytes", "captured_ms", "traffic_counter"),
            "plan_id = ? AND captured_ms >= ?",
            arrayOf(planId.toString(), since.toString()),
            null, null, "captured_ms ASC"
        ).use { c ->
            while (c.moveToNext()) {
                out.add(PlanSnapshot(c.getLong(0), c.getLong(1), c.getLong(2)))
            }
        }
        return out
    }
}
