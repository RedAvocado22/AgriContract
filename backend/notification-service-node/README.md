# notification-service (Node.js)

Node.js/TypeScript rewrite of `backend/notification-service` (ADR 0006,
`docs/planning/notification-service-node-rewrite-plan.md`). Until the N6 cutover it runs only in
tests and locally against its own database `notification_node_db`. It must never share a queue
or a database with the Java service.

Implemented so far:

- N1/N2: scaffold, `GET /health`, `notification_logs` migrations V1–V3, `NotificationLogRepository`.
- N3 (#114): synchronous OTP email endpoint, described below.

RabbitMQ consumers (N4/N5) are not implemented yet.

## OTP email endpoint

```
POST /internal/v1/notifications/otp-email
X-Internal-Secret: <SERVICE_INTERNAL_SECRET>
Content-Type: application/json
```

Called by contract-service only, from `InitiateSign` (signature design §6, step 4). Never route
it through the API Gateway.

### Authentication

`X-Internal-Secret` must equal `SERVICE_INTERNAL_SECRET`. The header is the one the Java services
already use for service-to-service calls (contract-service `FeignConfig`, user/product-service
`HeaderAuthenticationFilter`). Both values are SHA-256 hashed and compared with
`crypto.timingSafeEqual`, so a value of any length is safe. A missing, empty or wrong value
returns `401` before the body is read. The service does not start without
`SERVICE_INTERNAL_SECRET`.

This is the contract's `serviceAuth` scheme, which covers every `x-internal` endpoint. It is not
`X-Gateway-Secret`: that header is set only by the API Gateway on routed requests, and internal
endpoints are never routed through the Gateway.

### Request

Schema `OtpEmailRequest` from `contracts/openapi/golden-flow-api.yaml`, copied to
`src/contracts/generated/` by `npm run contracts:sync`. `npm run contracts:check` fails when the
copy no longer matches the contract.

| Field               | Rule                                                                                |
| ------------------- | ----------------------------------------------------------------------------------- |
| `requestId`         | string, the contract-service `otpId`; 1–36 characters (`event_id` is `VARCHAR(36)`) |
| `recipientUserId`   | string, 1–255 characters; stored in `user_id`                                       |
| `recipientEmail`    | `format: email`, at most 255 characters                                             |
| `contractId`        | string                                                                              |
| `otpCode`           | `^[0-9]{6}$`                                                                        |
| `signedContentHash` | `^[a-f0-9]{64}$` (lower-case hex, as the contract says)                             |
| `expiresAt`         | RFC 3339 `date-time` with a zone, strictly in the future                            |

All seven fields are required. Unknown fields are rejected (`additionalProperties: false`), and
types are not coerced. The length limits are in the contract schema and match the
`notification_logs` column sizes. The future-`expiresAt` rule is the contract's `x-validation`
note, since JSON Schema cannot compare against the current time; the route checks it.

### Responses

| Status | When                                                                                                         | Body                                                             |
| ------ | ------------------------------------------------------------------------------------------------------------ | ---------------------------------------------------------------- |
| `200`  | Provider accepted the mail now, or earlier for the same key                                                  | empty (the contract defines no body)                             |
| `400`  | Invalid JSON, schema violation, `expiresAt` not in the future, body over 4096 bytes, `Content-Type` not JSON | `ErrorEnvelope`, `code: VALIDATION_ERROR` or `MALFORMED_REQUEST` |
| `401`  | Missing or wrong `X-Internal-Secret`                                                                         | `ErrorEnvelope`, `code: UNAUTHORIZED`                            |
| `502`  | Not confirmed as sent, see the codes below                                                                   | `ErrorEnvelope`, `code: OTP_EMAIL_*`                             |
| `500`  | Database error (not a timeout) before anything was sent                                                      | `ErrorEnvelope`, `code: INTERNAL_ERROR`                          |

`ErrorEnvelope` is the contract's shape: `timestamp, status, error, code, message, path, traceId`.
Error messages never quote the request body.

| `502` code                      | Meaning                                                           | Could the mail arrive?  |
| ------------------------------- | ----------------------------------------------------------------- | ----------------------- |
| `OTP_EMAIL_PREVIOUSLY_FAILED`   | An earlier request with this key failed                           | no (not from this call) |
| `OTP_EMAIL_IN_PROGRESS`         | An earlier request with this key holds a `PENDING` row            | depends on that request |
| `OTP_EMAIL_DEADLINE_EXCEEDED`   | The time budget ran out before SMTP started                       | no                      |
| `OTP_EMAIL_PROVIDER_FAILED`     | The provider refused, or never received the complete message      | no                      |
| `OTP_EMAIL_DELIVERY_UNKNOWN`    | The complete message reached the provider; no answer came in time | **yes**                 |
| `OTP_EMAIL_RESULT_NOT_RECORDED` | The provider accepted, but `SENT` could not be stored             | **yes, it was sent**    |

The caller handles all of them the same way: report failure, create a new OTP and `requestId`.

### Idempotency

Key: `(requestId, recipientEmail, OTP_SIGN)`, enforced by the unique index
`uq_event_recipient_type (event_id, recipient_email, notification_type)`.

| Existing row                                        | Response                                                                                         | Mail sent?   |
| --------------------------------------------------- | ------------------------------------------------------------------------------------------------ | ------------ |
| none; this request inserts `PENDING`                | `200` once the provider accepts (`SENT`); otherwise `502` with one of the codes above (`FAILED`) | at most once |
| `SENT`                                              | `200`                                                                                            | no           |
| `FAILED`                                            | `502 OTP_EMAIL_PREVIOUSLY_FAILED`                                                                | no           |
| `PENDING` (in flight, or the process died mid-send) | `502 OTP_EMAIL_IN_PROGRESS`                                                                      | no           |

Only the request whose `INSERT` creates the row sends. A duplicate key never counts as
permission to send, so concurrent requests for the same key send one mail at most. Nothing is
queued or retried in the background.

> **After any `502` the caller must create a new OTP with a new `requestId`.** A `requestId`
> that failed is never sent, not even when it is called again later. This follows matrix line 20:
> no mail may arrive for an old `requestId` after the caller was told it failed.

### Stored data and logs

- `body` holds `[redacted:OTP_SIGN_V1]`. The subject has no code. `failure_reason` is built from
  error codes only, never from provider messages. A reason ending in `_AFTER_DATA` (for example
  `SMTP_DEADLINE_EXCEEDED_AFTER_DATA`) means the provider received the complete message and may
  have delivered it. `DEADLINE_BEFORE_SEND` and `SMTP_DEADLINE_BEFORE_*` mean nothing was sent.
- The mail is rendered in memory only (`OTP_SIGN_V1`: code, `contractId`, the hash shortened to
  its first and last 8 characters, and expiry in `Asia/Ho_Chi_Minh` time). It is sent as text
  and HTML, and every value is HTML-escaped.
- Request bodies are never logged. Pino `redact` covers `otpCode` at several depths and the
  `x-internal-secret` header, in case one of them is ever passed to the logger. Log lines carry
  `requestId`, `notificationId`, `recipientUserId`, template version and status. Unit and
  integration tests fail if the OTP shows up in a log line, a stored column or an error response.

### Time budget

The send runs inside contract-service's HTTP call. If that call times out first, the caller
reports a failure while the mail may still go out, which matrix line 20 forbids. So the whole
request, not only SMTP, has a deadline counted from when it reaches the service
(`OTP_TIME_BUDGET` in `src/application/otpTimeBudget.ts`):

| Setting                                     | Value   | Notes                                                                                                                                         |
| ------------------------------------------- | ------- | --------------------------------------------------------------------------------------------------------------------------------------------- |
| Caller read timeout                         | 5000 ms | contract-service `application.yml`, `feign.client.config.default`. The notification client does not exist yet (T2); it inherits this default. |
| Request budget                              | 4500 ms | Hard end of the request; leaves ≥ 500 ms for HTTP parsing, the response and the network                                                       |
| Each database call                          | 750 ms  | Includes waiting for a pool connection, PREPARE and EXECUTE                                                                                   |
| SMTP, whole exchange                        | 2500 ms | `totalTimeout` in `OTP_SMTP_TIMEOUTS`; ends early enough to leave the markSent reserve                                                        |
| markSent reserve                            | 750 ms  | Kept free after SMTP so `SENT` can be recorded inside the budget                                                                              |
| SMTP `connectionTimeout` (DNS+TCP+greeting) | 1000 ms | `src/infrastructure/email/smtpEmailSender.ts`                                                                                                 |
| SMTP `greetingTimeout` / `socketTimeout`    | 1000 ms | `socketTimeout` measures inactivity only; the 2500 ms total is the real cap                                                                   |
| DATA reply reserve                          | 1000 ms | Message body is written only if this much time is left when the server answers `354`                                                          |

Worst-case sending path: reserve 750 + SMTP 2500 + markSent 750 = 4000 ms, inside the 4500 ms budget.

Rules the code enforces:

- SMTP starts only if at least 2500 + 750 ms of the budget are left after `reserve`. Otherwise the
  row is set `FAILED` (`DEADLINE_BEFORE_SEND`) and the answer is `502 OTP_EMAIL_DEADLINE_EXCEEDED`.
  Nothing was sent.
- Nodemailer writes the message only after the server answered `DATA` with `354`. The service
  hands it a stream that checks the deadline at that moment. With less than 1000 ms left it writes
  nothing, so the server never sees the terminating `.` and cannot accept the message
  (`SMTP_DEADLINE_BEFORE_DATA_BODY`). The `DATA` command itself is sent by Nodemailer right after
  the `RCPT` replies and cannot be held back through its API; withholding the body is what matters.
- **The T2 client must keep its read timeout at 5000 ms or more.** A shorter timeout reopens the
  window where the caller gives up while this service is still sending.

#### Database timeouts do not cancel statements

When a database call hits its limit, the connection is destroyed and the call fails with
`DB_DEADLINE_EXCEEDED`. MySQL is not told to stop: a statement already queued or running may
still complete and commit after the request has answered. What that means per call:

| Call times out | Answer                              | What may happen afterwards in the database                                     |
| -------------- | ----------------------------------- | ------------------------------------------------------------------------------ |
| `reserve`      | `502 OTP_EMAIL_DEADLINE_EXCEEDED`   | The `INSERT` may commit a `PENDING` row. Nothing sends for it (see Operations) |
| `findByKey`    | `502 OTP_EMAIL_DEADLINE_EXCEEDED`   | Nothing; the row belongs to an earlier request                                 |
| `markSent`     | `502 OTP_EMAIL_RESULT_NOT_RECORDED` | The `UPDATE` may still set `SENT`; a later retry would then get `200`          |
| `markFailed`   | the SMTP failure's `502`            | The row ends `FAILED` or stays `PENDING`; both answer `502`                    |

The integration test `gives up without sending when the table is locked` shows this: the
`INSERT` waits on a table lock, the request answers within budget, and the statement may
commit once the lock is released.

### Cases where the outcome is uncertain

SMTP has no way to take back a message once the server has received the final `.`. Closing the
socket does not retract it. The service therefore never claims "not sent" after that point:

1. **No reply to the final `.` in time, or the connection drops.** The server may deliver. The
   row is `FAILED` with a reason ending in `_AFTER_DATA`, and the answer is
   `502 OTP_EMAIL_DELIVERY_UNKNOWN`. The caller creates a new OTP. The user may receive both
   mails, but contract-service has already superseded the old challenge (signature design §6,
   step 3), so the old code does not work. A `250` that arrives after the deadline is ignored;
   the row stays `FAILED`.
2. **Provider accepted, but storing `SENT` failed or timed out.** The mail has gone out and the
   row stays `PENDING` (or becomes `SENT` late, see the table above). The answer is
   `502 OTP_EMAIL_RESULT_NOT_RECORDED`. The error log line has the `notificationId` and the
   provider message id.

## Operations: OTP rows left `PENDING`

A request holds its row `PENDING` for at most the 4500 ms request budget. A row still `PENDING`
after that will never be sent: no background job or retry picks it up. Causes:

- `reserve` timed out but its `INSERT` committed afterwards (nothing was sent);
- the process was killed between `reserve` and recording the result (sent or not);
- `markSent` failed after the provider accepted (sent);
- `markFailed` failed after a provider error (not sent).

Every later call with that `requestId` gets `502 OTP_EMAIL_IN_PROGRESS`. Users are not stuck:
contract-service creates a new OTP with a new `requestId`. The row only matters for
investigation.

**Find them:**

```sql
SELECT notification_id, event_id AS request_id, user_id, recipient_email, created_at, updated_at
  FROM notification_logs
 WHERE notification_type = 'OTP_SIGN'
   AND status = 'PENDING'
   AND created_at < UTC_TIMESTAMP(6) - INTERVAL 30 SECOND
 ORDER BY created_at;
```

`created_at` is stored in UTC. 30 seconds is well past the 4.5 s budget; anything it returns is
abandoned.

**Investigate:** search the logs for the `notificationId`.

- `otp email sent but SENT could not be recorded` (or `... its row is missing`): the provider
  accepted; the line has `providerMessageId`, which can be matched in the provider's (MailHog's)
  message list. `errorCode` tells why the update failed (`DB_DEADLINE_EXCEEDED`, `ECONNRESET`, …).
