package com.khanproxy

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.File

object FileHelper {
    private const val TAG = "KhanFile"
    private val FF_PKGS = listOf("com.dts.freefiremax", "com.dts.freefireth")

    const val PREF_SAF_URI = "saf_tree_uri"

    data class WriteResult(
        val ok: Boolean,
        val path: String,
        val needsSaf: Boolean = false,
        val error: String? = null
    )

    fun getConfigJson(key: String): String {
        return "{\"serverLoginUrl\":\"https://all-capture-jwt.vercel.app/$key/\"}"
    }

    fun writeLocalConfig(ctx: Context, key: String): WriteResult {
        val json = getConfigJson(key)
        val external = Environment.getExternalStorageDirectory()

        // 1) Try direct (Android <11 / root)
        for (pkg in FF_PKGS) {
            try {
                val dir = File(external, "Android/data/$pkg/files")
                if (!dir.exists()) dir.mkdirs()
                if (dir.exists()) {
                    val f = File(dir, "localconfig.json")
                    f.writeText(json)
                    Log.i(TAG, "direct wrote: ${f.absolutePath}")
                    return WriteResult(true, f.absolutePath)
                }
            } catch (e: Exception) {
                Log.e(TAG, "direct $pkg", e)
            }
        }

        // 2) SAF
        val safResult = trySafWrite(ctx, key)
        if (safResult.ok) return safResult

        // 3) Fallback
        return try {
            val fallback = File(external, "KhanMultiToken/localconfig.json")
            fallback.parentFile?.mkdirs()
            fallback.writeText(json)
            WriteResult(false, fallback.absolutePath, needsSaf = true,
                error = "SAF required")
        } catch (e: Exception) {
            WriteResult(false, "", needsSaf = true, error = e.message)
        }
    }

    private fun trySafWrite(ctx: Context, key: String): WriteResult {
        val prefs = ctx.getSharedPreferences("kmt", Context.MODE_PRIVATE)
        val uriStr = prefs.getString(PREF_SAF_URI, null) ?: return WriteResult(false, "")
        return try {
            val treeUri = Uri.parse(uriStr)
            val docTree = DocumentFile.fromTreeUri(ctx, treeUri) ?: return WriteResult(false, "")
            if (!docTree.canWrite()) return WriteResult(false, "")

            docTree.findFile("localconfig.json")?.delete()
            val newFile = docTree.createFile("application/json", "localconfig.json")
                ?: return WriteResult(false, "")

            ctx.contentResolver.openOutputStream(newFile.uri)?.use { os ->
                os.write(getConfigJson(key).toByteArray())
                os.flush()
            }
            Log.i(TAG, "SAF wrote: ${newFile.uri}")
            WriteResult(true, newFile.uri.toString())
        } catch (e: Exception) {
            Log.e(TAG, "saf write", e)
            WriteResult(false, "")
        }
    }

    fun saveSafUri(ctx: Context, uri: Uri) {
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            ctx.contentResolver.takePersistableUriPermission(uri, flags)
            ctx.getSharedPreferences("kmt", Context.MODE_PRIVATE)
                .edit().putString(PREF_SAF_URI, uri.toString()).apply()
            Log.i(TAG, "SAF uri saved: $uri")
        } catch (e: Exception) {
            Log.e(TAG, "saveSafUri", e)
        }
    }

    fun hasSafUri(ctx: Context): Boolean {
        return ctx.getSharedPreferences("kmt", Context.MODE_PRIVATE)
            .getString(PREF_SAF_URI, null) != null
    }

    fun clearSafUri(ctx: Context) {
        ctx.getSharedPreferences("kmt", Context.MODE_PRIVATE)
            .edit().remove(PREF_SAF_URI).apply()
    }
}
