package com.agricontract.user.domain.repository;

import com.agricontract.user.domain.model.UserProfile;
import com.agricontract.user.domain.model.vo.UserId;

import java.util.Optional;

public interface UserProfileRepository {
    UserProfile save(UserProfile userProfile);
    Optional<UserProfile> findById(UserId userId);
    boolean existsById(UserId userId);
}