- `otp email failed and FAILED could not be recorded`: the provider did not take it
  (`failureReason`), and the update then failed.
- `otp email reserve timed out; not sending`: nothing was sent.
- No line after `reserve`: the process died mid-request. Whether the mail went out is unknown;
  check the provider by recipient and time.
- A `FAILED` row whose reason ends in `_AFTER_DATA` is not conclusive either: check the provider
  before telling a user the mail never left. A timeout never proves the provider refused it.

**Recover:** if a row needs closing, mark it `FAILED`; this does not change any answer, since
`PENDING` and `FAILED` both answer `502` and neither ever sends:

```sql
UPDATE notification_logs
   SET status = 'FAILED', failure_reason = 'ABANDONED_PENDING', updated_at = UTC_TIMESTAMP(6)
 WHERE notification_id = ? AND status = 'PENDING' AND notification_type = 'OTP_SIGN';
```

**Never `DELETE` these rows.** The row is what stops the `requestId` from sending. Without it, a
late or repeated call with the old `requestId` would reserve again and send an OTP after the
caller was told it failed. Do not set such a row to `SENT` either, unless the provider confirms
delivery; it would make a retry answer `200` for a mail that may not exist.

## Contract decisions

Settled in `contracts/openapi/golden-flow-api.yaml` (plan item N0):

