package com.agricontract.contract.infrastructure.persistence.idempotency;

import com.agricontract.contract.application.port.IdempotencyStore;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Insert-first claim. A concurrent request with the same key blocks on the primary key until the first transaction
 * ends: if it committed this one sees a duplicate (REPLAY/CONFLICT), if it rolled back this insert goes through.
 */
@Component
@RequiredArgsConstructor
public class JdbcIdempotencyStore implements IdempotencyStore {
    private final JdbcTemplate jdbc;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Outcome claim(String actorId, String operation, UUID idempotencyKey, String requestHash) {
        try {
            jdbc.update("INSERT INTO idempotency_record (actor_id, operation, idempotency_key, request_hash)"
                    + " VALUES (?, ?, ?, ?)", actorId, operation, idempotencyKey.toString(), requestHash);
            return Outcome.FIRST;
        } catch (DuplicateKeyException alreadyUsed) {
            String stored = jdbc.queryForObject("SELECT request_hash FROM idempotency_record"
                    + " WHERE actor_id = ? AND operation = ? AND idempotency_key = ?",
                    String.class, actorId, operation, idempotencyKey.toString());
            return requestHash.equals(stored) ? Outcome.REPLAY : Outcome.CONFLICT;
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordResult(String actorId, String operation, UUID idempotencyKey, String resultRef) {
        int updated = jdbc.update("UPDATE idempotency_record SET result_ref = ?"
                + " WHERE actor_id = ? AND operation = ? AND idempotency_key = ?",
                resultRef, actorId, operation, idempotencyKey.toString());
        if (updated != 1) {
            throw new IllegalStateException("No claimed idempotency record for " + operation + " " + idempotencyKey);
        }
    }

    @Override
    public Optional<String> resultOf(String actorId, String operation, UUID idempotencyKey) {
        return jdbc.queryForList("SELECT result_ref FROM idempotency_record"
                        + " WHERE actor_id = ? AND operation = ? AND idempotency_key = ?",
                String.class, actorId, operation, idempotencyKey.toString()).stream()
                .filter(Objects::nonNull).findFirst();
    }
}
