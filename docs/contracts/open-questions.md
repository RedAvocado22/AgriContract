# Contract Questions

No open contract blocker remains from OC-01 through OC-11 or OQ-02/OQ-03 after the approved human decisions on 18/07/2026.

| ID | Resolution | Authoritative source |
|---|---|---|
| OQ-02 / OQ-02b | Closed with `InspectionSettlementResultV1`, `resultHash`, revised `reportHash`, and inspection entitlement formula. | `inspection-phase2-design.md` section 2.3/4; `milestone-escrow-phase2-design.md` section 3.2 |
| OQ-03 / OC-11 | Closed with ES256, RFC 8785 signed payload, exact paths/body, timestamp/nonce policy and six canonical errors. | `bank-service-phase2-design.md` section 3.5; `api-gateway-phase2-design.md` section 3.4 |
| OC-01 / OC-05 | Closed by promoting the approved signature, milestone and activation recovery paths into authoritative Markdown. | `signature-phase2-design.md` section 6.0; `milestone-escrow-phase2-design.md` section 3.2.0 |
| OC-02 / OC-03 | Closed with exact bank request/result identity semantics and contract/milestone correlation fields. | `bank-service-phase2-design.md` sections 3-4; `milestone-escrow-phase2-design.md` section 7 |
| OC-04 | Closed with typed reconciliation content and explicit money legs. | `hash-chain-phase2-design.md` section 4.5; `bank-service-phase2-design.md` section 5b.1 |
| OC-06 | Closed: `milestone.settled` carries only the design-approved recipient routing exception; audit emits the notification command. | `milestone-escrow-phase2-design.md` section 7.1; `hash-chain-phase2-design.md` section 4.3 |
| OC-07 / OC-08 | Closed with authoritative escrow projection states and derived OTP lifecycle. | `state-machines.md`; `enum-ownership.md` |
| OC-09 | Closed by retaining the SDS error envelope and freezing only the six approved security error codes. | `error-catalog.md`; `bank-service-phase2-design.md` section 3.5.2 |
| OC-10 | Closed with the versioned normalized inspection result and contract-service consumer rules. | `inspection-phase2-design.md` section 2.3/4 |

Future event/API/error additions still require an authoritative Markdown change before contract files are updated.
