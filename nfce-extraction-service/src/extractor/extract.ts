import { ExtractedInvoice, ExtractedInvoiceItem, ExtractedInvoicePayment } from '../types';

// Todo o corpo desta funcao precisa ser autocontido (sem referencias a modulos
// externos): ela e serializada via `Function.prototype.toString()` e reexecutada
// dentro da pagina por `page.evaluate` (Tarefa 2.0), que nao tem acesso ao escopo
// deste modulo Node — apenas ao que estiver escrito dentro da propria funcao. O
// parametro `doc` tem `document` como default para permitir a chamada sem
// argumentos a partir do `page.evaluate`, onde `document` resolve para o DOM da
// pagina; nos testes (Node/jsdom) o `doc` e sempre passado explicitamente.
export function extractDataFromRJ(): ExtractedInvoice;
export function extractDataFromRJ(doc: Document): ExtractedInvoice;
export function extractDataFromRJ(doc: Document = document): ExtractedInvoice {
  function toNumber(text: string): number {
    return parseFloat(text.replace(/\./g, '').replace(',', '.'));
  }

  function extractItems(): ExtractedInvoiceItem[] {
    const items: ExtractedInvoiceItem[] = [];

    doc.querySelectorAll('#tabResult tr[id^="Item"]').forEach((row) => {
      const td = row.querySelectorAll('td');
      const productName = td[0].querySelector('.txtTit')!.textContent!;
      const code = td[0].querySelector('.RCod')!.textContent!.replace('(Código:', '').replace(')', '').trim();
      const quantity = toNumber(td[0].querySelector('.Rqtd')!.textContent!.replace('Qtde.:', '').trim());
      const unit = td[0].querySelector('.RUN')!.textContent!.replace('UN:', '').trim();
      const unitPrice = toNumber(td[0].querySelector('.RvlUnit')!.textContent!.replace('Vl. Unit.:', '').trim());
      const totalPrice = toNumber(td[1].querySelector('.valor')!.textContent!);

      items.push({ productName, code, quantity, unit, unitPrice, totalPrice });
    });

    return items;
  }

  function extractTotals(): { totalItems: number; subtotal: number; discount: number; total: number; taxes: number } {
    const values = doc.querySelectorAll('#totalNota .totalNumb');
    const totalItems = toNumber(values[0].textContent!);

    const discountLabel = 'Descontos R$:';
    const discountElement = Array.from(doc.querySelectorAll('#totalNota label')).find(
      (element) => element.textContent!.trim() === discountLabel,
    );

    let subtotal = 0;
    let discount = 0;
    if (discountElement) {
      subtotal = toNumber(values[1].textContent!);
      discount = toNumber(discountElement.nextElementSibling!.textContent!);
    }

    const total = toNumber(doc.querySelector('#totalNota .totalNumb.txtMax')!.textContent!);

    const taxesElement = doc.querySelector('#totalNota .totalNumb.txtObs');
    let taxes = 0;
    if (taxesElement) {
      taxes = toNumber(taxesElement.textContent!);
      if (isNaN(taxes)) {
        taxes = 0;
      }
    }

    return { totalItems, subtotal, discount, total, taxes };
  }

  function extractPayments(): ExtractedInvoicePayment[] {
    const paymentValueElements = Array.from(doc.querySelectorAll('#linhaTotal span.totalNumb'));
    const totalIndex = paymentValueElements.findIndex((element) => element.classList.contains('txtMax'));
    const paymentMethodElements = Array.from(doc.querySelectorAll('#linhaTotal label.tx'));

    return paymentMethodElements.map((element, index) => ({
      type: element.textContent!.trim(),
      value: toNumber(paymentValueElements[totalIndex + index + 1].textContent!),
    }));
  }

  function extractDate(): string {
    const DATE_FIELD_LENGTH = '00/00/0000 00:00:00-00:00'.length;
    // outerText (usado no script original de WebView) nao existe em ambientes sem
    // layout como o jsdom; textContent nao colapsa espacos/quebras de linha como o
    // layout faria, entao normalizamos antes de comparar o rotulo.
    const label = Array.from(doc.querySelectorAll('strong')).find(
      (el) => el.textContent?.replace(/\s+/g, ' ').trim() === 'Emissão:',
    );
    const rawDate = label!.nextSibling!.textContent!.trim();
    return rawDate.substring(0, DATE_FIELD_LENGTH);
  }

  const merchantName = doc.querySelector('.txtTopo')!.textContent!.replace(/\s+/g, ' ').trim();
  const cnpj = doc.querySelector('.text')!.textContent!.replace('CNPJ:', '').replace(/\s+/g, '').trim();
  const merchantAddress = doc
    .querySelector('.text')!
    .nextElementSibling!.textContent!.replace(/\t/g, '')
    .replace(/\n/g, '')
    .replace(/\s+/g, ' ')
    .replace(/\s*,\s*/g, ', ')
    .trim();

  const items = extractItems();
  const { totalItems, subtotal, discount, total, taxes } = extractTotals();
  const payments = extractPayments();
  const date = extractDate();

  return {
    merchantName,
    cnpj,
    merchantAddress,
    totalItems,
    subtotal,
    discount,
    total,
    taxes,
    date,
    items,
    payments,
  };
}
