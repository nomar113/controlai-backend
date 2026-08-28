import { closeBrowser, getOpenContextCount } from '../../src/browser/browser-manager';
import { extractInvoice } from '../../src/extractor/orchestrator';
import { findClosedPortUrl, FixtureServer, startFixtureServer } from '../fixtures/server';

jest.setTimeout(30_000);

describe('extractInvoice', () => {
  let server: FixtureServer;

  beforeAll(async () => {
    server = await startFixtureServer();
  });

  afterAll(async () => {
    await server.close();
    await closeBrowser();
  });

  afterEach(() => {
    expect(getOpenContextCount()).toBe(0);
  });

  it('retorna READY com os dados extraidos quando a nota esta pronta', async () => {
    const result = await extractInvoice(`${server.url}/success`);

    expect(result.status).toBe('READY');
    expect(result.data?.merchantName).toBe('MERCADO EXEMPLO LTDA');
    expect(result.data?.total).toBe(25);
  });

  it('retorna BLOCKED com a mensagem quando a SEFAZ bloqueia o acesso', async () => {
    const result = await extractInvoice(`${server.url}/blocked`);

    expect(result.status).toBe('BLOCKED');
    expect(result.message).toBe('Não foi possível validar o acesso.');
  });

  it('retorna TIMEOUT quando a nota nunca fica pronta dentro do limite', async () => {
    process.env.EXTRACTION_TIMEOUT_MS = '1500';
    try {
      const result = await extractInvoice(`${server.url}/loading`);
      expect(result.status).toBe('TIMEOUT');
    } finally {
      delete process.env.EXTRACTION_TIMEOUT_MS;
    }
  });

  it('retorna READY mesmo quando a pagina recarrega varias vezes durante o polling', async () => {
    const result = await extractInvoice(`${server.url}/reloading`);

    expect(result.status).toBe('READY');
    expect(result.data?.merchantName).toBe('MERCADO EXEMPLO LTDA');
  });

  it('retorna NAVIGATION_ERROR quando o servidor esta indisponivel', async () => {
    const unreachableUrl = await findClosedPortUrl();

    const result = await extractInvoice(unreachableUrl);

    expect(result.status).toBe('NAVIGATION_ERROR');
  });
});
