import type { Page } from 'playwright';
import { withBrowserContext } from '../browser/browser-manager';
import { ExtractionResult } from '../types';
import { getBlockMessageRJ } from './block-detection';
import { extractDataFromRJ } from './extract';
import { isInvoiceReadyRJ } from './readiness';

// Mesma ordem de grandeza do timeout ja usado no fluxo client-side
// (PAGE_LOAD_TIMEOUT_MS em InvoiceProcessingContext.tsx).
const DEFAULT_TIMEOUT_MS = 60_000;
const POLL_INTERVAL_MS = 500;

function getTimeoutMs(): number {
  const configured = Number(process.env.EXTRACTION_TIMEOUT_MS);
  return Number.isFinite(configured) && configured > 0 ? configured : DEFAULT_TIMEOUT_MS;
}

// A pagina real da SEFAZ-RJ recarrega o documento varias vezes durante o
// desafio anti-bot (reCAPTCHA v3/F5-TSPD, ver comentario em readiness.ts).
// Se um reload acontecer entre o inicio e o fim de um `page.evaluate`, o
// Playwright rejeita a chamada com um erro de contexto de execucao
// destruido/frame destacado — isso NAO e uma falha real, e sim o sinal de
// que a pagina ainda esta no meio do desafio, entao tratamos como "ainda nao
// pronto" e continuamos o polling ate o deadline.
function isPageReloadInterruption(error: unknown): boolean {
  const message = error instanceof Error ? error.message : String(error);
  return (
    message.includes('Execution context was destroyed') ||
    message.includes('Frame was detached') ||
    message.includes('Target closed')
  );
}

async function pollForReadyOrBlocked(page: Page, deadline: number): Promise<ExtractionResult> {
  while (Date.now() < deadline) {
    try {
      const blockMessage = await page.evaluate(getBlockMessageRJ);
      if (blockMessage) {
        return { status: 'BLOCKED', message: blockMessage };
      }

      const ready = await page.evaluate(isInvoiceReadyRJ);
      if (ready) {
        const data = await page.evaluate(extractDataFromRJ);
        return { status: 'READY', data };
      }
    } catch (error) {
      if (!isPageReloadInterruption(error)) {
        throw error;
      }
    }

    await page.waitForTimeout(POLL_INTERVAL_MS);
  }

  return { status: 'TIMEOUT', message: 'Tempo esgotado ao consultar a SEFAZ' };
}

export async function extractInvoice(invoiceUrl: string): Promise<ExtractionResult> {
  const timeoutMs = getTimeoutMs();
  const deadline = Date.now() + timeoutMs;

  return withBrowserContext(async (context) => {
    const page = await context.newPage();

    try {
      await page.goto(invoiceUrl, { waitUntil: 'domcontentloaded', timeout: timeoutMs });
    } catch {
      return { status: 'NAVIGATION_ERROR', message: 'Não foi possível consultar a SEFAZ' };
    }

    return pollForReadyOrBlocked(page, deadline);
  });
}
