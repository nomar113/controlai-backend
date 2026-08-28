import { createServer, Server } from 'http';
import { readFileSync } from 'fs';
import { join } from 'path';

const ROUTES: Record<string, string> = {
  '/success': 'rj-success.html',
  '/blocked': 'rj-blocked.html',
  '/loading': 'rj-loading.html',
};

export interface FixtureServer {
  url: string;
  close: () => Promise<void>;
}

const RELOAD_THRESHOLD = 3;

function handler(server: Server): Server {
  let reloadRequestCount = 0;

  server.on('request', (req, res) => {
    if (req.url === '/reloading') {
      reloadRequestCount += 1;
      res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
      if (reloadRequestCount < RELOAD_THRESHOLD) {
        // Simula o desafio anti-bot da SEFAZ-RJ recarregando o documento
        // varias vezes antes de finalmente servir a nota pronta.
        res.end('<!DOCTYPE html><html><body><script>setTimeout(() => location.reload(), 150);</script>Verificando acesso...</body></html>');
        return;
      }
      res.end(readFileSync(join(__dirname, 'rj-success.html'), 'utf-8'));
      return;
    }

    const fixtureFile = req.url ? ROUTES[req.url] : undefined;
    if (!fixtureFile) {
      res.writeHead(404);
      res.end('Not found');
      return;
    }

    const html = readFileSync(join(__dirname, fixtureFile), 'utf-8');
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
    res.end(html);
  });
  return server;
}

export function startFixtureServer(): Promise<FixtureServer> {
  return new Promise((resolve, reject) => {
    const server = handler(createServer());

    server.listen(0, '127.0.0.1', () => {
      const address = server.address();
      if (address === null || typeof address === 'string') {
        reject(new Error('Falha ao iniciar servidor de fixtures'));
        return;
      }

      resolve({
        url: `http://127.0.0.1:${address.port}`,
        close: () => new Promise((res) => server.close(() => res())),
      });
    });
  });
}

/**
 * Reserva uma porta livre e a libera imediatamente, para uso em testes que
 * precisam de um endereco com conexao garantidamente recusada (simulando o
 * `nfce-extraction-service`/SEFAZ fora do ar).
 */
export function findClosedPortUrl(): Promise<string> {
  return new Promise((resolve, reject) => {
    const server = createServer();
    server.listen(0, '127.0.0.1', () => {
      const address = server.address();
      server.close(() => {
        if (address === null || typeof address === 'string') {
          reject(new Error('Falha ao reservar porta livre'));
          return;
        }
        resolve(`http://127.0.0.1:${address.port}`);
      });
    });
  });
}