1. **`serviceAuth` is `X-Internal-Secret`.** It covers the four `x-internal` endpoints
   (`/internal/v1/users/{userId}`, `/internal/v1/notifications/otp-email`,
   `/internal/v1/audit/records`, `/internal/v1/bank/ledger`). Only the OTP endpoint is
   implemented so far; the running service-to-service calls (contract-service `FeignConfig`,
   user/product-service `HeaderAuthenticationFilter`) already use this header, so nothing breaks.
2. **Responses:** `200` (no body), `400` and `401` (the shared `BadRequest`/`Unauthorized`
   responses), `500` and `502` (`ErrorEnvelope`, codes listed in the contract). There is no
   `413`/`415`; this service answers `400` for those cases.
3. **Caller rule:** every `502`/`500` means "report failure, create a new OTP and `requestId`".
   The contract says that `OTP_EMAIL_DELIVERY_UNKNOWN` and `OTP_EMAIL_RESULT_NOT_RECORDED` may
   still deliver, and that the caller's read timeout must be at least 5000 ms.
4. **Field rules:** `minLength`/`maxLength` on `requestId`, `recipientUserId` and
   `recipientEmail` in the schema; the future-`expiresAt` rule as `x-validation`.

## Open decisions

1. **SMTP encryption and authentication (before N6).** Like the Java service, the sender uses
   no TLS (`ignoreTLS: true`) and no SMTP auth, and there is no config for credentials. That is
   fine for MailHog. A real provider needs `requireTLS` (or implicit TLS on 465), credentials
   from secrets, and new environment variables.
