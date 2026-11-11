package com.crestpoint.datatracker.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase

data class PlanSnapshot(val usageBytes: Long, val capturedMs: Long, val trafficCounter: Long)

/** Local plan lifecycle and cumulative mobile-usage snapshots. */
class PlanStore(context: Context) {
    private val db = Db.get(context)

    init {
        if (!priceColumnReady) {
            synchronized(PlanStore::class.java) {
                if (!priceColumnReady) {
                    ensurePriceColumn(db.writableDatabase)
                    priceColumnReady = true
                }
            }
        }
    }

    fun create(
        sizeBytes: Long,
        carrier: String,
        startMs: Long,
        expiryMs: Long,
        priceNaira: Double = 0.0
    ): Long {
        val values = ContentValues().apply {
            put("size_bytes", sizeBytes)
            put("carrier", carrier)
            put("start_ms", startMs)
            put("expiry_ms", expiryMs)
            put("fired_mask", 0)
            put("price_naira", priceNaira.coerceAtLeast(0.0))
        }
        return db.writableDatabase.insert("data_plans", null, values)
    }

    fun active(now: Long = System.currentTimeMillis()): DataPlan? {
        db.readableDatabase.query(
            "data_plans", null, "start_ms <= ? AND expiry_ms >= ?",
            arrayOf(now.toString(), now.toString()), null, null, "start_ms DESC", "1"
        ).use { c ->
            return if (c.moveToFirst()) c.toPlan() else null
        }
    }

    fun latestSnapshot(planId: Long): PlanSnapshot? {
        db.readableDatabase.query(
            "plan_snapshots", arrayOf("usage_bytes", "captured_ms", "traffic_counter"), "plan_id = ?",
            arrayOf(planId.toString()), null, null, "captured_ms DESC", "1"
        ).use { c ->
            return if (c.moveToFirst()) PlanSnapshot(c.getLong(0), c.getLong(1), c.getLong(2)) else null
        }
    }

    fun saveSnapshot(planId: Long, usageBytes: Long, trafficCounter: Long = -1L) {
        val values = ContentValues().apply {
            put("plan_id", planId)
            put("captured_ms", System.currentTimeMillis())
            put("usage_bytes", usageBytes.coerceAtLeast(0L))
            put("traffic_counter", trafficCounter)
        }
        db.writableDatabase.insert("plan_snapshots", null, values)
    }

    fun markThresholdFired(planId: Long, threshold: Int): Boolean {
        val current = activePlanById(planId)?.firedMask ?: return false
        val bit = when (threshold) { 80 -> 1; 90 -> 2; else -> 4 }
        if (current and bit != 0) return false
        val values = ContentValues().apply { put("fired_mask", current or bit) }
        db.writableDatabase.update("data_plans", values, "id = ?", arrayOf(planId.toString()))
        return true
    }

    private fun activePlanById(id: Long): DataPlan? {
        db.readableDatabase.query("data_plans", null, "id = ?", arrayOf(id.toString()), null, null, null)
            .use { c -> return if (c.moveToFirst()) c.toPlan() else null }
    }

    private fun ensurePriceColumn(sdb: SQLiteDatabase) {
        var found = false
        sdb.rawQuery("PRAGMA table_info(data_plans)", null).use { c ->
            val nameIndex = c.getColumnIndex("name")
            while (c.moveToNext()) {
                if (c.getString(nameIndex) == "price_naira") {
                    found = true
                    break
                }
            }
        }
        if (!found) {
            sdb.execSQL("ALTER TABLE data_plans ADD COLUMN price_naira REAL NOT NULL DEFAULT 0")
        }
    }

    private fun android.database.Cursor.toPlan(): DataPlan {
        val priceIndex = getColumnIndex("price_naira")
        return DataPlan(
            id = getLong(getColumnIndexOrThrow("id")),
            sizeBytes = getLong(getColumnIndexOrThrow("size_bytes")),
            carrier = getString(getColumnIndexOrThrow("carrier")),
            startMs = getLong(getColumnIndexOrThrow("start_ms")),
            expiryMs = getLong(getColumnIndexOrThrow("expiry_ms")),
            priceNaira = if (priceIndex >= 0) getDouble(priceIndex) else 0.0,
            firedMask = getInt(getColumnIndexOrThrow("fired_mask"))
        )
    }

    companion object {
        @Volatile
        private var priceColumnReady = false
    }
}
