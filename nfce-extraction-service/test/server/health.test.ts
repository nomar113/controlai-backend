import request from 'supertest';
import { createServer } from '../../src/server';

describe('GET /health', () => {
  it('retorna 200 sem exigir autenticacao', async () => {
    const app = createServer();

    const response = await request(app).get('/health');

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ status: 'ok' });
  });
});
