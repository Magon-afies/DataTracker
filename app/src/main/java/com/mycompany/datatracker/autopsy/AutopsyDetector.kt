package com.mycompany.datatracker.autopsy

import com.mycompany.datatracker.usage.UsageRepository
import com.mycompany.datatracker.util.Dates

data class Snap(val total: Long, val bg: Long, val mobile: Long, val wifi: Long)

data class Spike(
    val uid: Int,
    val label: String,
    val packageName: String?,
    val bytes: Long,
    val totalBytes: Long,
    val bgBytes: Long,
    val mobileBytes: Long,
    val wifiBytes: Long,
    val from: Long,
    val to: Long,
    val screen: String
)

class AutopsyDetector(
    private val repo: UsageRepository,
    private val screen: ScreenLog,
    private val prefs: AutopsyPrefs,
    private val selfUid: Int
) {
    private val history = ArrayDeque<Pair<Long, Map<Int, Snap>>>()
    private val labels = HashMap<Int, Pair<String, String?>>()
    private val lastAlert = HashMap<Int, Long>()
    private var dayKey = ""

    fun tick(now: Long): List<Spike> {
        if (!repo.hasUsageAccess()) {
            history.clear()
            return emptyList()
        }
        val key = Dates.day(now)
        if (key != dayKey) {
            dayKey = key
            history.clear()
        }
        val usage = repo.query(repo.startOfDay(now), now)
        if (usage.isEmpty()) return emptyList()

        val current = HashMap<Int, Snap>()
        for (u in usage) {
            current[u.uid] = Snap(u.total, u.backgroundBytes, u.mobileTotal, u.wifiTotal)
            labels[u.uid] = u.label to u.packageName
        }

        val windowMs = prefs.windowMin * 60_000L
        var base = history.firstOrNull()
        val idx = history.indexOfLast { it.first <= now - windowMs }
        if (idx >= 0) {
            repeat(idx) { history.removeFirst() }
            base = history.first()
        }
        history.addLast(now to current)

        if (base == null) return emptyList()
        val span = now - base.first
        if (span < 30_000L) return emptyList()

        val offMs = screen.offMillis(base.first, now)
        val offFrac = offMs.toDouble() / span
        val threshold = prefs.spikeMb * 1024L * 1024L
        val cooldown = prefs.cooldownMin * 60_000L
        val state = screenLabel(offMs, span, offFrac)
        val out = ArrayList<Spike>()

        for ((uid, cur) in current) {
            if (uid == selfUid) continue
            val b = base.second[uid] ?: Snap(0, 0, 0, 0)
            val dTotal = cur.total - b.total
            val dBg = cur.bg - b.bg
            if (dTotal < 0 || dBg < 0) continue
            val used = if (offFrac >= 0.5) dTotal else dBg
            if (used < threshold) continue
            val last = lastAlert[uid]
            if (last != null && now - last < cooldown) continue
            val meta = labels[uid] ?: ("UID $uid" to null)
            lastAlert[uid] = now
            out.add(
                Spike(
                    uid = uid,
                    label = meta.first,
                    packageName = meta.second,
                    bytes = used,
                    totalBytes = dTotal,
                    bgBytes = dBg,
                    mobileBytes = (cur.mobile - b.mobile).coerceAtLeast(0L),
                    wifiBytes = (cur.wifi - b.wifi).coerceAtLeast(0L),
                    from = base.first,
                    to = now,
                    screen = state
                )
            )
        }
        return out.sortedByDescending { it.bytes }
    }

    private fun screenLabel(offMs: Long, spanMs: Long, frac: Double): String = when {
        frac >= 0.9 -> "Screen off"
        frac <= 0.1 -> "Screen on"
        else -> "Screen off ${minutes(offMs)} of ${minutes(spanMs)} min"
    }

    private fun minutes(ms: Long): Long = ((ms + 30_000L) / 60_000L).coerceAtLeast(1L)
}
