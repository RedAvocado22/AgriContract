package com.agricontract.user.domain.repository;

import com.agricontract.user.domain.model.UserProfile;
import com.agricontract.user.domain.model.vo.UserId;
import com.agricontract.user.domain.model.vo.VerificationStatus;
import org.springframework.data.domain.Pageable;

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

    // Oldest registration first, so the KYC queue is reviewed in submission order.
    List<UserProfile> findByVerificationStatus(VerificationStatus status, Pageable pageable);

    long countByVerificationStatus(VerificationStatus status);
}
