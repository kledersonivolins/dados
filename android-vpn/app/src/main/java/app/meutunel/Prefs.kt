package app.meutunel

import android.content.Context
import com.wireguard.crypto.Key
import com.wireguard.crypto.KeyPair

/** Armazena a chave privada do aparelho e a lista de apps excluídos do VPN. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("meutunel", Context.MODE_PRIVATE)

    /** Gera o par de chaves na primeira vez; a privada nunca sai do aparelho. */
    val keyPair: KeyPair
        get() {
            val saved = sp.getString(KEY_PRIVATE, null)
            if (saved != null) return KeyPair(Key.fromBase64(saved))
            val fresh = KeyPair()
            sp.edit().putString(KEY_PRIVATE, fresh.privateKey.toBase64()).apply()
            return fresh
        }

    var excludedApps: Set<String>
        get() = sp.getStringSet(KEY_EXCLUDED, emptySet()) ?: emptySet()
        set(value) = sp.edit().putStringSet(KEY_EXCLUDED, value).apply()

    var lastServerId: String?
        get() = sp.getString(KEY_LAST_SERVER, null)
        set(value) = sp.edit().putString(KEY_LAST_SERVER, value).apply()

    private companion object {
        const val KEY_PRIVATE = "private_key"
        const val KEY_EXCLUDED = "excluded_apps"
        const val KEY_LAST_SERVER = "last_server"
    }
}
