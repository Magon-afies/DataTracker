package com.mycompany.datatracker.autopsy

class ScreenLog(initialOn: Boolean, now: Long) {
    private val events = ArrayDeque<Pair<Long, Boolean>>()

    init {
        events.addLast(now to initialOn)
    }

    @Synchronized
    fun set(on: Boolean, at: Long) {
        if (events.last().second == on) return
        events.addLast(at to on)
        while (events.size > 500) events.removeFirst()
    }

    @Synchronized
    fun offMillis(from: Long, to: Long): Long {
        val list = events.toList()
        var total = 0L
        for (i in list.indices) {
            if (list[i].second) continue
            val start = maxOf(list[i].first, from)
            val end = minOf(if (i + 1 < list.size) list[i + 1].first else Long.MAX_VALUE, to)
            if (end > start) total += end - start
        }
        return total
    }
}
