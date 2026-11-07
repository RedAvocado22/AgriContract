package com.agricontract.contract.application.port;

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
}
