// The SEFAZ-RJ page goes through reCAPTCHA v3 and an F5/TSPD anti-bot challenge
// that reloads the document several times before the invoice renders — that's
// why readiness must be checked via polling, never a fixed delay. `doc`
// defaults to `document` so it can be called without arguments inside
// `page.evaluate`; in tests (Node/jsdom) it's always passed explicitly.
export function isInvoiceReadyRJ(): boolean;
export function isInvoiceReadyRJ(doc: Document): boolean;
export function isInvoiceReadyRJ(doc: Document = document): boolean {
  return (
    !!doc.querySelector('.txtTopo') &&
    doc.querySelectorAll('#tabResult tr[id^="Item"]').length > 0 &&
    !!doc.querySelector('#totalNota .totalNumb.txtMax')
  );
}
