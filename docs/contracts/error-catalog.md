# Error Catalog

The design Markdown defines failure behavior and HTTP classes but does not define a shared JSON wire format. The consolidated SDS supplies the existing cross-service error envelope, and no authoritative design Markdown supersedes it. The contract therefore retains that envelope instead of introducing RFC 9457.

## Shared response format

```json
{
  "timestamp": "2026-07-05T09:12:33Z",
  "status": 409,
  "error": "Conflict",
  "code": "CONTRACT_ALREADY_SIGNED",
  "message": "Hợp đồng đã ở trạng thái SIGNED",
  "path": "/api/v1/contracts/{id}/sign",
  "traceId": "b7f3..."
}
```

Required fields: `timestamp`, `status`, `error`, `code`, `message`, `path`, and `traceId`.
Services must not put OTP, access tokens, or unnecessary PII in `message`.

## Canonical error codes approved for Phase 2 security contract

The following six codes are the complete approved canonical list for the External Verifier kill-switch wire contract. No RFC 9457 conversion is allowed, and no additional canonical code is inferred from a prose example.

| Code | HTTP | Owner | Trigger |
|---|---:|---|---|
| `EXTERNAL_SIGNATURE_INVALID` | 401 | bank-service | ES256 signature does not verify |
| `EXTERNAL_KEY_UNKNOWN` | 401 | bank-service | `keyId` is not registered/known |
| `SIGNATURE_TIMESTAMP_OUT_OF_WINDOW` | 401 | bank-service | Timestamp outside ±300 seconds |
| `NONCE_REPLAYED` | 409 | bank-service | Persisted nonce is not unique |
| `SYSTEM_ALREADY_LOCKED` | 409 | bank-service | Emergency lock while system lock is active |
| `SYSTEM_NOT_LOCKED` | 409 | bank-service | Emergency unlock while system lock is not active |

All other domain failures retain the SDS envelope and their HTTP class from the owning design; a new canonical code requires a later design decision.

## Stable transport categories

| Category | HTTP behavior | Retry | Owner/source | Verification trace |
|---|---|---|---|---|
| Authentication missing/invalid | `401` at Gateway/service boundary | Caller fixes credentials | `api-gateway-phase2-design.md` sections 2-3 | Matrix 13, 19 |
| Role/ownership denied | `403` | No automatic retry | Owning domain service; Gateway coarse role gate | Matrix 12, 16, 17, 21 |
| Public DTO or internal route violation | `403`/`404` | No retry | Gateway/user-service | Matrix 14, 21 |
| Validation failure | `400` | No retry without payload correction | Owning service | Matrix 22, 26b, 26c |
| Listing/quality discriminator mismatch | `400` | Supply the spec variant matching authoritative `Category.commodity` | product/contract | Matrix 27 |
| Inspection cost policy other than `LOSER_PAYS` | `400` | Use the sole Phase 2 signed value | contract-service | Matrix 27o / 27h |
| Server-owned snapshot field supplied | `400` | Remove client `goodsTerms`/`totalContractValue`; reread server snapshot | contract-service | Matrix 27c |
| State transition conflict / immutable terms | `409` | No blind retry; reread state | contract/escrow/inspection | Matrix 3, 11c, 21b |
| Legacy draft missing new signed terms | `400`/controlled sign rejection | PATCH all required quality/delivery/inspection/pricing terms before initiating signature | contract-service | Matrix 27d |
| OTP expired/locked/invalid or challenge binding mismatch | `403` or `409` as defined by the signature use case | New challenge only | contract-service | Matrix 20, 21b |
| Dependency unavailable on eligibility gate | `503` | Caller may retry with idempotency | user-service callers | Matrix 19, 21f |
| Bank/escrow transient failure | Event retry; API reports controlled failure | Retry failed leg/event only | escrow/bank | Matrix 1, 6, 11, 11b |
| Milestone funding failure | `FUNDING_FAILED`; seller clock remains paused | Retry failed funding leg; buyer cure window applies only to buyer-caused failure | escrow/contract | Matrix 11m |
| Price proposal outside signed band | `400`/`409` validation conflict | Propose a price inside the immutable inclusive band | contract-service | Matrix 27g |
| Price self-accept | `403` | Counterparty must accept using its own JWT | contract-service | Matrix 27g |
| Price accept after funding/cutoff or after prior acceptance | `409` | Reread milestone; funded/accepted price is final for the milestone | contract-service | Matrix 27g, 27h |
| Kill switch active | Controlled bank command failure | No retry until unlock | bank-service | Matrix 9 |
| Duplicate/replay | `200`/no-op for idempotent duplicate, or controlled conflict if state differs | Reuse original eventId only; preserve payload | Every consumer; bank/notification/audit rules | Matrix 1, 6, 7, 11o, 11q, 24 |
| Allegation not final | Controlled `409`/domain rejection; no money/reputation command | Resolve `BreachCase` or create Rổ A AttributionDecision first | contract-service | Matrix 11g, 26e, 26h |
| Legal penalty cap / incompatible damages policy | `400` validation failure | Correct `LegalProfile`/rates/evidence | contract-service remedy calculator | Matrix 11j, 11k |
| Supersede/refund pending conflict | `409`; state remains pending | Retry only outstanding refund/activation leg | contract/escrow/bank | Matrix 11l, 11p |
| File virus/parse failure | File becomes `FAILED`; business attachment cannot advance | Correct source and resubmit | file-service | Matrix 15 |
| File legal hold / retention deletion denied | Controlled rejection, file remains | No deletion retry while hold exists | file-service | Matrix 21d, 21e |
| Audit tamper/reconciliation mismatch | Operational alert; never mutate old evidence | Repair by append-only correction or investigation | audit/bank | Matrix 4, 5, 11d, 11e, 11f, 11q |
| Maker-checker violation | `403`/`409` | New proposal by a different actor | governance/reputation | Matrix 16, 17 |
| Mandatory inspection missing/inconclusive | Controlled `409`/domain pending result | Wait for confirmed complete report or continue existing inconclusive routing | contract/inspection | Matrix 26i, 27e, 27f |
| Duplicate quality money path | Controlled conflict/no-op; never a second bank leg | Keep `remedy.finalized` as the sole resolution trigger and suppress matching `milestone.settled` | contract/escrow | Matrix 27n / 27f |

## Resolution

`OC-09` is corrected: RFC 9457 and the generic codes previously added here were not authoritative. The retained envelope comes from `AgriContract_SDS_Full_v1.docx` section 3.1 (generated by `docs/phase_2/files/build04.js`), with design Markdown continuing to own HTTP/security behavior.
