#!/usr/bin/env bash
# Instala e configura um servidor WireGuard em Ubuntu/Debian.
# Uso (como root): bash setup-wireguard.sh
# Variáveis opcionais: WG_PORT (padrão 51820), WG_SUBNET (padrão 10.8.0)
set -euo pipefail

[ "$(id -u)" -eq 0 ] || { echo "rode como root" >&2; exit 1; }

PORT="${WG_PORT:-51820}"
SUBNET="${WG_SUBNET:-10.8.0}"

apt-get update -qq
apt-get install -y -qq wireguard iptables

umask 077
mkdir -p /etc/wireguard
if [ ! -f /etc/wireguard/server.key ]; then
  wg genkey | tee /etc/wireguard/server.key | wg pubkey > /etc/wireguard/server.pub
fi

WAN="$(ip route get 1.1.1.1 | awk '{for(i=1;i<=NF;i++) if($i=="dev"){print $(i+1); exit}}')"

# Não sobrescreve uma configuração existente (preserva os peers já cadastrados).
if [ ! -f /etc/wireguard/wg0.conf ]; then
  cat > /etc/wireguard/wg0.conf <<CONF
[Interface]
Address = ${SUBNET}.1/24
ListenPort = ${PORT}
PrivateKey = $(cat /etc/wireguard/server.key)
PostUp = iptables -A FORWARD -i %i -j ACCEPT; iptables -A FORWARD -o %i -j ACCEPT; iptables -t nat -A POSTROUTING -o ${WAN} -j MASQUERADE
PostDown = iptables -D FORWARD -i %i -j ACCEPT; iptables -D FORWARD -o %i -j ACCEPT; iptables -t nat -D POSTROUTING -o ${WAN} -j MASQUERADE
CONF
fi

echo 'net.ipv4.ip_forward=1' > /etc/sysctl.d/99-wireguard.conf
sysctl -q -p /etc/sysctl.d/99-wireguard.conf

systemctl enable --now wg-quick@wg0

echo
echo "Servidor WireGuard no ar (interface de saída: ${WAN})."
echo "Chave pública do servidor (cole em servers.json -> publicKey):"
cat /etc/wireguard/server.pub
echo "Lembre de liberar a porta UDP ${PORT} no firewall da VPS."
echo "Próximo passo: bash add-peer.sh <chave-publica-do-app> ${SUBNET}.2"
