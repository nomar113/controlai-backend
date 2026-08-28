import { readFileSync } from 'fs';
import { join } from 'path';
import { JSDOM } from 'jsdom';
import { getBlockMessageRJ } from '../../src/extractor/block-detection';

function loadFixture(name: string): Document {
  const html = readFileSync(join(__dirname, '..', 'fixtures', name), 'utf-8');
  return new JSDOM(html).window.document;
}

describe('getBlockMessageRJ', () => {
  it('retorna null quando a nota esta pronta (sem bloqueio)', () => {
    expect(getBlockMessageRJ(loadFixture('rj-success.html'))).toBeNull();
  });

  it('retorna a mensagem do elemento .avisoErro quando presente', () => {
    expect(getBlockMessageRJ(loadFixture('rj-blocked.html'))).toBe('Não foi possível validar o acesso.');
  });

  it('retorna o texto do corpo quando um indicador de erro e encontrado sem .avisoErro', () => {
    const message = getBlockMessageRJ(loadFixture('rj-blocked-keyword.html'));
    expect(message).not.toBeNull();
    expect(message!.toLowerCase()).toContain('nossos serviços de segurança da informação');
  });

  it('retorna null enquanto a nota ainda esta carregando (sem indicador de erro)', () => {
    expect(getBlockMessageRJ(loadFixture('rj-loading.html'))).toBeNull();
  });
});
