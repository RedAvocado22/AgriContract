# Phase 2 Vertical-Slice Implementation Plan

## Delivery rules

- No implementation source code change is authorized by this artifact; this plan freezes contract work and test gates first.
- T1 remains contract, escrow, bank, user and Gateway. Gateway contract tests and audit contract projection move into Month 1 so downstream slices do not discover edge/security drift late.
- P1 remains accountable for contract/bank/audit ownership, but audit schema/test preparation and Gateway contract-test execution are delegated to P2/P4 with P1 approval. This reduces the single-person critical path without changing service ownership.
- Every PR links to `docs/contracts/verification-traceability.md`, an authoritative Markdown section and exact Verification Matrix rows.
- No event/API/state/error name is added without an authoritative source or explicit human approval.

## Slices and PRs

| Slice / PR | Owner / delegated work | Dependencies | Tests / trace | Reviewer | Exit criteria |
|---|---|---|---|---|---|
| S0 / PR-S0 Contract source sync | P1; P2 validates docs | None | YAML/OpenAPI/schema parse; traceability table | P2 + P4 | Approved decisions reflected; no open OQ/OC blocker |
| S1 / PR-S1 Envelope and idempotency | P1; P4 builds fixtures | S0 | Matrix 1, 6, 7, 11f | P3 | `eventId/sourceEventId/causationId` rules and replay fixtures pass |
| S11a / PR-S11a Gateway security contract | P2 delegated; P1 accountable | S0, S1 | Matrix 13, 14, 21; emergency route metadata | P1 + P4 | Internal paths blocked; API-key audit hash and ES256 routes have no retry/64KB cap |
| S2 / PR-S2 User KYC and lock projection | P2 | S0, S1 | Matrix 12, 16, 18, 19, 21, 21c, 21f | P1 | Fail-closed gates, role ownership and monotonic lock revisions pass |
| S3 / PR-S3 Contract terms/signature/OTP | P1 | S1, S2, S11a | Matrix 3, 11c, 19, 20, 21b | P2 + P3 | Approved sign paths, OTP binding, immutable terms and sync delivery pass |
| S4 / PR-S4 Escrow activation/recovery | P1 | S1, S3 | Matrix 1, 6, 8, 9, 11, 11b, 16 | P2 + P4 | Per-leg retry, exact recovery paths, refund-pending compensation and ACTIVE gate pass |
| S5 / PR-S5 Bank ledger and kill switch | P1; P4 security fixtures | S1, S4, S11a | Matrix 1, 2, 5, 6, 8, 9 | P4 + P2 | Append-only ledger, positive amounts, ES256 body/errors and system lock gate pass |
| S6 / PR-S6 Milestone 1 settlement | P1 | S4, S5 | Matrix 1, 8, 10 | P4 | `milestone.settled` correlation and release/refund commands conserve money |
| S7 / PR-S7 Inspection Level 1.5 decision | P4; P1 integration approval | S6, file contract | Matrix 11f, 15, 17, 21b | P1 + P5 | `InspectionSettlementResultV1`, both hashes, entitlement formula and matching CONTESTED transition pass |
| S8a / PR-S8a Audit chain/reconciliation contract | P4 delegated; P1 accountable | S1, S5, S6 | Matrix 4, 5, 11d, 11e, 11f | P2 + P1 | Typed audit projection, append-only anchor and read API fixtures pass |
| S8b / PR-S8b Audit implementation integration | P1 | S8a, S7 | Matrix 4, 5, 11d, 11e, 11f, 25 | P2 + P4 | Direct canonical-event ingestion and ledger reconciliation pass |
| S9 / PR-S9 Notification evidence/dedup | P3 | S1, S3, S6, S8a | Matrix 20, 24 | P1 | Commands use recipient routing only where design permits; dedup passes |
| S10 / PR-S10 File evidence pipeline | P5 | S0, S2 | Matrix 15, 21d, 21e | P4 | Virus scan, legal hold and two-step delete pass |
| S12 / PR-S12 Analytics/reputation/product breadth | P3/P4 | S2, S6, S8b | Matrix 17, 18, 22, 23, 26, 26b, 26c | P1 | Non-critical projections catch up; plot/yield/reputation fixtures pass |
| S13 / PR-S13 Golden-flow release gate | P1 accountable; all owners sign | S0-S12 | All 39 Matrix rows | All owners | End-to-end flow, reconciliation and deployment policy pass; no P0/P1 trace gap |

## Five-month cadence

- Month 1: S0, S1, S11a, S2 contract/security foundations.
- Month 2: S3, S4, S5 money and activation invariants.
- Month 3: S6, S7, S8a/S8b golden settlement, inspection and audit reconciliation.
- Month 4: S9, S10 and remaining Gateway hardening; no new public route without contract review.
- Month 5: S12, S13, DR/demo and full 39-row release gate.

## Scope-cut rule

Cut in this order if schedule slips: pricing automation, analytics dashboard breadth, Level 2 automation beyond the approved result/event contract, non-demo inspection edges and UI polish. Never cut ledger append-only controls, positive-money validation, idempotency, Signature/OTP binding, activation compensation, ES256 kill-switch verification, audit verification, typed reconciliation, the Level 1.5 golden dispute or any Matrix fixture.

## PR exit discipline

Each PR must include: changed contract paths/events, source Markdown citations, Matrix row IDs, fixture names, reviewer approval and the corresponding row in `docs/contracts/verification-traceability.md`. “Schema parses” alone is not an exit criterion.
