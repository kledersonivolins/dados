package app.meutunel

import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket

object Ping {
    /**
     * Tempo de uma conexão TCP até o servidor, em ms. Uma recusa (porta fechada) também
     * prova que o host respondeu, então conta. Devolve null se der timeout/falhar.
     * O WireGuard usa UDP e não responde a sondas, por isso medimos por TCP.
     */
    fun measure(server: Server, timeoutMs: Int = 2000): Int? {
        val start = System.nanoTime()
        return try {
            Socket().use { it.connect(InetSocketAddress(server.host, server.pingPort), timeoutMs) }
            elapsed(start)
        } catch (e: ConnectException) {
            if (e.message?.contains("refused", ignoreCase = true) == true) elapsed(start) else null
        } catch (_: Exception) {
            null
        }
    }

    private fun elapsed(startNs: Long) = ((System.nanoTime() - startNs) / 1_000_000).toInt()
}
