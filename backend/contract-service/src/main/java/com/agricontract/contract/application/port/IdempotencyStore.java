package com.agricontract.contract.application.port;

import java.util.Optional;
import java.util.UUID;

/**
 * Remembers which Idempotency-Key each actor already used for an operation, together with a fingerprint of the
 * request. Must run inside the caller's transaction so a failed command does not burn the key.
 */
public interface IdempotencyStore {

    enum Outcome {
        /** First time this key is seen: run the command. */
        FIRST,
        /** Same key and same request already processed: do nothing, return the current state. */
        REPLAY,
        /** Same key reused for a different request: refuse. */
        CONFLICT
    }

    Outcome claim(String actorId, String operation, UUID idempotencyKey, String requestHash);

    /** After FIRST, in the same transaction: the id of what this command created, for replays to return. */
    void recordResult(String actorId, String operation, UUID idempotencyKey, String resultRef);

    /** After REPLAY: what the first run created, empty if it recorded nothing. */
    Optional<String> resultOf(String actorId, String operation, UUID idempotencyKey);
}
