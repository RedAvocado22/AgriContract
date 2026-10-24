## Summary

<!-- What changes and why, in a few lines. -->

Closes #<!-- exactly one issue; CI (pr-policy) fails otherwise -->

## Changes

-

## Testing

<!-- Commands you ran and what they showed. Integration tests use Testcontainers (Docker required). -->

## Definition of Done

- [ ] Additive migration exists or is explicitly not required
- [ ] Implementation matches the frozen API/event contract (`contracts/**` unchanged, or changed in a separate docs PR merged first)
- [ ] Unit tests pass
- [ ] Boundary integration/contract test passes when applicable
- [ ] No unrelated refactor or second service implementation
- [ ] New Java service/module? Registered in `.github/workflows/ci.yml` (paths-filter + "all" list)
