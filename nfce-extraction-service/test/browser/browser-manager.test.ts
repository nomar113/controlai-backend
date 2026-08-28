import { closeBrowser, getOpenContextCount, withBrowserContext } from '../../src/browser/browser-manager';

jest.setTimeout(30_000);

describe('browser-manager', () => {
  afterAll(async () => {
    await closeBrowser();
  });

  it('cria e fecha o contexto ao final de uma execucao bem sucedida', async () => {
    expect(getOpenContextCount()).toBe(0);

    await withBrowserContext(async (context) => {
      expect(getOpenContextCount()).toBe(1);
      expect(context).toBeDefined();
    });

    expect(getOpenContextCount()).toBe(0);
  });

  it('fecha o contexto mesmo quando a funcao executada lanca erro', async () => {
    await expect(
      withBrowserContext(async () => {
        throw new Error('falha simulada');
      }),
    ).rejects.toThrow('falha simulada');

    expect(getOpenContextCount()).toBe(0);
  });

  it('reutiliza o mesmo browser entre chamadas sucessivas', async () => {
    await withBrowserContext(async () => undefined);
    await withBrowserContext(async () => undefined);
    expect(getOpenContextCount()).toBe(0);
  });
});
