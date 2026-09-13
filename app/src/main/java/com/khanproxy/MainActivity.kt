package com.khanproxy

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var adapter: TokenAdapter

    private var currentKey: String = ""
    private var pollJob: Job? = null
    private var isPolling: Boolean = false
    private var running: Boolean = false

    private val seenAccounts = linkedMapOf<String, TokenItem>()

    private val safPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data?.data != null) {
            val uri = result.data!!.data!!
            FileHelper.saveSafUri(this, uri)
            toast("✔ Folder granted — deploying...")
            scope.launch {
                delay(400)
                doDeploy()
            }
        } else {
            toast("Folder not selected")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_main)
            requestAllFilesAccess()
            requestNotif()
            requestOverlay()
            setupTokenList()
            setupButtons()

            val saved = getSharedPreferences("kmt", MODE_PRIVATE).getString("key", "")
            if (!saved.isNullOrEmpty()) {
                currentKey = saved
                showKey()
                scope.launch {
                    delay(1000)
                    autoDeployIfPossible()
                }
            } else {
                scope.launch {
                    delay(1500)
                    generateNewKey()
                }
            }
            updateStartStopUI()
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun setupTokenList() {
        adapter = TokenAdapter { token -> showTokenDialog(token) }
        findViewById<RecyclerView>(R.id.rvTokens).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btnNewKey).setOnClickListener { generateNewKey() }
        findViewById<Button>(R.id.btnRefresh).setOnClickListener { refreshOnce() }
        findViewById<Button>(R.id.btnDeploy).setOnClickListener { deployWithPermission() }
        findViewById<Button>(R.id.btnStartStop).setOnClickListener { toggleStartStop() }
        findViewById<Button>(R.id.btnOverlay).setOnClickListener { showOverlay() }
        findViewById<Button>(R.id.btnClear).setOnClickListener { confirmClear() }
        findViewById<Button>(R.id.btnPickFolder).setOnClickListener { pickFolder() }
    }

    // ============================================================
    // FILE MANAGER CHOOSER + SAF
    // ============================================================
    private fun pickFolder() {
        try {
            val treeIntent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                putExtra("android.provider.extra.INITIAL_URI",
                    Uri.parse("content://com.android.externalstorage.documents/root/primary"))
            }

            val pm = packageManager
            val handlers: List<ResolveInfo> = pm.queryIntentActivities(treeIntent, 0)

            if (handlers.isEmpty()) {
                safPicker.launch(treeIntent)
                return
            }

            // Build unique list by package
            val labels = ArrayList<String>()
            val intents = ArrayList<Intent>()
            val seen = HashSet<String>()

            for (info in handlers) {
                val pkg = info.activityInfo.packageName
                if (seen.contains(pkg)) continue
                seen.add(pkg)

                val appName = info.loadLabel(pm).toString()
                labels.add("$appName\n$pkg")

                val appIntent = Intent(treeIntent).apply { setPackage(pkg) }
                intents.add(appIntent)
            }

            // Direct launch if only one app
            if (intents.size == 1) {
                safPicker.launch(intents[0])
                return
            }

            // Prioritize MT Manager / ZArchiver / Files at top
            val priority = listOf("mt.mtxx", "zarchiver", "documentsui", "files")
            val sortedPairs = labels.zip(intents).sortedByDescending { pair ->
                priority.indexOfFirst { pair.first.contains(it, true) }.let { if (it == -1) -99 else -it }
            }
            val sortedLabels = sortedPairs.map { it.first }.toTypedArray()
            val sortedIntents = sortedPairs.map { it.second }

            AlertDialog.Builder(this)
                .setTitle("📁 Select File Manager")
                .setItems(sortedLabels) { _, which ->
                    try {
                        safPicker.launch(sortedIntents[which])
                    } catch (e: Exception) {
                        toast("Cannot open: ${e.message}")
                    }
                }
                .setNegativeButton("CANCEL", null)
                .show()

        } catch (e: Exception) {
            toast("Picker error: ${e.message}")
        }
    }

    // ============================================================
    private fun autoDeployIfPossible() {
        if (currentKey.isEmpty()) return
        scope.launch {
            if (FileHelper.hasSafUri(this@MainActivity) ||
                Build.VERSION.SDK_INT < 30 ||
                Environment.isExternalStorageManager()) {
                doDeploy()
            }
        }
    }

    private fun deployWithPermission() {
        if (currentKey.isEmpty()) { toast("Generate key first"); return }

        if (FileHelper.hasSafUri(this)) {
            scope.launch { doDeploy() }
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Deploy localconfig.json")
            .setMessage(
                "Android 11+ restricts direct write to Android/data.\n\n" +
                "Tap PICK FOLDER then select a file manager (MT Manager / ZArchiver) " +
                "and navigate to:\n\n" +
                "Internal → Android → data → com.dts.freefiremax → files\n\n" +
                "Then tap USE THIS FOLDER."
            )
            .setPositiveButton("PICK FOLDER") { _, _ -> pickFolder() }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private suspend fun doDeploy() {
        val r = withContext(Dispatchers.IO) {
            FileHelper.writeLocalConfig(this@MainActivity, currentKey)
        }
        if (r.ok) {
            AlertDialog.Builder(this@MainActivity)
                .setTitle("✔ Deployed")
                .setMessage("localconfig.json written to:\n${r.path}")
                .setPositiveButton("OK", null)
                .show()
        } else {
            AlertDialog.Builder(this@MainActivity)
                .setTitle("Folder access needed")
                .setMessage(
                    "Saved fallback at:\n${r.path}\n\n" +
                    "For FF folder, tap PICK FOLDER:\n" +
                    "Android → data → com.dts.freefiremax → files"
                )
                .setPositiveButton("PICK FOLDER") { _, _ -> pickFolder() }
                .setNegativeButton("CLOSE", null)
                .show()
        }
    }

    // ============================================================
    private fun generateNewKey() {
        if (isPolling) return
        isPolling = true
        toast("Generating new key...")

        scope.launch {
            val resp = withContext(Dispatchers.IO) { ApiClient.generateKey() }
            isPolling = false
            if (resp == null || resp.key.isEmpty()) {
                toast("Key generation failed")
                return@launch
            }
            currentKey = resp.key
            getSharedPreferences("kmt", MODE_PRIVATE)
                .edit().putString("key", resp.key).apply()

            seenAccounts.clear()
            adapter.submit(emptyList())
            showWaiting(true)
            showKey()
            updateTime()

            autoDeployIfPossible()
        }
    }

    private fun toggleStartStop() {
        if (running) {
            running = false
            pollJob?.cancel()
            try {
                findViewById<TextView>(R.id.tvLiveStatus).text = "● Stopped"
                findViewById<TextView>(R.id.tvLiveStatus).setTextColor(0xFFFFC107.toInt())
            } catch (_: Exception) {}
            toast("Stopped")
        } else {
            if (currentKey.isEmpty()) { toast("Generate key first"); return }
            running = true
            startPolling()
            toast("Started")
        }
        updateStartStopUI()
    }

    private fun updateStartStopUI() {
        try {
            val btn = findViewById<Button>(R.id.btnStartStop)
            btn.text = if (running) "■ STOP" else "▶ START"
            btn.backgroundTintList = android.content.res.ColorStateList.valueOf(
                if (running) 0xFFFF3045.toInt() else 0xFF4ADE80.toInt())
        } catch (_: Exception) {}
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive && running) {
                refreshOnce()
                delay(10_000L)
            }
        }
    }

    private fun refreshOnce() {
        if (currentKey.isEmpty() || !running) return
        scope.launch {
            val hist = withContext(Dispatchers.IO) { ApiClient.fetchHistory(currentKey) }
            if (hist == null) {
                try {
                    findViewById<TextView>(R.id.tvLiveStatus).text = "● Offline"
                    findViewById<TextView>(R.id.tvLiveStatus).setTextColor(0xFFFF3045.toInt())
                } catch (_: Exception) {}
                return@launch
            }
            updateTime()
            try {
                findViewById<TextView>(R.id.tvLiveStatus).text = "● Live · next check in 10s"
                findViewById<TextView>(R.id.tvLiveStatus).setTextColor(0xFF4ADE80.toInt())
            } catch (_: Exception) {}

            for (t in hist.tokens) {
                val name = t.name
                if (!seenAccounts.containsKey(name)) {
                    seenAccounts[name] = t
                }
            }

            val list = seenAccounts.values.toList().reversed()
            adapter.submit(list)
            try {
                findViewById<TextView>(R.id.tvCapturedCount).text =
                    "Region ${hist.region} · captured ${seenAccounts.size} accounts"
            } catch (_: Exception) {}

            if (list.isEmpty()) showWaiting(true) else showWaiting(false)
        }
    }

    private fun showKey() {
        try {
            findViewById<TextView>(R.id.tvKey).text = currentKey
            findViewById<TextView>(R.id.tvKey).startAnimation(glowPulse())
        } catch (_: Exception) {}
    }

    private fun showWaiting(show: Boolean) {
        try {
            findViewById<View>(R.id.waitingCard).visibility =
                if (show) View.VISIBLE else View.GONE
        } catch (_: Exception) {}
    }

    private fun updateTime() {
        try {
            val s = SimpleDateFormat("MMM dd, hh:mm:ss a", Locale.getDefault()).format(Date())
            findViewById<TextView>(R.id.tvLastUpdate).text = "Last update: $s"
        } catch (_: Exception) {}
    }

    private fun showTokenDialog(token: TokenItem) {
        val jwt = token.jwt
        val message = if (jwt.isEmpty()) "No JWT available" else jwt
        AlertDialog.Builder(this)
            .setTitle(token.name)
            .setMessage(message)
            .setPositiveButton("COPY JWT") { _, _ ->
                try {
                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("jwt", jwt))
                    toast("JWT copied")
                } catch (_: Exception) {}
            }
            .setNegativeButton("CLOSE", null)
            .show()
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle("Clear all tokens?")
            .setPositiveButton("CLEAR") { _, _ ->
                seenAccounts.clear()
                adapter.submit(emptyList())
                showWaiting(true)
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun requestAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val i = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    i.data = Uri.parse("package:$packageName")
                    startActivity(i)
                } catch (_: Exception) {
                    try {
                        startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                    } catch (_: Exception) {}
                }
            }
        }
    }

    private fun requestNotif() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS), 200)
        }
    }

    private fun requestOverlay() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")))
            } catch (_: Exception) {}
        }
    }

    private fun showOverlay() {
        try {
            if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
                requestOverlay(); return
            }
            val i = Intent(this, OverlayService::class.java)
            i.putExtra("key", currentKey)
            startService(i)
        } catch (_: Exception) {}
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun glowPulse(): Animation {
        val a = AlphaAnimation(1f, 0.65f)
        a.duration = 1200
        a.repeatMode = Animation.REVERSE
        a.repeatCount = Animation.INFINITE
        return a
    }

    override fun onDestroy() {
        pollJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }
}
