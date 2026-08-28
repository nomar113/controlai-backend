// Autocontida por ser serializada via `page.evaluate` (ver comentario em
// extract.ts). `doc` tem `document` como default para permitir a chamada sem
// argumentos dentro da pagina.
export function getBlockMessageRJ(): string | null;
export function getBlockMessageRJ(doc: Document): string | null;
export function getBlockMessageRJ(doc: Document = document): string | null {
  const MAX_BLOCK_MESSAGE_LENGTH = 200;
  const ERROR_INDICATORS = [
    'não foi possível localizar a nf-e',
    'não se refere a um documento fiscal eletrônico',
    'página da web não disponível',
    'erro na consulta',
    'serviço indisponível',
    'erro interno do servidor',
    'tempo limite excedido',
    'conexão recusada',
    'acesso negado',
    'nossos serviços de segurança da informação',
  ];

  const el = doc.querySelector('.avisoErro');
  if (el) {
    return (el.textContent ?? '').replace(/\s+/g, ' ').trim();
  }

  const bodyText = (doc.body?.textContent ?? '').replace(/\s+/g, ' ').trim();
  if (bodyText.length === 0) {
    return null;
  }

  const normalized = bodyText.toLowerCase();
  const matched = ERROR_INDICATORS.find((indicator) => normalized.includes(indicator));
  return matched ? bodyText.slice(0, MAX_BLOCK_MESSAGE_LENGTH) : null;
}
