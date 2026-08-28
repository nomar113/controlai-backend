import { Router } from 'express';
import { extractInvoice } from '../extractor/orchestrator';
import { logger } from '../logger';

export const extractRouter = Router();

function isValidBody(body: unknown): body is { invoiceUrl: string } {
  return (
    typeof body === 'object' &&
    body !== null &&
    typeof (body as Record<string, unknown>).invoiceUrl === 'string' &&
    (body as Record<string, unknown>).invoiceUrl !== ''
  );
}

extractRouter.post('/extract', async (req, res) => {
  if (!isValidBody(req.body)) {
    res.status(400).json({ error: 'invoiceUrl e obrigatorio e deve ser uma string' });
    return;
  }

  const startedAt = Date.now();

  try {
    const result = await extractInvoice(req.body.invoiceUrl);
    const durationMs = Date.now() - startedAt;

    // Structured log: only the final status and duration, never the page HTML
    // or personal data from the invoice (consumer CPF/CNPJ, address).
    logger.info({ status: result.status, durationMs }, 'nfce_extraction_completed');

    res.status(200).json(result);
  } catch (error) {
    const durationMs = Date.now() - startedAt;
    logger.error({ status: 'ERROR', durationMs, error: error instanceof Error ? error.message : String(error) }, 'nfce_extraction_failed');
    res.status(500).json({ error: 'Falha inesperada ao processar a nota' });
  }
});
