export const HEALTH_PATH = '/health';
export const MANUAL_PRICE_PATH = '/api/v1/prices/manual';
export const PUBLIC_PRICE_PATH = '/api/v1/prices/{commodity}/{province}';
export const PRICE_ITEMS_PATH = '/api/v1/prices/{commodity}/{province}/items';

/** Method each known path accepts: a request with another method is a 405, an unknown path a 404. */
export const ALLOWED_METHOD: Readonly<Record<string, string>> = {
  [HEALTH_PATH]: 'GET',
  [MANUAL_PRICE_PATH]: 'POST',
  [PUBLIC_PRICE_PATH]: 'GET',
  [PRICE_ITEMS_PATH]: 'GET',
};

export function allowedMethod(path: string): string | undefined {
  const exact = ALLOWED_METHOD[path];
  if (exact) return exact;
  if (/^\/api\/v1\/prices\/[^/]+\/[^/]+(?:\/items)?$/.test(path)) return 'GET';
  return undefined;
}
