package com.agricontract.contract.domain.repository;

import com.agricontract.contract.domain.model.activation.ActivationCancelRequest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ActivationCancelRequestRepository {

    /** The requests still standing on this contract (at most one per party). */
    List<ActivationCancelRequest> findStanding(String contractId);

    void insert(ActivationCancelRequest request);

    void withdraw(UUID requestId, Instant at);

    /** SIGNED contracts whose one-sided window has passed and that have a standing request (D42). */
    List<String> findContractsWithOneSidedCancelDue(Instant now, int limit);
}
