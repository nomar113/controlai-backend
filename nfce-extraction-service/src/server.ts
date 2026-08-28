import express, { Express, NextFunction, Request, Response } from 'express';
import { internalAuth } from './middleware/internal-auth';
import { extractRouter } from './routes/extract';
import { healthRouter } from './routes/health';

export function createServer(): Express {
  const app = express();
  app.use(express.json());

  // Normaliza erro de parsing de JSON malformado para o mesmo formato
  // { error } do resto da API, em vez da pagina de erro padrao do Express.
  app.use((err: unknown, _req: Request, res: Response, next: NextFunction) => {
    if (err instanceof SyntaxError && 'body' in err) {
      res.status(400).json({ error: 'JSON invalido' });
      return;
    }
    next(err);
  });

  app.use(healthRouter);
  app.use(internalAuth, extractRouter);

  return app;
}
