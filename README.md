# Roteador de IAs

Servidor que recebe uma pergunta, classifica (código, raciocínio, escrita, simples), escolhe a melhor IA disponível e faz fallback se uma falhar.

    export ANTHROPIC_API_KEY=... OPENAI_API_KEY=... GEMINI_API_KEY=...   # opcional
    npm start        # http://localhost:3000

- `POST /api/chat` `{ "messages": [{"role":"user","content":"..."}], "model": "claude-fast" (opcional) }`
- `GET /api/models` lista modelos ativos
- Regras de escolha: `router.js` (`PREFERENCE`). Modelos: `providers.js`.
- As chaves ficam só no servidor (nunca no front-end).
