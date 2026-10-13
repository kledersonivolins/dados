package app.meutunel

import android.content.Context
import org.json.JSONArray

/** Um servidor WireGuard. Vem de assets/servers.json. */
data class Server(
    val id: String,
    val name: String,
    val country: String,
    /** host:porta UDP do WireGuard, ex.: 203.0.113.10:51820 */
    val endpoint: String,
    val publicKey: String,
    /** Endereço deste aparelho dentro da VPN, ex.: 10.8.0.2/32 */
    val address: String,
    val dns: String,
    /** Porta TCP usada só para medir latência (resposta ou recusa contam). */
    val pingPort: Int,
) {
    val host: String get() = endpoint.substringBeforeLast(':')

    override fun toString() = if (country.isNotBlank()) "$name ($country)" else name

    companion object {
        fun loadAll(context: Context): List<Server> {
            val text = context.assets.open("servers.json").bufferedReader().use { it.readText() }
            val arr = JSONArray(text)
            return (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Server(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    country = o.optString("country", ""),
                    endpoint = o.getString("endpoint"),
                    publicKey = o.getString("publicKey"),
                    address = o.getString("address"),
                    dns = o.optString("dns", "1.1.1.1"),
                    pingPort = o.optInt("pingPort", 22),
                )
            }
        }
    }
}
