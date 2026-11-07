package com.agricontract.contract.application.dto;

import com.agricontract.contract.domain.model.consent.SignerRole;

import java.time.Instant;
import java.util.UUID;

/** POST /api/v1/contracts/{id}/sign/initiate. authTime comes from the gateway (JWT auth_time), never the body. */
public record InitiateSignCommand(String contractId, String actorId, UUID idempotencyKey, SignerRole signerRole,
                                  String consentStatementVersion, String presentedContentHash, Instant authTime) {
}
