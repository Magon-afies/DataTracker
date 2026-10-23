package com.mycompany.datatracker.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Day keys look like "2026-10-23" (device time zone). */
object Dates {
    private fun fmt() = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun day(millis: Long): String = fmt().format(Date(millis))

    /** The last [n] day keys, oldest first, ending with today. */
    fun lastDays(n: Int, now: Long = System.currentTimeMillis()): List<String> {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val list = ArrayList<String>(n)
        for (i in 0 until n) {
            list.add(day(cal.timeInMillis))
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        list.reverse()
        return list
    }

    fun startOfDay(key: String): Long = fmt().parse(key)?.time ?: 0L

    fun endOfDay(key: String): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = startOfDay(key) }
        cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }
}
