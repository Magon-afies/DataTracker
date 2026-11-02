package com.mycompany.datatracker.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase

data class DataPlan(
    val id: Long,
    val sizeBytes: Long,
    val carrier: String,
    val startMs: Long,
    val expiryMs: Long,
    val firedMask: Int
)

data class PlanSnapshot(val usageBytes: Long, val capturedMs: Long, val trafficCounter: Long)

/** Local plan lifecycle and cumulative mobile-usage snapshots. */
class PlanStore(context: Context) {
    private val db = Db.get(context)

    fun create(sizeBytes: Long, carrier: String, startMs: Long, expiryMs: Long): Long {
        val values = ContentValues().apply {
            put("size_bytes", sizeBytes)
            put("carrier", carrier)
            put("start_ms", startMs)
            put("expiry_ms", expiryMs)
            put("fired_mask", 0)
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

    private fun android.database.Cursor.toPlan() = DataPlan(
        getLong(getColumnIndexOrThrow("id")),
        getLong(getColumnIndexOrThrow("size_bytes")),
        getString(getColumnIndexOrThrow("carrier")),
        getLong(getColumnIndexOrThrow("start_ms")),
        getLong(getColumnIndexOrThrow("expiry_ms")),
        getInt(getColumnIndexOrThrow("fired_mask"))
    )
}
