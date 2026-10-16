/**
 * Time budget of one POST /internal/v1/notifications/otp-email request, measured from when the
 * request reaches the service.
 *
 * The caller is contract-service. Its Feign default is readTimeout 5000 ms
 * (contract-service application.yml, feign.client.config.default). Every step below is bounded,
 * and the worst-case sending path is
 *
 *   reserve 750 + SMTP 2500 + markSent 750 = 4000 ms  <=  requestBudgetMs 4500 ms
 *
 * which leaves at least 500 ms for HTTP parsing, the response and the network before the
 * caller gives up. Nothing is sent once there is not enough budget left to also record SENT.
 */
export interface OtpTimeBudget {
  /** Reference value only: the caller's read timeout this budget must stay under. */
  callerTimeoutMs: number;
  /** Hard end of the request, counted from arrival. */
  requestBudgetMs: number;
  /** Cap on each database call, including waiting for a pool connection. */
  dbOperationTimeoutMs: number;
  /** Cap on the whole SMTP exchange. */
  smtpBudgetMs: number;
  /** Kept free after SMTP so markSent can run before the request deadline. */
  postSendReserveMs: number;
}

export const OTP_TIME_BUDGET: OtpTimeBudget = {
  callerTimeoutMs: 5000,
  requestBudgetMs: 4500,
  dbOperationTimeoutMs: 750,
  smtpBudgetMs: 2500,
  postSendReserveMs: 750,
};

/** Milliseconds until `deadlineAt`, never negative. */
export function remainingMs(deadlineAt: number, nowMs: number): number {
  return Math.max(0, deadlineAt - nowMs);
}
