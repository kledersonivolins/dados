# MeuTúnel

Cliente VPN para Android (Kotlin) sobre **WireGuard**, com servidor próprio.
Projeto original, escrito do zero: não usa código, servidores, protocolo nem
identidade visual de nenhum outro app.

## O que faz (v0.1)
- Conecta e desconecta o túnel (`VpnService` via `com.wireguard.android:tunnel`).
- Lista de servidores em `app/src/main/assets/servers.json`, com medição de latência.
- Exclui apps do VPN (split tunneling) por `excludeApplications`.
- Gera o par de chaves no aparelho (a privada nunca sai dele) e mostra a pública.
- Mostra bytes recebidos/enviados.
- Todo o tráfego IPv4 e IPv6 vai pelo túnel (IPv6 sem rota no servidor fica bloqueado, sem vazamento).

Não tem (ainda): anúncios, assinatura, contas, lista de servidores via API,
notificação persistente, "kill switch" dedicado (use *VPN sempre ativa* + *bloquear
conexões sem VPN* nas configurações do Android).

## Status
**Escrito sem ter sido compilado nem testado**: o ambiente em que foi criado não
alcança o Android SDK. Espere pequenos erros de compilação na primeira vez.

## Servidor (VPS Ubuntu/Debian)
```bash
sudo bash server/setup-wireguard.sh        # instala, configura e imprime a chave pública do servidor
```
Libere a porta **UDP 51820** no firewall da VPS.

## App
1. Abra a pasta `android-vpn` no Android Studio (ele baixa o Gradle e o SDK).
2. Edite `app/src/main/assets/servers.json`: IP da VPS em `endpoint` e a chave pública
   do servidor em `publicKey`.
3. Rode no aparelho. Copie a "Chave pública deste aparelho" que aparece no app.
4. No servidor: `sudo bash server/add-peer.sh <chave-copiada> 10.8.0.2`
   (o IP deve ser o mesmo de `address` no `servers.json`, sem o `/32`).
5. No app, toque em **Conectar** e aceite o pedido de permissão de VPN.

Um IP de VPN por aparelho: se usar vários aparelhos, dê a cada um um `address`
e um `add-peer.sh` diferentes.

## Estrutura
```
app/src/main/java/app/meutunel/
  App.kt             instância única do controlador
  VpnController.kt   monta a config WireGuard e liga/desliga o túnel
  MainActivity.kt    interface (servidor, conectar, apps excluídos, chave)
  Server.kt          modelo + leitura de servers.json
  Prefs.kt           chave privada e preferências
  Ping.kt            latência por TCP
server/              scripts de instalação do servidor
```
