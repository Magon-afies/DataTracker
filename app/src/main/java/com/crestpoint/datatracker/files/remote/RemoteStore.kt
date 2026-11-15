package com.crestpoint.datatracker.files.remote

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

data class Connection(val name: String, val host: String, val port: Int, val user: String, val pass: String, val secure: Boolean)

/** Saved FTP/FTPS servers. Passwords are encrypted with a Keystore-backed key. */
object RemoteStore {
    private fun prefs(ctx: Context): SharedPreferences {
        val key = MasterKey.Builder(ctx.applicationContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(
            ctx.applicationContext, "remote_connections", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun list(ctx: Context): List<Connection> = try {
        val arr = JSONArray(prefs(ctx).getString("list", "[]") ?: "[]")
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Connection(o.getString("name"), o.getString("host"), o.getInt("port"), o.getString("user"), o.getString("pass"), o.getBoolean("secure"))
        }
    } catch (_: Exception) { emptyList() }

    private fun save(ctx: Context, l: List<Connection>) {
        val arr = JSONArray()
        l.forEach { arr.put(JSONObject().put("name", it.name).put("host", it.host).put("port", it.port).put("user", it.user).put("pass", it.pass).put("secure", it.secure)) }
        prefs(ctx).edit().putString("list", arr.toString()).apply()
    }

    fun add(ctx: Context, c: Connection) = save(ctx, list(ctx).filter { it.name != c.name } + c)
    fun remove(ctx: Context, name: String) = save(ctx, list(ctx).filter { it.name != name })
}
