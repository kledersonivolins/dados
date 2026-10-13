package app.meutunel

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import java.util.concurrent.Executors

/**
 * Liga e desliga o túnel WireGuard. O próprio [GoBackend] registra o VpnService
 * do Android (vem no manifesto da biblioteca), então não precisamos de um serviço nosso.
 *
 * A permissão de VPN (VpnService.prepare) precisa ter sido concedida antes de [connect].
 */
class VpnController(context: Context) {
    private val appContext = context.applicationContext
    private val backend = GoBackend(appContext)
    private val prefs = Prefs(appContext)
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    @Volatile
    var state: Tunnel.State = Tunnel.State.DOWN
        private set

    /** Chamado na thread principal sempre que o estado muda. */
    var onStateChange: ((Tunnel.State) -> Unit)? = null

    val publicKey: String get() = prefs.keyPair.publicKey.toBase64()

    private val tunnel = object : Tunnel {
        override fun getName() = "meutunel"

        override fun onStateChange(newState: Tunnel.State) {
            state = newState
            main.post { this@VpnController.onStateChange?.invoke(newState) }
        }
    }

    fun connect(server: Server, onError: (Throwable) -> Unit) {
        worker.execute {
            try {
                backend.setState(tunnel, Tunnel.State.UP, buildConfig(server))
            } catch (t: Throwable) {
                main.post { onError(t) }
            }
        }
    }

    fun disconnect(onError: (Throwable) -> Unit) {
        worker.execute {
            try {
                backend.setState(tunnel, Tunnel.State.DOWN, null)
            } catch (t: Throwable) {
                main.post { onError(t) }
            }
        }
    }

    /** Bytes recebidos e enviados desde que o túnel subiu, ou null se estiver desligado. */
    fun traffic(): Pair<Long, Long>? = try {
        if (state != Tunnel.State.UP) null
        else backend.getStatistics(tunnel).let { it.totalRx() to it.totalTx() }
    } catch (_: Throwable) {
        null
    }

    private fun buildConfig(server: Server): Config {
        val iface = Interface.Builder()
            .setKeyPair(prefs.keyPair)
            .parseAddresses(requireNotNull(server.address) { "servidor sem endereço" })
            .parseDnsServers(server.dns)
            .excludeApplications(prefs.excludedApps)
            .build()
        val peer = Peer.Builder()
            .parsePublicKey(server.publicKey)
            .parseEndpoint(server.endpoint)
            // Todo o tráfego IPv4 e IPv6 vai pelo túnel (IPv6 sem rota no servidor fica
            // bloqueado, o que evita vazamento fora da VPN).
            .parseAllowedIPs("0.0.0.0/0, ::/0")
            .setPersistentKeepalive(25)
            .build()
        return Config.Builder().setInterface(iface).addPeer(peer).build()
    }
}
