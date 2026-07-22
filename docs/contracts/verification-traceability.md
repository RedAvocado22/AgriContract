# Verification Matrix Traceability

This table covers all 55 rows in `verification-matrix-phase2.md` plus the signed goods/quality/delivery/pricing additions frozen on 2026-07-23. Each row maps to a dependency-ordered implementation task; the task must cite the authoritative design section and fixture before it can pass its release gate.

| Matrix row | Contract surface / invariant | Planned task | Fixture / exit |
|---|---|---|---|
| 1 | Bank request source-event and one ledger effect | T0/T5 | Duplicate request: one leg |
| 2 | Append-only bank ledger | T5 | UPDATE/DELETE denied |
| 3 | Signed terms immutable | T2 | Terms update after signature returns 409 |
| 4 | Audit record hash detects tamper | T9 | Verify job reports field |
| 5 | Typed ledger/audit reconciliation | T5/T9 | Altered leg raises mismatch |
| 6 | Bank completion callback dedup | T5 | Duplicate callback is no-op |
| 7 | DLQ replay preserves original event ID | T0 | Replay has no side effect |
| 8 | Terminal contract has zero lock | T3/T5/T13 | Missing/failed remedy leg blocks terminal event; complete leg set and zero lock permits it |
| 9 | ES256 kill switch and system lock | T1/T5 | Bad signature/replay/lock exact errors |
| 10 | Level 2 explicit legs conserve money | T7 | Silent buyer release-floor fixture |
| 11 | Deposit lock retry and recovery | T5/T6 | Three failures then retry to ACTIVE |
| 11b | Partial lock refund before activation failure | T5/T6 | Refund pending blocks terminal state |
| 11c | Both signatures use same terms hash | T2 | Mutation/hash mismatch rejected |
| 11d | All audit fields are hash committed | T9 | Source/subject/previous hash tamper fails |
| 11e | Audit anchor append-only | T9 | Anchor UPDATE/DELETE denied |
| 11f | Source/result hashes separate from record hash | T2/T7/T9 | Signed/inspection hash tamper rejected |
| 11g | Unresolved allegation has no consequence | T3/T4 | `BreachCase` not RESOLVED yields no money/lock |
| 11h | Requester is not inferred as breacher | T3/T4/T11 | Mirrored requester/breacher fixture |
| 11i | Null final role has no penalty/lock | T4/T5/T8 | Mutual/FM/activation refund-only fixture |
| 11j | LegalProfile penalty cap | T2/T4 | Rate above cap returns 400 |
| 11k | No double recovery | T4/T5 | Damages policy leg matrix |
| 11l | Replacement links and no double count | T3/T6/T7 | Old locks zero; new starts at zero |
| 11m | Funding failure pauses seller deadline | T6 | Failed lock, cure window and buyer attribution |
| 11n | Termination remedy is pro-rata | T4/T5 | Settled milestones untouched |
| 11o | Remedy decision/leg/reputation idempotency | T4/T5/T8 | Replay finalized event, one leg and lock |
| 11p | Replacement crash windows | T3/T6/T13 | Unsigned, refund-pending and post-supersede failure |
| 11q | Evidence replay audit/anchor/email dedup | T9/T10 | Same event produces one record/anchor/email |
| 12 | Participant ownership checks | T1/T2/T7 | Other user receives 403 |
| 13 | Gateway strips client identity headers | T1 | Forged identity is ignored |
| 14 | Internal routes are not public | T1 | External `/internal/**` is blocked |
| 15 | Infected file never becomes evidence | T12 | EICAR upload ends FAILED |
| 16 | ADMIN-only activation/governance operations | T1/T3/T6 | OPERATOR gets 403 |
| 17 | Maker-checker separation | T12 | Proposer cannot approve own action |
| 18 | Overlapping reputation locks retain latest | T8 | Early unlock leaves later lock |
| 19 | Sign fails closed on user outage | T1/T2 | User outage returns 503 |
| 20 | Synchronous OTP failure has no late email | T2/T10 | Failed request has no delayed send |
| 21 | Public DTO excludes PII | T1/T2 | Public response has no contact fields |
| 21b | OTP binds user, contract, terms and challenge | T2 | Wrong user/stale terms rejected |
| 21c | Lock revision is monotonic across restart | T8 | Older revision ignored |
| 21d | Legal hold blocks file deletion | T12 | Held file remains |
| 21e | Two-step file deletion self-heals | T12 | Blob retry reconciles tombstone |
| 21f | Listing/offer fail closed on user outage/lock | T1/T12 | Create operation returns 503/403 |
| 22 | Commodity plot gate | T12 | Coffee/rubber and rice/cashew two-way fixture |
| 23 | Analytics outage is non-critical and catches up | T11 | Queue backlog catches up after restart |
| 24 | Notification recipient/type dedup | T10 | Two recipients, retry, one SENT each |
| 25 | Restore money traffic only after verify | T9/T13 | Two verification passes required |
| 26 | Old reputation event cannot overwrite new | T8 | Lower revision ignored |
| 26b | Cross-seller plot overlap is a review signal | T12 | HIGH risk and operator review |
| 26c | Yield anomaly is signal, not gate | T12 | Listing remains pending/reviewable |
| 26d | Deployment policy is enforced | T13 | Digest/secret/branch policy failure blocks build |
| 26e | Delta shortfall opens review, no auto breach | T3/T7 | `BreachCase`, pro-rata release |
| 26f | Only strategic remedy reasons lock reputation | T4/T8 | Production shock no lock; side-selling lock |
| 26g | Analytics stores final role separately from requester | T11 | Termination fact fields and mutual exclusion |
| 26h | Exonerated allegation has no negative consequence | T3/T4/T8 | Resolved null role, refund/release only |
| 26i | Pending inspection blocks auto-confirm | T7 | Timer applies only to `BUYER_RECEIVED` |
| 27 | Declared/committed/actual quality unions cover four commodities | T2/T7/T12 | Four valid variants and all commodity/spec mismatches rejected |
| 27b | Rice variety is identity, not an actual/deviation metric | T2/T7/T12 | `varietyName` accepted in goods/committed spec and rejected in rice actual/policy |
| 27c | Goods snapshot is independent from mutable source records | T2/T12 | Listing/Product/Category/Plot edit/delete leaves stored snapshot and hash unchanged |
| 27d | Every new signed field is hash committed; legacy policy is explicit | T2 | Nested-field mutation changes hash; first signature locks PATCH; signed legacy reads, incomplete legacy draft cannot sign |
| 27e | Quality disposition is deterministic | T4/T7 | Recompute from `signedContentHash` committed spec/deviation policy and `resultHash` actuals; subjective reviewer input cannot change result |
| 27f | Inspection requirement does not add milestone state | T3/T7 | Clean optional flow creates no report; mandatory guard blocks settlement in `BUYER_RECEIVED`/`CONTESTED` until confirmed report |
| 27g | Bounded milestone price acceptance is authenticated and cutoff-safe | T2/T6/T9/T11 | In/out-band, self-accept, stale/cutoff, immutable accept event and unchanged signed hash |
| 27h | Funding uses effective price while deposits remain nominal | T5/T6 | Base fallback, accepted-price funding amount, no settlement true-up, deposit unchanged for upward/downward adjustment |
| 27i | Quality disposition maps exactly to attribution/remedy | T3/T4/T5/T8 | Conforming has null role/no consequences; partial max discount once/no punitive reputation; non-conforming is Seller + `QUALITY_BELOW_COMMITTED` with rejected-milestone refund/policy penalties; inconclusive emits no money |
| 27j | Exact quality rejection is commodity-limited | T2/T7/T12 | Coffee `type` and rubber/cashew `grade` mismatch reject; rice remains numeric-only |
| 27k | Quantity precedence prevents double tolerance | T4/T5/T7 | Clean path keeps Delta 1/Delta 2; inspected/contested path uses accepted quantity × effective price with safety cap and no second Delta 2 |
| 27l | Delivery certificate is evidence only | T2/T7/T10 | Optional seller/buyer certificate refs are not parsed into actual metrics, `resultHash` or disposition and cannot replace a confirmed report |
| 27m | Quality money conserves per fund source | T4/T5/T9 | `MILESTONE_PAYMENT` legs sum to `batchAmount`; penalty and seller-deposit forfeiture reconcile separately; penalty base is affected milestone value |
| 27n | Quality resolution has one idempotent money trigger | T4/T5/T7 | Replay `remedy.finalized` is a no-op by leg identity and no `milestone.settled` is emitted/consumed for the same resolution |
| 27o | Inspection cost policy is signed and deterministic | T2/T4/T7 | Only `LOSER_PAYS` validates; conforming assigns Buyer, partial/non-conforming Seller, inconclusive defers until final result |
| 27p | Quality reject remains milestone-scoped | T3/T4 | `NON_CONFORMING` fails only the affected milestone and does not auto-terminate; alternate bargain requires supersede |

## Release rule

The release gate is the complete set above, not the earlier matrix subset. A task is incomplete when its payload, idempotency key, nullability, owner or migration note differs from the corresponding source design.
