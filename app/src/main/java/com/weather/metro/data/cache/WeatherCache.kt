package com.weather.metro.data.cache

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class WeatherCache(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "weather_snapshot_v3.json"))

    suspend fun write(payload: String) = withContext(Dispatchers.IO) {
        var stream = file.startWrite()
        try {
            stream.write(payload.toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (error: Throwable) {
            file.failWrite(stream)
            throw error
        }
    }

    suspend fun read(): String? = withContext(Dispatchers.IO) {
        runCatching {
            if (!file.baseFile.exists()) null
            else file.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrNull()
    }

    suspend fun clear() = withContext(Dispatchers.IO) { file.delete() }
}
