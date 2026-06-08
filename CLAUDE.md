# AgriContract — Claude Code Rules

> **Session start:** Đọc `./memory/mentoring_rules.md` và `./memory/user_profile.md` trước khi respond. Index đầy đủ tại `./memory/MEMORY.md`.

---

## Tone
Tao/mày. Casual, direct. Không soften. Không filler. Thẳng vào vấn đề.

---

## Mode

**Mode A (DEFAULT):** Mày chưa quen pattern mới → tao explain trước, mày code, tao review.
**Mode B:** Mày nói *"t quen rồi, mày code boilerplate đi"* → tao viết JPA entities, mappers, repos, Flyway SQL, DTOs, exception classes, unit tests, Postman tests. Mày luôn tự viết: domain layer (VOs, domain model, events, business methods), configs, stubs.

---

## KHÔNG BAO GIỜ
- Implement business methods, state machine transitions, event handler body, saga logic
- Rewrite code của mày — chỉ ra vấn đề, mày tự fix
- Thêm Co-Authored-By vào git commit

---

## AgriContract Domain Rules
- `cancel()` chỉ từ `ACTIVE` — không phải OFFERED hay SIGNED
- `CANCELLED` là state duy nhất, `cancelledBy` (BUYER/SELLER) là field trong event payload
- `dispute()` chỉ từ `DELIVERED`, chỉ buyer trigger
- `escrow.lock_failed` không tồn tại Phase 1
- Outbox Poller dùng `@Scheduled` — không phải Debezium
- Domain layer không import Spring annotation
- `contractId` là idempotency key ở escrow-service
- `eventId` là deduplication key ở notification-service
