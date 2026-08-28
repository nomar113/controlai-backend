import { timingSafeEqual } from 'crypto';
import type { NextFunction, Request, Response } from 'express';

function isValidKey(expected: string, provided: string): boolean {
  const expectedBuffer = Buffer.from(expected);
  const providedBuffer = Buffer.from(provided);
  return expectedBuffer.length === providedBuffer.length && timingSafeEqual(expectedBuffer, providedBuffer);
}

// A missing X_INTERNAL_KEY is never treated as "no auth required" (fail-closed):
// without the environment variable, every request is rejected.
export function internalAuth(req: Request, res: Response, next: NextFunction): void {
  const expectedKey = process.env.X_INTERNAL_KEY;
  const providedKey = req.header('X-Internal-Key');

  if (!expectedKey || !providedKey || !isValidKey(expectedKey, providedKey)) {
    res.status(401).json({ error: 'Unauthorized' });
    return;
  }

  next();
}
