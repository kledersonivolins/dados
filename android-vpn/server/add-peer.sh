#!/usr/bin/env bash
# Autoriza um aparelho no servidor.
# Uso (como root): bash add-peer.sh <chave-publica-do-app> <ip-na-vpn>
# Ex.: bash add-peer.sh 3k2...= 10.8.0.2
set -euo pipefail

[ "$(id -u)" -eq 0 ] || { echo "rode como root" >&2; exit 1; }
[ $# -eq 2 ] || { echo "uso: $0 <chave-publica> <ip>" >&2; exit 1; }

PUB="$1"
IP="$2"

wg set wg0 peer "$PUB" allowed-ips "${IP}/32"

# Persiste para sobreviver a reinício.
if ! grep -qF "$PUB" /etc/wireguard/wg0.conf; then
  printf '\n[Peer]\nPublicKey = %s\nAllowedIPs = %s/32\n' "$PUB" "$IP" >> /etc/wireguard/wg0.conf
fi

echo "peer ${IP} autorizado."
