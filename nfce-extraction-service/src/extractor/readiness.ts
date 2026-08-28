// A pagina da SEFAZ-RJ passa por reCAPTCHA v3 e um desafio anti-bot F5/TSPD que
// recarregam o documento varias vezes antes da nota renderizar — por isso a
// prontidao precisa ser verificada via polling, nunca por um delay fixo.
// `doc` tem `document` como default para permitir a chamada sem argumentos
// dentro do `page.evaluate` (Tarefa 2.0); nos testes (Node/jsdom) e sempre
// passado explicitamente.
export function isInvoiceReadyRJ(): boolean;
export function isInvoiceReadyRJ(doc: Document): boolean;
export function isInvoiceReadyRJ(doc: Document = document): boolean {
  return (
    !!doc.querySelector('.txtTopo') &&
    doc.querySelectorAll('#tabResult tr[id^="Item"]').length > 0 &&
    !!doc.querySelector('#totalNota .totalNumb.txtMax')
  );
}
