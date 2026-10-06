import http from 'node:http';
import { readFile } from 'node:fs/promises';
import { route } from './router.js';
import { MODELS, isEnabled } from './providers.js';

const PORT = process.env.PORT || 3000;

const readBody = (req) => new Promise((res, rej) => {
  let b = ''; req.on('data', (c) => { b += c; if (b.length > 1e6) req.destroy(); });
  req.on('end', () => { try { res(JSON.parse(b || '{}')); } catch (e) { rej(e); } });
});
const json = (res, code, obj) => { res.writeHead(code, { 'content-type': 'application/json' }); res.end(JSON.stringify(obj)); };

http.createServer(async (req, res) => {
  try {
    if (req.method === 'GET' && req.url === '/api/models')
      return json(res, 200, Object.entries(MODELS).filter(([id]) => isEnabled(id)).map(([id, m]) => ({ id, label: m.label })));
    if (req.method === 'POST' && req.url === '/api/chat') {
      const { messages, model } = await readBody(req);
      if (!Array.isArray(messages) || !messages.length) return json(res, 400, { error: 'messages obrigatório' });
      return json(res, 200, await route(messages, model || undefined));
    }
    if (req.method === 'GET' && (req.url === '/' || req.url === '/index.html')) {
      res.writeHead(200, { 'content-type': 'text/html; charset=utf-8' });
      return res.end(await readFile(new URL('./public/index.html', import.meta.url)));
    }
    json(res, 404, { error: 'not found' });
  } catch (e) { json(res, 500, { error: e.message }); }
}).listen(PORT, () => console.log(`Servidor de IAs em http://localhost:${PORT}`));
