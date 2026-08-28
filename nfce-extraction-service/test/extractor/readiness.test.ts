import { readFileSync } from 'fs';
import { join } from 'path';
import { JSDOM } from 'jsdom';
import { isInvoiceReadyRJ } from '../../src/extractor/readiness';

function loadFixture(name: string): Document {
  const html = readFileSync(join(__dirname, '..', 'fixtures', name), 'utf-8');
  return new JSDOM(html).window.document;
}

describe('isInvoiceReadyRJ', () => {
  it('retorna true quando a nota esta pronta (sucesso)', () => {
    expect(isInvoiceReadyRJ(loadFixture('rj-success.html'))).toBe(true);
  });

  it('retorna false quando a nota foi bloqueada pela SEFAZ', () => {
    expect(isInvoiceReadyRJ(loadFixture('rj-blocked.html'))).toBe(false);
  });

  it('retorna false enquanto a nota ainda esta carregando', () => {
    expect(isInvoiceReadyRJ(loadFixture('rj-loading.html'))).toBe(false);
  });
});
