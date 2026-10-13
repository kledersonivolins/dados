package app.meutunel

import android.app.Application

class App : Application() {
    lateinit var vpn: VpnController
        private set

    override fun onCreate() {
        super.onCreate()
        // Uma única instância do backend para o processo todo: o túnel continua
        // de pé mesmo quando a Activity é destruída.
        vpn = VpnController(this)
    }
}
