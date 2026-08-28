import { closeBrowser } from './browser/browser-manager';
import { logger } from './logger';
import { createServer } from './server';

const PORT = Number(process.env.PORT) || 3000;
const HOST = process.env.HOST || '0.0.0.0';

const app = createServer();

const httpServer = app.listen(PORT, HOST, () => {
  logger.info({ port: PORT, host: HOST }, 'nfce_extraction_service_started');
});

async function shutdown(signal: string): Promise<void> {
  logger.info({ signal }, 'nfce_extraction_service_shutting_down');
  httpServer.close();
  await closeBrowser();
  process.exit(0);
}

process.on('SIGTERM', () => void shutdown('SIGTERM'));
process.on('SIGINT', () => void shutdown('SIGINT'));
