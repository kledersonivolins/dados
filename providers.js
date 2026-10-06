// Provedores de IA. Só ficam ativos os que têm chave em variável de ambiente.
// Os modelos podem ser trocados por env (ex.: OPENAI_MODEL_SMART=...).
const env = (k, d) => process.env[k] || d;

async function post(url, headers, body) {
  const r = await fetch(url, { method: 'POST', headers: { 'content-type': 'application/json', ...headers }, body: JSON.stringify(body) });
  const data = await r.json().catch(() => ({}));
  if (!r.ok) throw new Error(`${r.status} ${JSON.stringify(data).slice(0, 200)}`);
  return data;
}

const anthropic = (model) => async (messages) => {
  const d = await post('https://api.anthropic.com/v1/messages',
    { 'x-api-key': process.env.ANTHROPIC_API_KEY, 'anthropic-version': '2023-06-01' },
    { model, max_tokens: 2048, messages });
  return d.content.map((c) => c.text || '').join('');
};

const openai = (model) => async (messages) => {
  const d = await post('https://api.openai.com/v1/chat/completions',
    { authorization: `Bearer ${process.env.OPENAI_API_KEY}` }, { model, messages });
  return d.choices[0].message.content;
};

const gemini = (model) => async (messages) => {
  const d = await post(
    `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${process.env.GEMINI_API_KEY}`, {},
    { contents: messages.map((m) => ({ role: m.role === 'assistant' ? 'model' : 'user', parts: [{ text: m.content }] })) });
  return d.candidates[0].content.parts.map((p) => p.text || '').join('');
};

// Catálogo: id -> { enabled, call, label }
export const MODELS = {
  'claude-fast':  { key: 'ANTHROPIC_API_KEY', label: 'Claude Haiku',  call: anthropic(env('ANTHROPIC_MODEL_FAST', 'claude-haiku-4-5-20251001')) },
  'claude-smart': { key: 'ANTHROPIC_API_KEY', label: 'Claude Sonnet', call: anthropic(env('ANTHROPIC_MODEL_SMART', 'claude-sonnet-5-5')) },
  'claude-best':  { key: 'ANTHROPIC_API_KEY', label: 'Claude Opus',   call: anthropic(env('ANTHROPIC_MODEL_BEST', 'claude-opus-5-5')) },
  'gpt-fast':     { key: 'OPENAI_API_KEY',    label: 'OpenAI mini',   call: openai(env('OPENAI_MODEL_FAST', 'gpt-4o-mini')) },
  'gpt-smart':    { key: 'OPENAI_API_KEY',    label: 'OpenAI',        call: openai(env('OPENAI_MODEL_SMART', 'gpt-4o')) },
  'gemini-fast':  { key: 'GEMINI_API_KEY',    label: 'Gemini Flash',  call: gemini(env('GEMINI_MODEL_FAST', 'gemini-2.0-flash')) },
  // Modo demonstração: sempre disponível, sem chave.
  'mock':         { key: null, label: 'Demo (sem chave)', call: async (m) => `[demo] Recebi: "${m[m.length - 1].content.slice(0, 80)}". Configure uma chave de API para respostas reais.` },
};

export const isEnabled = (id) => { const m = MODELS[id]; return m && (!m.key || process.env[m.key]); };
