package com.agricontract.user.infrastructure.persistence.repository;

import com.agricontract.user.domain.model.UserProfile;
import com.agricontract.user.domain.model.vo.UserId;
import com.agricontract.user.domain.repository.UserProfileRepository;
import com.agricontract.user.infrastructure.persistence.entity.UserProfileJpaEntity;
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
        UserProfileJpaEntity entity = mapper.toJpaEntity(userProfile);
        UserProfileJpaEntity savedEntity = jpaRepository.save(entity);
        return mapper.toDomain(savedEntity);
    }

    @Override
    public Optional<UserProfile> findById(UserId userId) {
        Optional<UserProfileJpaEntity> userProfileJpaEntity = jpaRepository.findByUserId(userId.value());
        return userProfileJpaEntity.map(mapper::toDomain);
    }

    @Override
    public boolean existsById(UserId userId) {
        return jpaRepository.existsByUserId(userId.value());
    }
}
