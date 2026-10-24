/** Injection tokens (strings, so no emitted type metadata is needed). */
export const CONFIG = 'PRICING_CONFIG';
export const LOGGER = 'PRICING_LOGGER';
export const GATEWAY_SECRET = 'PRICING_GATEWAY_SECRET';
export const MYSQL_POOL = 'PRICING_MYSQL_POOL';
export const CACHE_HANDLE = 'PRICING_CACHE_HANDLE';
/** repository + cache + logger (+ clock and id source) used by the use case. */
export const MANUAL_PRICE_DEPS = 'PRICING_MANUAL_PRICE_DEPS';
