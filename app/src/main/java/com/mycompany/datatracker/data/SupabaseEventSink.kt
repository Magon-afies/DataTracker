package com.mycompany.datatracker.data

import com.mycompany.datatracker.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object SupabaseEventSink {
    suspend fun record(eventType: String) = withContext(Dispatchers.IO) {
        val base = BuildConfig.SUPABASE_URL.trimEnd('/')
        val key = BuildConfig.SUPABASE_ANON_KEY
        if (base.isBlank() || key.isBlank()) return@withContext
        runCatching {
            val connection = (URL("$base/rest/v1/app_events").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("apikey", key)
                setRequestProperty("Authorization", "Bearer $key")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Prefer", "return=minimal")
            }
            connection.outputStream.use { it.write("{\"event_type\":\"${eventType.replace("\"", "") }\"}".toByteArray()) }
            connection.responseCode
            connection.disconnect()
        }
    }
}
