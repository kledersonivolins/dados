#!/usr/bin/env sh
# Instala o mimic (https://github.com/littledivy/mimic) como ferramenta isolada via uv.
# Uso: sh scripts/install-mimic.sh
set -e

if ! command -v uv >/dev/null 2>&1; then
  echo "instalando uv…"
  curl -LsSf https://astral.sh/uv/install.sh | sh
  export PATH="$HOME/.local/bin:$PATH"
fi

uv tool install git+https://github.com/littledivy/mimic --reinstall

echo
echo "instalado. próximos passos:"
echo "  mimic doctor      # verifica o setup"
echo "  mimic record      # inicia a captura (siga os passos do iPhone)"
