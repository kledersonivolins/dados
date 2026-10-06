// Roteador: classifica o pedido e devolve a lista de modelos em ordem de preferência.
import { MODELS, isEnabled } from './providers.js';

export function classify(text) {
  const t = text.toLowerCase();
  if (/```|function |class |def |import |bug|erro|stack ?trace|sql|select .* from|regex|código|codigo|script|api\b/.test(t)) return 'code';
  if (/calcul|equaç|equac|prove|demonstr|passo a passo|analis|compar|estrat|planej|raciocin/.test(t) || text.length > 1500) return 'reasoning';
  if (/escreva|redija|resuma|traduz|e-?mail|texto|proposta|relat[oó]rio/.test(t)) return 'writing';
  return 'simple';
}

const PREFERENCE = {
  code:      ['claude-smart', 'gpt-smart', 'claude-fast', 'gemini-fast'],
  reasoning: ['claude-best', 'claude-smart', 'gpt-smart', 'gemini-fast'],
  writing:   ['claude-smart', 'gpt-smart', 'gemini-fast', 'claude-fast'],
  simple:    ['claude-fast', 'gemini-fast', 'gpt-fast', 'claude-smart'],
};

export function plan(text, forced) {
  const category = classify(text);
  const order = forced ? [forced] : PREFERENCE[category];
  const chain = order.filter(isEnabled);
  if (!chain.length) chain.push('mock');
  return { category, chain };
}

// Executa com fallback: se um modelo falhar, tenta o próximo.
export async function route(messages, forced) {
  const last = messages[messages.length - 1].content;
  const { category, chain } = plan(last, forced);
  const errors = [];
  for (const id of chain) {
    try {
      const t0 = Date.now();
      const reply = await MODELS[id].call(messages);
      return { reply, model: id, label: MODELS[id].label, category, ms: Date.now() - t0, tried: errors };
    } catch (e) { errors.push({ model: id, error: e.message }); }
  }
  throw new Error('Todos os modelos falharam: ' + JSON.stringify(errors));
}
