package com.agricontract.user.infrastructure.persistence.repository;

import com.agricontract.user.domain.model.UserProfile;
import com.agricontract.user.domain.model.vo.UserId;
import com.agricontract.user.domain.repository.UserProfileRepository;
import com.agricontract.user.infrastructure.persistence.entity.UserProfileJpaEntity;
import com.agricontract.user.infrastructure.persistence.mapper.UserProfileMapper;
import com.agricontract.user.common.exception.UserAlreadyExistsException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserProfileRepositoryImpl implements UserProfileRepository {

    private final UserProfileJpaRepository jpaRepository;
    private final UserProfileMapper mapper;

    @Override
    @Transactional
    public UserProfile save(UserProfile userProfile) {
        try {
            UserProfileJpaEntity entity = mapper.toJpaEntity(userProfile);
            UserProfileJpaEntity savedEntity = jpaRepository.saveAndFlush(entity);
            return mapper.toDomain(savedEntity);
        } catch (DataIntegrityViolationException e) {
            throw new UserAlreadyExistsException(userProfile.getUserId().value());
        }
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
