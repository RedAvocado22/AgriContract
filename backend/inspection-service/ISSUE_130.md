# Issue #130 — deterministic inspection commitments

## Scope

Pure inspection-service generation and verification of `resultHash` and `reportHash`.
No endpoint, persistence, assignment lifecycle, signature, file lookup or event publisher is added.
The branch starts directly from main; it does not inherit #129/#160 implementation commits.

No additive migration is required because #130 implements deterministic hash generation/verification as a pure inspection-service capability. Persistence integration belongs to the report workflow and is handled separately.

## Exact projections

```text
resultHash = lowercaseHex(SHA256(RFC8785(normalizedResult)))
reportHash = lowercaseHex(SHA256(RFC8785({
  reportId, contractId, milestoneId, tier,
  reportFileHash, resultHash, sourceTimestamp, actorOrSourceIdentity
})))
```

`normalizedResult` contains exactly resultSchemaVersion, measuredQuantityKg,
acceptedQuantityKg, measurementStatus, actualQualityMetrics, determinedAt and optional
inconclusiveReasons. Coffee metrics contain commodity, type, moisturePercent,
foreignMatterPercent and blackBrokenBeansPercent. An absent reasons property is omitted;
explicit JSON null is rejected. Every legitimate finding, including reasons, is committed.
No envelope, free-text report, raw file bytes, assignment metadata, quality disposition,
penalty, entitlement or settlement information enters either projection.

## Architecture and validation

- Immutable domain records/enums enforce COFFEE-only findings, required fields, version/status,
  decimal ranges and acceptedQuantityKg <= measuredQuantityKg. Reasons are defensively copied.
- `InspectionHashInput` is an isolated strict JSON boundary. It rejects duplicate keys, trailing
  tokens, unknown properties, wrong types, scalar coercion and explicit nulls. It does not modify
  the application's ObjectMapper. COMPLETE with reasons remains accepted. Per the approved Phase B
  requirement, whitespace-only reasons are rejected (the frozen minLength rule alone is weaker).
- `InspectionHashes` depends on `InspectionCanonicalizer`, not a vendor API. Both calculate and
  verify operations are available; verification recomputes and compares fixed-size decoded hashes
  with MessageDigest.isEqual. Malformed/uppercase expected hashes are rejected, never normalized.
- `JcsCanonicalizer` wraps pinned `io.github.erdtman:java-json-canonicalization:1.1`; Maven Central
  metadata was checked before adding it. Jackson builds the projection; the library defines JCS
  bytes. Additional strict parsing and unpaired-surrogate checks protect the adapter boundary.
- SHA-256 uses Java's MessageDigest, canonical UTF-8 bytes and lowercase hexadecimal output.
  `EventEnvelopeCodec` is not used as a canonicalizer.

Reference: https://www.rfc-editor.org/rfc/rfc8785 and
https://github.com/erdtman/java-json-canonicalization .
Tests use the existing repository convention of jackson-dataformat-yaml (Boot-managed, test only)
and com.networknt:json-schema-validator:1.5.3 (test only) to read the frozen schemas in place.

## Number and timestamp policies

Business validation uses BigDecimal without arbitrary scaling or rounding. JSON numbers remain
numbers. RFC8785 uses IEEE-754 binary64/ECMAScript representation: 1, 1.0 and 1e0 coincide; -0
canonicalizes to 0. Values beyond binary64 precision can collide after conversion, e.g.
9007199254740992 and 9007199254740993. Tiny values may underflow; non-finite/overflow values are
rejected. No arbitrary business precision restriction or decimal-as-string contract is introduced.
Hashing does not promise arbitrary-precision decimal distinctions outside the JCS/I-JSON model.

determinedAt/sourceTimestamp are validated date-time strings and retain their exact representation,
including timezone and fractional digits. Equivalent instants with different string spellings can
have different hashes. RFC3339 lowercase t/z and leap-second spelling are supported; validation
checks calendar/time/offset shape and leap-second time position, not an external historical leap
second registry. No clock-derived timestamp is fabricated by the hashing component.

## Integration boundaries

Approved Level 1.5 workflow mapping (outside the generic canonicalizer):

```text
sourceTimestamp = confirmedAt
actorOrSourceIdentity = inspectorId
```

The caller must supply authoritative server-generated report identity, assignment ownership,
trusted inspector identity, immutable file hash and the chosen confirmation timestamp. The generic
actor/source value is an opaque nonblank string, not a substitute for authentication or an
eligibility/ownership check. Keep that timestamp representation consistent with persisted/event
material; do not silently truncate precision after computing a commitment.

The frozen event remains unchanged. A contract-boundary test reconstructs the eight-field report
projection from confirmedAt/inspectorId in its LEVEL_1_5 payload and verifies both hashes after
JSON round-trip. This is a payload fixture test, not a live RabbitMQ or contract-service integration.

LEVEL_2 is a valid tier in the generic projection, but its external-source/operator mapping is
not solved here: external identity is not fully represented in the current frozen event. No
intake workflow or contract extension is introduced.

Future #129 persistence integration can call this capability after allocating stable report identity.
Assignment closure (#160), real file-byte verification, signatures/session freshness, outbox/event
publication and downstream contract-service verification remain separate work. #208 is not a
dependency of this pure capability. No changes were made to PR #226 or #209.

Verification compares against an expected commitment from a trusted source. An attacker replacing
both data and all unprotected hashes can create a self-consistent replacement; hashing alone does
not authenticate the source or prove real-world accuracy of measurements.

## Acceptance criteria and tests

| Criterion | Evidence |
|---|---|
| Recomputation is stable | Literal canonical bytes; independent SHA-256 golden digests; RFC number/property-order vectors; repeated calculation, reordered nested JSON, numeric equivalence, Unicode/escaping and immutable list tests. |
| Tampered metric or file hash is rejected | Parameterized mutations for every mutable result field and all eight report fields; malformed/mismatched expected hashes rejected by recomputation. |
| Subjective criteria cannot alter result | Unknown fields rejected at both result and coffee-metric levels: disposition, scoring, opinion, certificate-derived result, discount, penalty, entitlement and settlement amount. Legitimate reasons still affect the hash. |

The frozen version/commodity constants reject changes instead of producing a new valid commitment.
Schema tests cover missing/null fields, unsupported commodities/types/status/version, invalid ranges,
reasons, timestamp validation and accepted quantity invariants. Frozen API/event schema fixtures are
read from contracts/ without copies or modifications.

## Verification

Commands (Maven from backend/, Python/Git from repository root):

```text
mvn -B -pl inspection-service -am test -Dtest=InspectionHashTest,InspectionHashContractTest -Dsurefire.failIfNoSpecifiedTests=false --no-transfer-progress
mvn -B -pl inspection-service -am clean --no-transfer-progress
mvn -B -pl inspection-service -am verify --no-transfer-progress -Dapi.version=1.44
python scripts/validate-contracts.py
python -m pytest scripts/test_validate_contracts.py -q
git diff --check
```

Verified on 2026-10-26 against main `763ef5e80f9a321a4ff048f7cb6b68d5b32e24ca`:
focused #130 tests: 55 passed, 0 failures/errors/skips. After cleaning stale branch build outputs,
full Maven verification passed 146 tests (91 existing + 55 new), 0 failures/errors/skips.
Five contract files validated; 11 validator tests passed. Git whitespace checks passed.
The existing MySQL/Testcontainers scaffold suite ran; no live report persistence, file-service,
signature or downstream consumer integration is claimed by these results.
