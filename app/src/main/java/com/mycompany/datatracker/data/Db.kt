package com.mycompany.datatracker.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** One small local database: usage history + data plans. */
class Db private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "tracker.db", null, 3) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE daily_usage (" +
                "day TEXT NOT NULL, uid INTEGER NOT NULL, pkg TEXT, label TEXT NOT NULL, " +
                "mobile INTEGER NOT NULL, wifi INTEGER NOT NULL, " +
                "fg INTEGER NOT NULL, bg INTEGER NOT NULL, PRIMARY KEY (day, uid))"
        )
        createPlanTables(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createPlanTables(db)
        if (oldVersion < 3) {
            db.execSQL("DROP TABLE IF EXISTS media_cache")
            db.execSQL("DROP TABLE IF EXISTS doc_cache")
        }
    }

    private fun createPlanTables(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS data_plans (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, size_bytes INTEGER NOT NULL, " +
                "carrier TEXT NOT NULL, start_ms INTEGER NOT NULL, expiry_ms INTEGER NOT NULL, " +
                "fired_mask INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS plan_snapshots (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, plan_id INTEGER NOT NULL, " +
                "captured_ms INTEGER NOT NULL, usage_bytes INTEGER NOT NULL, " +
                "traffic_counter INTEGER NOT NULL DEFAULT -1)"
        )
    }

    companion object {
        @Volatile
        private var instance: Db? = null

        fun get(context: Context): Db =
            instance ?: synchronized(this) {
                instance ?: Db(context).also { instance = it }
            }
    }
}