2. **T2.** The contract-service notification client is not written yet; it must keep
   `readTimeout` ≥ 5000 ms, as the contract now states.

## Development

```sh
npm ci
npm run typecheck
npm run lint
npm test                  # unit tests, no Docker needed
npm run test:integration  # needs Docker (MySQL 8.0, Flyway 10.10.0, MailHog v1.0.1)
npm run build
npm run contracts:check
```

On a Windows checkout with `core.autocrlf=true`, `prettier --check` reports every file whose
working copy has CRLF endings. The files in git use LF. `npx prettier --check --end-of-line auto .`
shows real formatting problems only.

### Integration tests with MailHog

`test/integration/otpEmail.test.ts` starts disposable MySQL and MailHog containers, applies
`db/migration` V1–V3 with Flyway and calls the endpoint in process. It checks that:

- a valid request delivers one mail, verified through MailHog `GET /api/v2/search`;
- the stored provider message id is MailHog's API `ID` (the `queued as` id from the SMTP reply),
  not the `Message-ID` header;
- repeated requests, and 10 concurrent requests spread over two app instances with separate
  pools, produce one mail and one row;
- an SMTP port with nothing listening gives `502`, and the same `requestId` sent later to a
  working server gives `502` with no mail;
- a locked `notification_logs` table, or a pool with no free connection, gives
  `502 OTP_EMAIL_DEADLINE_EXCEEDED` within the request budget and sends nothing;
