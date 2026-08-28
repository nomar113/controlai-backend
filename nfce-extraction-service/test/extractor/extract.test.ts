import { readFileSync } from 'fs';
import { join } from 'path';
import { JSDOM } from 'jsdom';
import { extractDataFromRJ } from '../../src/extractor/extract';

function loadFixture(name: string): Document {
  const html = readFileSync(join(__dirname, '..', 'fixtures', name), 'utf-8');
  return new JSDOM(html).window.document;
}

describe('extractDataFromRJ', () => {
  it('extrai todos os campos da nota a partir da fixture de sucesso', () => {
    const result = extractDataFromRJ(loadFixture('rj-success.html'));

    expect(result).toEqual({
      merchantName: 'MERCADO EXEMPLO LTDA',
      cnpj: '12.345.678/0001-90',
      merchantAddress: 'Rua Exemplo, 123, Bairro Teste, Rio de Janeiro - RJ',
      totalItems: 2,
      subtotal: 30,
      discount: 5,
      total: 25,
      taxes: 3.5,
      date: '03/01/2024 10:15:00-03:00',
      items: [
        {
          productName: 'Produto Exemplo 1',
          code: '111',
          quantity: 2,
          unit: 'UN',
          unitPrice: 10,
          totalPrice: 20,
        },
        {
          productName: 'Produto Exemplo 2',
          code: '222',
          quantity: 1,
          unit: 'UN',
          unitPrice: 10,
          totalPrice: 10,
        },
      ],
      payments: [{ type: 'Dinheiro', value: 25 }],
    });
  });
});
