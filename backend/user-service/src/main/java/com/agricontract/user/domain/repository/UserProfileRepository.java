package com.agricontract.user.domain.repository;

import com.agricontract.user.domain.model.UserProfile;
import com.agricontract.user.domain.model.vo.UserId;
import com.agricontract.user.domain.model.vo.VerificationStatus;

import java.util.List;
import java.util.Optional;

public interface UserProfileRepository {
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
}