- the OTP submitted in each test appears in no log line and no `notification_logs` column.

The SMTP race cases (deadline before `DATA`, while waiting for `354`, after the final `.`, a
silent close before the greeting, a late `250`) are unit tests against a scripted SMTP server in
`test/unit/smtpEmailSender.test.ts`.

### Running locally for the Bruno requests

Bruno requests `20`–`23` in `backend/bruno/notification-service/` target this service on
`{{notification_service_url}}` (`localhost:8085`). Set the secret var `service_internal_secret`
in the `local` environment to the `SERVICE_INTERNAL_SECRET` value in `backend/.env`. Requests
`00`–`19` are for the Java consumers and do not apply here until N5.

The plan's `notification-node` Compose profile has not been added yet (N1 follow-up). Until it is,
run the service on the host against the Compose MySQL and MailHog, using its own database:

```sh
cd backend
docker compose up -d notification-db mailhog rabbitmq
docker compose exec notification-db mysql -uroot -p"$DB_ROOT_PASSWORD" \
  -e "CREATE DATABASE IF NOT EXISTS notification_node_db"
docker run --rm -v "$PWD/notification-service-node/db/migration:/flyway/sql" flyway/flyway:10.10.0 \
  "-url=jdbc:mysql://host.docker.internal:3311/notification_node_db?allowPublicKeyRetrieval=true&useSSL=false" \
  -user=root "-password=$DB_ROOT_PASSWORD" -locations=filesystem:/flyway/sql migrate

cd notification-service-node
DB_PASSWORD="$DB_ROOT_PASSWORD" SERVICE_INTERNAL_SECRET="<same as backend/.env>" npm run dev
```

The defaults point at `localhost:3311/notification_node_db`, MailHog on `localhost:1025` and port
`8085`. Never set `DB_NAME=notification_db` before the cutover.
