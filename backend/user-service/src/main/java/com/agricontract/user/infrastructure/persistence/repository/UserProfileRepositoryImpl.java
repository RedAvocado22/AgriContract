package com.agricontract.user.infrastructure.persistence.repository;

import com.agricontract.user.common.exception.UserAlreadyExistsException;
import com.agricontract.user.domain.exception.ConcurrentProfileUpdateException;
import com.agricontract.user.domain.model.UserProfile;
import com.agricontract.user.domain.model.vo.UserId;
import com.agricontract.user.domain.model.vo.VerificationStatus;
import com.agricontract.user.domain.repository.UserProfileRepository;
import com.agricontract.user.infrastructure.persistence.entity.UserProfileJpaEntity;
import com.agricontract.user.infrastructure.persistence.mapper.UserProfileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserProfileRepositoryImpl implements UserProfileRepository {

    private final UserProfileJpaRepository jpaRepository;
    private final UserProfileMapper mapper;

    @Override
    public List<String> findEligibleInspectorIds(Instant now) {
        return jpaRepository.findEligibleInspectorIds(now);
    }

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
    @Transactional(propagation = Propagation.MANDATORY)
    public UserProfile update(UserProfile userProfile) {
        String userId = userProfile.getUserId().value();
        // Same transaction as the caller's load, so this returns the managed entity with the version read then.
        UserProfileJpaEntity entity = jpaRepository.findByUserId(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));
        mapper.updateEntity(userProfile, entity);
        try {
            return mapper.toDomain(jpaRepository.saveAndFlush(entity));
        } catch (OptimisticLockingFailureException e) {
            throw new ConcurrentProfileUpdateException(userId);
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

    @Override
    public List<UserProfile> findByVerificationStatus(VerificationStatus status, int page, int size) {
        // Queue order is fixed here, never taken from the client, so it cannot reference arbitrary columns.
        PageRequest oldestFirst = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt", "id"));
        return jpaRepository.findByVerificationStatus(status, oldestFirst).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public long countByVerificationStatus(VerificationStatus status) {
        return jpaRepository.countByVerificationStatus(status);
    }
}
