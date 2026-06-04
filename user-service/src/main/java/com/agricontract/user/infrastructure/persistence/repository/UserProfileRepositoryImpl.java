package com.agricontract.user.infrastructure.persistence.repository;

import com.agricontract.user.domain.model.UserProfile;
import com.agricontract.user.domain.model.vo.UserId;
import com.agricontract.user.domain.repository.UserProfileRepository;
import com.agricontract.user.infrastructure.persistence.mapper.UserProfileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserProfileRepositoryImpl implements UserProfileRepository {

    private final UserProfileJpaRepository jpaRepository;
    private final UserProfileMapper mapper;

    @Override
    public UserProfile save(UserProfile userProfile) {
        // TODO
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public Optional<UserProfile> findById(UserId userId) {
        // TODO
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public boolean existsById(UserId userId) {
        // TODO
        throw new UnsupportedOperationException("TODO");
    }
}
