package com.khanproxy

import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object ApiClient {
    private const val TAG = "KhanApi"
    private const val BASE = "https://all-capture-jwt.vercel.app"
    private const val GEN_KEY = "khanbro"

    fun generateKey(): KeyResponse? {
        return try {
            val url = "$BASE/jwt_generate?key=" + URLEncoder.encode(GEN_KEY, "UTF-8")
            val body = httpGet(url) ?: return null
            Log.i(TAG, "gen: $body")
            KeyResponse.fromJson(JSONObject(body))
        } catch (e: Exception) {
            Log.e(TAG, "generateKey", e); null
        }
    }

    fun fetchHistory(key: String): HistoryResponse? {
        return try {
            val url = "$BASE/history_token?key=" + URLEncoder.encode(key, "UTF-8")
            val body = httpGet(url) ?: return null
            HistoryResponse.fromJson(JSONObject(body))
        } catch (e: Exception) {
            Log.e(TAG, "fetchHistory", e); null
        }
    }

    private fun httpGet(urlStr: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            val u = URL(urlStr)
            conn = (u.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("User-Agent", "KhanMultiToken/1.0")
                setRequestProperty("Accept", "application/json")
            }
            val code = conn.responseCode
            if (code != 200) {
                Log.w(TAG, "HTTP $code for $urlStr")
                return null
            }
            conn.inputStream.bufferedReader().use(BufferedReader::readText)
        } catch (e: Exception) {
            Log.e(TAG, "httpGet", e); null
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }
}
