# MeuTúnel

Cliente VPN para Android (Kotlin) sobre **WireGuard**, com servidor próprio.
Projeto original, escrito do zero: não usa código, servidores, protocolo nem
identidade visual de nenhum outro app.

## O que faz (v0.1)
- Conecta e desconecta o túnel (`VpnService` via `com.wireguard.android:tunnel`).
- Lista de servidores vinda da **API própria** (`api/`), com cache offline e `assets/servers.json` como reserva, e medição de latência.
- Cadastro automático do aparelho: o app envia a chave pública e a API devolve o IP na VPN.
- Exclui apps do VPN (split tunneling) por `excludeApplications`.
- Gera o par de chaves no aparelho (a privada nunca sai dele) e mostra a pública.
- Mostra bytes recebidos/enviados.
- Todo o tráfego IPv4 e IPv6 vai pelo túnel (IPv6 sem rota no servidor fica bloqueado, sem vazamento).

Não tem (ainda): anúncios, assinatura, contas por usuário,
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

## API (recomendado)
Veja `api/README.md`: instala na mesma VPS, cadastra os aparelhos sozinha e entrega a lista de servidores.
Sem API, siga o fluxo manual abaixo.

## App
1. Abra a pasta `android-vpn` no Android Studio (ele baixa o Gradle e o SDK).
2. Edite `app/src/main/assets/servers.json`: IP da VPS em `endpoint` e a chave pública
   do servidor em `publicKey`.
3. Rode no aparelho. Copie a "Chave pública deste aparelho" que aparece no app.
4. (Fluxo manual, sem API) No servidor: `sudo bash server/add-peer.sh <chave-copiada> 10.8.0.2`
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
  ApiClient.kt       lista de servidores e cadastro do aparelho na API
server/              scripts de instalação do servidor WireGuard
api/                 API (Python) de servidores e cadastro de aparelhos
```
