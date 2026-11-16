package com.crestpoint.datatracker.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.crestpoint.datatracker.usage.AppUsage
import com.crestpoint.datatracker.usage.UsageRepository
import com.crestpoint.datatracker.util.Dates

data class DayTotal(val day: String, val mobile: Long, val wifi: Long)

/** Permanent per-day, per-app usage history (survives Android's counter expiry). */
class HistoryStore(context: Context) {

    private val db = Db.get(context)

    fun saveDay(day: String, list: List<AppUsage>) {
        val w = db.writableDatabase
        w.beginTransaction()
        try {
            w.delete("daily_usage", "day = ?", arrayOf(day))
            for (a in list) {
                val cv = ContentValues()
                cv.put("day", day)
                cv.put("uid", a.uid)
                cv.put("pkg", a.packageName)
                cv.put("label", a.label)
                cv.put("mobile", a.mobileTotal)
                cv.put("wifi", a.wifiTotal)
                cv.put("fg", a.foregroundBytes)
                cv.put("bg", a.backgroundBytes)
                w.insertWithOnConflict("daily_usage", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
    }

    /** Mobile/Wi-Fi totals per day, from [fromDay] (inclusive) to today. */
    fun dayTotals(fromDay: String): List<DayTotal> {
        val out = ArrayList<DayTotal>()
        db.readableDatabase.rawQuery(
            "SELECT day, SUM(mobile), SUM(wifi) FROM daily_usage " +
                "WHERE day >= ? GROUP BY day ORDER BY day",
            arrayOf(fromDay)
        ).use { c ->
            while (c.moveToNext()) out.add(DayTotal(c.getString(0), c.getLong(1), c.getLong(2)))
        }
        return out
    }

    fun storedDays(): Int {
        db.readableDatabase.rawQuery("SELECT COUNT(DISTINCT day) FROM daily_usage", null).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    /** Pull whatever Android still has for the last [days] days into the history. */
    fun backfill(repo: UsageRepository, days: Int) {
        val now = System.currentTimeMillis()
        for (key in Dates.lastDays(days, now)) {
            val start = Dates.startOfDay(key)
            val end = minOf(Dates.endOfDay(key), now)
            if (end <= start) continue
            val list = repo.query(start, end)
            if (list.isNotEmpty()) saveDay(key, list)
        }
    }
}
