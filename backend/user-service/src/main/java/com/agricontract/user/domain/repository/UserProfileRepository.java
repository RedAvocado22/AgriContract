package com.agricontract.user.domain.repository;

import com.agricontract.user.domain.model.UserProfile;
import com.agricontract.user.domain.model.vo.UserId;
import com.agricontract.user.domain.model.vo.VerificationStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserProfileRepository {
    List<String> findEligibleInspectorIds(Instant now);
    // Insert only: a duplicate userId raises UserAlreadyExistsException.
    UserProfile save(UserProfile userProfile);

    // Existing profile only: never inserts. Must run in the transaction that loaded the profile;
    // if another transaction changed it since that load, throws ConcurrentProfileUpdateException.
    UserProfile update(UserProfile userProfile);

    Optional<UserProfile> findById(UserId userId);

    boolean existsById(UserId userId);

    // Oldest registration first, so the KYC queue is reviewed in submission order. page is zero-based.
    List<UserProfile> findByVerificationStatus(VerificationStatus status, int page, int size);

    long countByVerificationStatus(VerificationStatus status);

    // Lock projection (design §2.2). Both apply only when lockRevision is greater than the stored revision, checked
    // and written in one statement, and return false otherwise (older revision or unknown user). Caller's transaction.

    // lockedUntil becomes the later of the stored and the incoming value: a new lock never shortens a longer one.
    boolean applyLock(UserId userId, Instant lockedUntil, long lockRevision);

    // lockedUntil becomes effectiveLockedUntil as computed by reputation-service; null means no active lock.
    boolean applyUnlock(UserId userId, Instant effectiveLockedUntil, long lockRevision);
}
