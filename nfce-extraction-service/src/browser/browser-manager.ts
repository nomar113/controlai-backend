import type { Browser, BrowserContext } from 'playwright';
import { chromium } from 'playwright-extra';
import StealthPlugin from 'puppeteer-extra-plugin-stealth';

chromium.use(StealthPlugin());

let browserPromise: Promise<Browser> | null = null;
let openContextCount = 0;

async function getBrowser(): Promise<Browser> {
  if (!browserPromise) {
    browserPromise = chromium.launch({ headless: true });
  }
  return browserPromise;
}

export async function withBrowserContext<T>(run: (context: BrowserContext) => Promise<T>): Promise<T> {
  const browser = await getBrowser();
  const context = await browser.newContext();
  openContextCount += 1;

  try {
    return await run(context);
  } finally {
    await context.close();
    openContextCount -= 1;
  }
}

export function getOpenContextCount(): number {
  return openContextCount;
}

export async function closeBrowser(): Promise<void> {
  if (!browserPromise) {
    return;
  }
  const browser = await browserPromise;
  browserPromise = null;
  await browser.close();
}
