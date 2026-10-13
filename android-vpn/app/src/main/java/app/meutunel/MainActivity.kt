package app.meutunel

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.wireguard.android.backend.Tunnel
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private val vpn get() = (application as App).vpn
    private val prefs by lazy { Prefs(this) }
    private val io = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())

    private var servers: List<Server> = emptyList()
    private lateinit var spinner: Spinner
    private lateinit var connectButton: Button
    private lateinit var statusText: TextView
    private lateinit var statsText: TextView
    private lateinit var pingText: TextView

    private var working = false

    private val statsTick = object : Runnable {
        override fun run() {
            renderStats()
            ui.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        spinner = findViewById(R.id.serverSpinner)
        connectButton = findViewById(R.id.connectButton)
        statusText = findViewById(R.id.statusText)
        statsText = findViewById(R.id.statsText)
        pingText = findViewById(R.id.pingText)

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val server = servers.getOrNull(pos) ?: return
                prefs.lastServerId = server.id
                measurePing(server)
            }

            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        // Mostra já o último cache (ou os servidores embutidos) e atualiza pela API em seguida.
        setServers(loadLocalServers())
        refreshServers()

        findViewById<TextView>(R.id.publicKeyText).text = vpn.publicKey
        findViewById<Button>(R.id.copyKeyButton).setOnClickListener {
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("chave pública", vpn.publicKey))
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.excludedButton).setOnClickListener { pickExcludedApps() }
        connectButton.setOnClickListener { toggle() }

        vpn.onStateChange = {
            working = false
            render()
        }
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
        ui.post(statsTick)
    }

    override fun onPause() {
        ui.removeCallbacks(statsTick)
        super.onPause()
    }

    override fun onDestroy() {
        vpn.onStateChange = null
        super.onDestroy()
    }

    private fun toggle() {
        if (working) return
        if (vpn.state == Tunnel.State.UP) {
            working = true
            render()
            vpn.disconnect(::onVpnError)
            return
        }
        // Pede a permissão de VPN ao sistema (diálogo "Solicitação de conexão").
        val intent = android.net.VpnService.prepare(this)
        if (intent != null) {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQ_VPN)
        } else {
            startTunnel()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_VPN && resultCode == Activity.RESULT_OK) startTunnel()
    }

    private fun startTunnel() {
        val server = servers.getOrNull(spinner.selectedItemPosition) ?: return
        working = true
        render()
        io.execute {
            try {
                // O cadastro é idempotente: repetir devolve o mesmo IP e reaplica o peer.
                val address = server.address
                    ?: ApiClient.registerDevice(server.id, vpn.publicKey)
                ui.post { vpn.connect(server.copy(address = address), ::onVpnError) }
            } catch (t: Throwable) {
                ui.post { onVpnError(t) }
            }
        }
    }

    private fun loadLocalServers(): List<Server> {
        prefs.cachedServers?.let { cached ->
            runCatching { Server.parse(cached) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return runCatching { Server.loadAssets(this) }.getOrDefault(emptyList())
    }

    private fun refreshServers() {
        if (!ApiClient.enabled) return
        io.execute {
            val raw = runCatching { ApiClient.fetchServers() }.getOrNull() ?: return@execute
            val list = runCatching { Server.parse(raw) }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return@execute
            prefs.cachedServers = raw
            ui.post { if (!isFinishing) setServers(list) }
        }
    }

    private fun setServers(list: List<Server>) {
        // Mantém a seleção atual (ou a última usada) ao trocar a lista.
        val keepId = servers.getOrNull(spinner.selectedItemPosition)?.id ?: prefs.lastServerId
        servers = list
        if (list.isEmpty()) {
            Toast.makeText(this, R.string.no_servers, Toast.LENGTH_LONG).show()
        }
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, list)
        list.indexOfFirst { it.id == keepId }.takeIf { it >= 0 }?.let { spinner.setSelection(it) }
        render()
    }

    private fun onVpnError(t: Throwable) {
        working = false
        render()
        Toast.makeText(this, t.message ?: t.javaClass.simpleName, Toast.LENGTH_LONG).show()
    }

    private fun measurePing(server: Server) {
        pingText.setText(R.string.ping_testing)
        io.execute {
            val ms = Ping.measure(server)
            ui.post {
                // Ignora se o usuário já trocou de servidor.
                if (servers.getOrNull(spinner.selectedItemPosition)?.id != server.id) return@post
                pingText.text = if (ms != null) getString(R.string.ping_ms, ms) else getString(R.string.ping_fail)
            }
        }
    }

    private fun render() {
        val up = vpn.state == Tunnel.State.UP
        connectButton.isEnabled = servers.isNotEmpty() && !working
        connectButton.setText(if (up) R.string.disconnect else R.string.connect)
        statusText.setText(
            when {
                working -> R.string.status_working
                up -> R.string.status_up
                else -> R.string.status_down
            }
        )
        spinner.isEnabled = !up && !working
        renderStats()
    }

    private fun renderStats() {
        val t = vpn.traffic()
        statsText.text = if (t == null) "" else "↓ ${human(t.first)}   ↑ ${human(t.second)}"
    }

    private fun human(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
    }

    /** Mostra os apps com ícone de lançador e deixa marcar quais ficam fora do túnel. */
    private fun pickExcludedApps() {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val pm = packageManager
        val apps = pm.queryIntentActivities(launcher, PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { it.first != packageName }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
        val selected = prefs.excludedApps.toMutableSet()
        val checked = BooleanArray(apps.size) { apps[it].first in selected }

        AlertDialog.Builder(this)
            .setTitle(R.string.excluded_apps)
            .setMultiChoiceItems(apps.map { it.second }.toTypedArray(), checked) { _, i, on ->
                if (on) selected.add(apps[i].first) else selected.remove(apps[i].first)
            }
            .setPositiveButton(R.string.ok) { _, _ ->
                prefs.excludedApps = selected
                if (vpn.state == Tunnel.State.UP) {
                    Toast.makeText(this, "Reconecte para aplicar", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private companion object {
        const val REQ_VPN = 1
    }
}
