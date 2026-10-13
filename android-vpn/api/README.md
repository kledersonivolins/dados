# API do MeuTúnel

Pequena API em Python (só biblioteca padrão, Python 3.9+) que:
- entrega a **lista de servidores** ao app;
- **cadastra aparelhos**: o app manda a chave pública e recebe um IP na VPN; a API
  aplica o peer no WireGuard local (`wg set`). Acabou o `add-peer.sh` manual.

| Rota | Auth | Resposta |
|---|---|---|
| `GET /healthz` | não | `{"status":"ok"}` |
| `GET /v1/servers` | Bearer | `{"servers":[...]}` |
| `POST /v1/servers/<id>/device` `{"publicKey":"..."}` | Bearer | `{"address":"10.8.0.2/32"}` (idempotente) |

## Instalar na VPS (a mesma do WireGuard)
```bash
# 1) arquivos
sudo mkdir -p /opt/meutunel-api
sudo cp server.py /opt/meutunel-api/
sudo cp servers.example.json /opt/meutunel-api/servers.json   # edite: IP, chave pública, etc.

# 2) segredo e caminhos
sudo tee /etc/meutunel-api.env >/dev/null <<ENV
API_TOKEN=$(openssl rand -hex 24)
SERVERS_FILE=/opt/meutunel-api/servers.json
STATE_FILE=/var/lib/meutunel-api/state.json
ENV
sudo chmod 600 /etc/meutunel-api.env
sudo cat /etc/meutunel-api.env     # anote o API_TOKEN para o app

# 3) serviço
sudo cp meutunel-api.service /etc/systemd/system/
sudo systemctl daemon-reload && sudo systemctl enable --now meutunel-api
```

A API escuta em `127.0.0.1:8080`. **Ponha HTTPS na frente** (o token viaja no cabeçalho).
Com um domínio apontando para a VPS, o Caddy resolve em duas linhas
(`/etc/caddy/Caddyfile`):
```
api.seudominio.com {
    reverse_proxy 127.0.0.1:8080
}
```

## Configuração (`servers.json`)
Cada servidor tem `id`, `name`, `country`, `endpoint`, `publicKey`, `dns`, `pingPort`, e:
- **`"local": true` + `"subnet"`**: este servidor é o WireGuard da própria VPS. A API aloca
  um IP por aparelho (o `.1` é do servidor) e roda `wg set`. Ao reiniciar, reaplica os peers salvos.
- **`"address"` fixo**: servidor cadastrado à mão (rode `add-peer.sh` lá). A API só repassa o endereço.

Variáveis: `API_TOKEN` (obrigatória, ≥ 24 caracteres), `SERVERS_FILE`, `STATE_FILE`,
`WG_IFACE` (wg0), `MAX_DEVICES` (20), `BIND` (127.0.0.1), `PORT` (8080).

> Não misture a API com `add-peer.sh` na mesma sub-rede: os dois escolheriam IPs sem saber um do outro.

## No app
Em `~/.gradle/gradle.properties` (fora do repositório):
```
meutunelApiUrl=https://api.seudominio.com
meutunelApiToken=<o API_TOKEN>
```
Sem isso o app usa só `assets/servers.json`. O token vai dentro do APK, então **não é
um segredo forte**: serve para uso pessoal ou grupo pequeno. Para distribuir publicamente
seria preciso conta/login por usuário.

## Teste local
```bash
API_TOKEN=$(openssl rand -hex 24) SERVERS_FILE=servers.example.json STATE_FILE=/tmp/state.json \
WG_BIN=echo python3 server.py     # WG_BIN=echo só simula o `wg`
```
