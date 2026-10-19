export const HEALTH_PATH = '/health';
export const MANUAL_PRICE_PATH = '/api/v1/prices/manual';

/** Method each known path accepts: a request with another method is a 405, an unknown path a 404. */
export const ALLOWED_METHOD: Readonly<Record<string, string>> = {
  [HEALTH_PATH]: 'GET',
  [MANUAL_PRICE_PATH]: 'POST',
};
