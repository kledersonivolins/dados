package app.meutunel

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Um servidor WireGuard. Vem de assets/servers.json. */
data class Server(
    val id: String,
    val name: String,
    val country: String,
    /** host:porta UDP do WireGuard, ex.: 203.0.113.10:51820 */
    val endpoint: String,
    val publicKey: String,
    /**
     * Endereço deste aparelho dentro da VPN, ex.: 10.8.0.2/32. Se for null, o app o
     * pede à API ([ApiClient.registerDevice]) na hora de conectar.
     */
    val address: String?,
    val dns: String,
    /** Porta TCP usada só para medir latência (resposta ou recusa contam). */
    val pingPort: Int,
) {
    val host: String get() = endpoint.substringBeforeLast(':')

    override fun toString() = if (country.isNotBlank()) "$name ($country)" else name

    companion object {
        /** Servidores embutidos, usados se a API não estiver configurada ou falhar. */
        fun loadAssets(context: Context): List<Server> =
            parse(context.assets.open("servers.json").bufferedReader().use { it.readText() })

        /** Aceita um array JSON ou um objeto {"servers": [...]} (resposta da API). */
        fun parse(text: String): List<Server> {
            val trimmed = text.trim()
            val arr = if (trimmed.startsWith("[")) JSONArray(trimmed)
            else JSONObject(trimmed).getJSONArray("servers")
            return (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Server(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    country = o.optString("country", ""),
                    endpoint = o.getString("endpoint"),
                    publicKey = o.getString("publicKey"),
                    address = o.optString("address", "").ifBlank { null },
                    dns = o.optString("dns", "1.1.1.1"),
                    pingPort = o.optInt("pingPort", 22),
                )
            }
        }
    }
}
