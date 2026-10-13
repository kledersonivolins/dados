#!/usr/bin/env python3
"""API do MeuTúnel: lista de servidores e cadastro de aparelhos (só biblioteca padrão).

GET  /healthz                         -> "ok" (sem autenticação)
GET  /v1/servers                      -> {"servers": [...]}
POST /v1/servers/<id>/device          -> {"address": "10.8.0.2/32"}
     corpo: {"publicKey": "<chave pública WireGuard do aparelho, base64>"}

Autenticação: cabeçalho "Authorization: Bearer <API_TOKEN>". Rode atrás de HTTPS.

Servidores com "local": true são gerenciados por este processo: ele aloca um IP por
aparelho dentro de "subnet" e roda `wg set` na interface local. Os demais devem trazer
um "address" fixo e são cadastrados à mão no próprio servidor (add-peer.sh).
"""
import base64
import binascii
import hmac
import ipaddress
import json
import os
import re
import subprocess
import sys
import tempfile
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

TOKEN = os.environ.get("API_TOKEN", "")
SERVERS_FILE = os.environ.get("SERVERS_FILE", "servers.json")
STATE_FILE = os.environ.get("STATE_FILE", "state.json")
WG_IFACE = os.environ.get("WG_IFACE", "wg0")
WG_BIN = os.environ.get("WG_BIN", "wg")
MAX_DEVICES = int(os.environ.get("MAX_DEVICES", "20"))
BIND = os.environ.get("BIND", "127.0.0.1")
PORT = int(os.environ.get("PORT", "8080"))

PRIVATE_FIELDS = {"local", "subnet"}
ID_RE = re.compile(r"^[A-Za-z0-9_-]{1,64}$")
KEY_RE = re.compile(r"^[A-Za-z0-9+/]{43}=$")

lock = threading.Lock()


def load_servers():
    with open(SERVERS_FILE, encoding="utf-8") as f:
        servers = json.load(f)
    for s in servers:
        for field in ("id", "name", "endpoint", "publicKey"):
            if field not in s:
                raise ValueError(f"servidor sem '{field}': {s}")
        if not ID_RE.match(s["id"]):
            raise ValueError(f"id inválido: {s['id']}")
        if s.get("local") and "subnet" not in s:
            raise ValueError(f"servidor local '{s['id']}' precisa de 'subnet'")
    return servers


def load_state():
    try:
        with open(STATE_FILE, encoding="utf-8") as f:
            return json.load(f)
    except FileNotFoundError:
        return {"devices": {}}


def save_state(state):
    directory = os.path.dirname(os.path.abspath(STATE_FILE))
    fd, tmp = tempfile.mkstemp(dir=directory)
    with os.fdopen(fd, "w", encoding="utf-8") as f:
        json.dump(state, f, indent=2)
    os.chmod(tmp, 0o600)
    os.replace(tmp, STATE_FILE)


def valid_key(key):
    if not isinstance(key, str) or not KEY_RE.match(key):
        return False
    try:
        return len(base64.b64decode(key, validate=True)) == 32
    except (binascii.Error, ValueError):
        return False


def wg_set_peer(key, address):
    subprocess.run(
        [WG_BIN, "set", WG_IFACE, "peer", key, "allowed-ips", address],
        check=True, capture_output=True, timeout=10,
    )


def allocate(server, key, state):
    """Devolve o IP do aparelho neste servidor, alocando um novo se for a 1ª vez."""
    devices = state["devices"].setdefault(server["id"], {})
    if key in devices:
        return devices[key]
    if sum(len(d) for d in state["devices"].values()) >= MAX_DEVICES:
        raise OverflowError("limite de aparelhos atingido")
    used = {ipaddress.ip_interface(a).ip for a in devices.values()}
    for host in list(ipaddress.ip_network(server["subnet"]).hosts())[1:]:  # .1 é o servidor
        if host not in used:
            return f"{host}/32"
    raise OverflowError("sub-rede esgotada")


def register(server, key):
    with lock:
        state = load_state()
        address = allocate(server, key, state)
        wg_set_peer(key, address)  # se falhar, nada é gravado
        state["devices"].setdefault(server["id"], {})[key] = address
        save_state(state)
        return address


def reapply_peers(servers):
    """Reaplica os peers salvos (o `wg set` não sobrevive a reinício do wg-quick)."""
    state = load_state()
    for s in servers:
        if not s.get("local"):
            continue
        for key, address in state["devices"].get(s["id"], {}).items():
            try:
                wg_set_peer(key, address)
            except Exception as e:  # noqa: BLE001
                print(f"falha ao reaplicar peer {address}: {e}", file=sys.stderr)


class Handler(BaseHTTPRequestHandler):
    server_version = "meutunel-api"
    sys_version = ""

    def log_message(self, fmt, *args):
        sys.stderr.write("%s - %s\n" % (self.address_string(), fmt % args))

    def _send(self, code, body=None):
        data = b"" if body is None else json.dumps(body).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(data)

    def _authorized(self):
        header = self.headers.get("Authorization", "")
        supplied = header[7:] if header.startswith("Bearer ") else ""
        return bool(TOKEN) and hmac.compare_digest(supplied.encode(), TOKEN.encode())

    def do_GET(self):
        if self.path == "/healthz":
            return self._send(200, {"status": "ok"})
        if self.path != "/v1/servers":
            return self._send(404, {"error": "não encontrado"})
        if not self._authorized():
            return self._send(401, {"error": "não autorizado"})
        servers = [{k: v for k, v in s.items() if k not in PRIVATE_FIELDS} for s in load_servers()]
        # Servidores gerenciados por aqui não têm "address": o app o pede via /device.
        return self._send(200, {"servers": servers})

    def do_POST(self):
        m = re.match(r"^/v1/servers/([A-Za-z0-9_-]+)/device$", self.path)
        if not m:
            return self._send(404, {"error": "não encontrado"})
        if not self._authorized():
            return self._send(401, {"error": "não autorizado"})
        server = next((s for s in load_servers() if s["id"] == m.group(1)), None)
        if server is None:
            return self._send(404, {"error": "servidor desconhecido"})
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if not 0 < length <= 4096:
                raise ValueError
            key = json.loads(self.rfile.read(length)).get("publicKey")
        except (ValueError, AttributeError):
            return self._send(400, {"error": "corpo inválido"})
        if not valid_key(key):
            return self._send(400, {"error": "publicKey inválida"})
        if not server.get("local"):
            if "address" not in server:
                return self._send(409, {"error": "servidor sem gerenciamento local"})
            return self._send(200, {"address": server["address"]})
        try:
            return self._send(200, {"address": register(server, key)})
        except OverflowError as e:
            return self._send(409, {"error": str(e)})
        except Exception as e:  # noqa: BLE001
            print(f"erro ao cadastrar aparelho: {e}", file=sys.stderr)
            return self._send(500, {"error": "falha ao configurar o servidor"})


def main():
    if len(TOKEN) < 24:
        sys.exit("defina API_TOKEN com pelo menos 24 caracteres (ex.: openssl rand -hex 24)")
    servers = load_servers()
    reapply_peers(servers)
    print(f"ouvindo em {BIND}:{PORT} com {len(servers)} servidor(es)", file=sys.stderr)
    ThreadingHTTPServer((BIND, PORT), Handler).serve_forever()


if __name__ == "__main__":
    main()
