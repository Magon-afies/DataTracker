package com.mycompany.datatracker.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** One small local database: usage history + photo/PDF scan cache. */
class Db private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "tracker.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE daily_usage (" +
                "day TEXT NOT NULL, uid INTEGER NOT NULL, pkg TEXT, label TEXT NOT NULL, " +
                "mobile INTEGER NOT NULL, wifi INTEGER NOT NULL, " +
                "fg INTEGER NOT NULL, bg INTEGER NOT NULL, PRIMARY KEY (day, uid))"
        )
        db.execSQL(
            "CREATE TABLE media_cache (" +
                "id INTEGER PRIMARY KEY, name TEXT NOT NULL, path TEXT NOT NULL, " +
                "modified INTEGER NOT NULL, size INTEGER NOT NULL, dhash INTEGER NOT NULL, " +
                "ocr TEXT NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE doc_cache (uri TEXT PRIMARY KEY, name TEXT NOT NULL, ocr TEXT NOT NULL)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    companion object {
        @Volatile
        private var instance: Db? = null

        fun get(context: Context): Db =
            instance ?: synchronized(this) {
                instance ?: Db(context).also { instance = it }
            }
    }
}
