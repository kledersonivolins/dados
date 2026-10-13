package app.meutunel

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fala com a API própria (api/server.py). Bloqueante: chame fora da thread principal.
 * O token vai no APK (BuildConfig), então não é um segredo forte: serve para uso
 * pessoal ou de um grupo pequeno. Use sempre HTTPS (o Android bloqueia HTTP puro).
 */
object ApiClient {
    val enabled: Boolean get() = BuildConfig.API_URL.isNotBlank() && BuildConfig.API_TOKEN.isNotBlank()

    /** JSON bruto da lista de servidores. */
    fun fetchServers(): String = request("GET", "/v1/servers", null)

    /** Pede à API um endereço de VPN para esta chave pública; devolve ex. "10.8.0.2/32". */
    fun registerDevice(serverId: String, publicKey: String): String {
        val body = JSONObject().put("publicKey", publicKey).toString()
        val reply = request("POST", "/v1/servers/$serverId/device", body)
        return JSONObject(reply).getString("address")
    }

    private fun request(method: String, path: String, body: String?): String {
        val conn = URL(BuildConfig.API_URL.trimEnd('/') + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("Authorization", "Bearer ${BuildConfig.API_TOKEN}")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val msg = runCatching { JSONObject(text).getString("error") }.getOrDefault("HTTP $code")
                throw java.io.IOException(msg)
            }
            return text
        } finally {
            conn.disconnect()
        }
    }
}
