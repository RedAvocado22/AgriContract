# Verification Matrix Traceability

This table maps all 39 Verification Matrix rows to the exact contract surface, owner, fixture, planned PR and exit check. `PR-Sx` refers to the vertical slice in `docs/planning/implementation-plan.md`; it is a planning identifier, not an implementation commit.

| Matrix | API/event | Owner | State/invariant | Fixture | PR / reviewer / exit |
|---|---|---|---|---|---|
| 1 | `bank.*_requested`, `bank.*_completed` | bank/escrow | `sourceEventId == request eventId`; one ledger effect | duplicate request | PR-S5 / P4 / one entry |
| 2 | bank ledger DB | bank | append-only | UPDATE/DELETE DB user | PR-S5 / P4 / denied |
| 3 | `PATCH /contracts/{id}/terms`, signatures | contract | signed terms immutable | update after first signature | PR-S3 / P2 / 409 |
| 4 | audit records + `VerifyChainJob` | audit | record hash commits every field | tamper source metadata | PR-S8 / P2 / fail location |
| 5 | internal audit read + reconciliation command | bank/audit | typed money projection equals ledger | altered settled leg | PR-S8 / P4 / mismatch alert |
| 6 | bank completion callbacks | bank | result dedup and re-publish | duplicate callback | PR-S5 / P1 / no duplicate |
| 7 | DLQ replay envelope | all consumers | original eventId preserved | replay original event | PR-S1 / P3 / no side effect |
| 8 | `contract.settled`, `contract.cancelled` | escrow/bank | terminal lock total zero | terminal contract with lock | PR-S4/S8 / P2 / zero lock |
| 9 | emergency lock/unlock API | bank/gateway | ES256, nonce/timestamp, system lock gate | wrong signature/replay/active lock | PR-S5 / P4 / exact 401/409 |
| 10 | Level 2 three events | contract/escrow | explicit legs conserve batch amount | buyer silent, X2 high/low | PR-S6 / P4 / no over-release |
| 11 | `escrow.deposit_lock_failed` + activation APIs | escrow/contract | retry failed leg only | bank fails 3 times | PR-S4 / P2 / ACTIVE after retry |
| 11b | activation mark-failed + `bank.refund_requested` | escrow/bank | refund before `ACTIVATION_FAILED` | partial lock/refund failure | PR-S4 / P2 / pending on failure |
| 11c | sign initiate/verify | contract | both signatures same terms hash | terms mutate between signatures | PR-S3 / P2 / reject |
| 11d | audit record hash | audit | source/subject/prev fields committed | mutate one field | PR-S8 / P2 / verify fail |
| 11e | `audit_anchor` DB | audit | INSERT/SELECT only | UPDATE/DELETE attempt | PR-S8 / P4 / denied |
| 11f | contract/signed email and inspection hashes | contract/inspection/audit | source/result hashes committed; record hash separate | tamper result/report identity | PR-S7/S8 / P1 / reject/reconcile |
| 12 | contract/milestone/statement APIs | owning service | participant ownership | other user calls action | PR-S11 / P2 / 403 |
| 13 | Gateway identity boundary | gateway | client identity headers stripped | forged `X-User-*` | PR-S11 / P2 / downstream JWT identity |
| 14 | `/internal/**` | gateway | internal routes not public | external internal request | PR-S11 / P2 / 404/403 |
| 15 | file upload + `file.ready/failed` | file | infected file cannot become evidence | EICAR upload | PR-S10 / P4 / failed/no ready |
| 16 | activation/admin routes | governance/gateway | OPERATOR cannot invoke ADMIN | OPERATOR retry/mark-failed | PR-S11 / P2 / 403 |
| 17 | maker-checker API/events | reputation | proposer cannot approve own action | same actor propose/approve | PR-S12 / P1 / reject |
| 18 | reputation unlock projection | reputation/user | effective overlapping lock retained | early unlock with two locks | PR-S12 / P2 / latest lock remains |
| 19 | sign eligibility dependency | contract/user | fail closed on user outage | user-service unavailable | PR-S3 / P2 / 503 |
| 20 | synchronous OTP API | contract/notification | no delayed send after provider failure | provider rejects OTP | PR-S3 / P3 / no late email |
| 21 | public user/reputation DTO | user/gateway | no contact PII | public GET | PR-S2/S11 / P1 / no email/phone |
| 21b | sign verify API | contract | otpId/user/contract/terms binding | wrong JWT/contract/stale terms | PR-S3 / P2 / 403/409 |
| 21c | `reputation.locked/unlocked` | reputation/user | monotonic lockRevision | out-of-order revision | PR-S2/S12 / P1 / stale ignored |
| 21d | file delete API/job | file | legal hold blocks delete | held file deletion | PR-S10 / P4 / retained |
| 21e | file tombstone/blob delete | file | two-step delete self-heals | blob delete failure/retry | PR-S10 / P4 / consistent |
| 21f | listing/offer eligibility | product/user | fail closed on user outage/lock | create while dependency down | PR-S2 / P1 / 503 |
| 22 | listing/product creation | product | commodity plot gate | coffee without plot / rice without plot | PR-S2 / P1 / expected gate |
| 23 | analytics event consumer | analytics | read model outage non-critical; backlog catch-up | analytics down then restart | PR-S12 / P3 / catch-up |
| 24 | notification commands | notification | dedup `(eventId, recipient, type)` | retry two-recipient command | PR-S9 / P1 / one SENT each |
| 25 | restore/verify jobs | bank/audit | money traffic opens after verify | restore bank DB | PR-S13 / P2 / two verify passes |
| 26 | reputation lock events | user/reputation | old decision cannot overwrite new | stale eventId/new revision | PR-S12 / P1 / stale ignored |
| 26b | plot registration API | product | cross-seller overlap flagged | overlapping polygons | PR-S12 / P1 / HIGH + review |
| 26c | listing yield validation | product | yield anomaly is signal not gate | quantity above yield bound | PR-S12 / P1 / signal/review |
| 26d | CI/deployment policy | governance | digest pin/secrets/branch approval | policy violation build | PR-S13 / P1 / build fails |

## Traceability rule

Every implementation PR must link its fixture to the row above and cite the exact authoritative Markdown section. A PR cannot claim exit success when its mapped row still depends on an unreviewed contract change.
