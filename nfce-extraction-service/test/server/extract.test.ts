import request from 'supertest';
import { closeBrowser, getOpenContextCount } from '../../src/browser/browser-manager';
import { createServer } from '../../src/server';
import { FixtureServer, startFixtureServer } from '../fixtures/server';

jest.setTimeout(30_000);

const TEST_KEY = 'test-internal-key';

describe('POST /extract', () => {
  const app = createServer();
  let fixtureServer: FixtureServer;

  beforeAll(async () => {
    process.env.X_INTERNAL_KEY = TEST_KEY;
    fixtureServer = await startFixtureServer();
  });

  afterAll(async () => {
    delete process.env.X_INTERNAL_KEY;
    await fixtureServer.close();
    await closeBrowser();
  });

  afterEach(() => {
    expect(getOpenContextCount()).toBe(0);
  });

  it('retorna 401 sem o header X-Internal-Key', async () => {
    const response = await request(app)
      .post('/extract')
      .send({ invoiceUrl: `${fixtureServer.url}/success` });

    expect(response.status).toBe(401);
  });

  it('retorna 401 com header incorreto', async () => {
    const response = await request(app)
      .post('/extract')
      .set('X-Internal-Key', 'chave-errada')
      .send({ invoiceUrl: `${fixtureServer.url}/success` });

    expect(response.status).toBe(401);
  });

  it('retorna 400 quando invoiceUrl esta ausente', async () => {
    const response = await request(app).post('/extract').set('X-Internal-Key', TEST_KEY).send({});

    expect(response.status).toBe(400);
  });

  it('retorna 400 quando invoiceUrl nao e string', async () => {
    const response = await request(app)
      .post('/extract')
      .set('X-Internal-Key', TEST_KEY)
      .send({ invoiceUrl: 123 });

    expect(response.status).toBe(400);
  });

  it('retorna 200 com status READY e os dados extraidos', async () => {
    const response = await request(app)
      .post('/extract')
      .set('X-Internal-Key', TEST_KEY)
      .send({ invoiceUrl: `${fixtureServer.url}/success` });

    expect(response.status).toBe(200);
    expect(response.body.status).toBe('READY');
    expect(response.body.data.merchantName).toBe('MERCADO EXEMPLO LTDA');
  });

  it('retorna 200 com status BLOCKED quando a SEFAZ bloqueia', async () => {
    const response = await request(app)
      .post('/extract')
      .set('X-Internal-Key', TEST_KEY)
      .send({ invoiceUrl: `${fixtureServer.url}/blocked` });

    expect(response.status).toBe(200);
    expect(response.body.status).toBe('BLOCKED');
    expect(response.body.message).toBe('Não foi possível validar o acesso.');
  });

  it('retorna 200 com status TIMEOUT quando a nota nunca fica pronta', async () => {
    process.env.EXTRACTION_TIMEOUT_MS = '1500';
    try {
      const response = await request(app)
        .post('/extract')
        .set('X-Internal-Key', TEST_KEY)
        .send({ invoiceUrl: `${fixtureServer.url}/loading` });

      expect(response.status).toBe(200);
      expect(response.body.status).toBe('TIMEOUT');
    } finally {
      delete process.env.EXTRACTION_TIMEOUT_MS;
    }
  });
});
