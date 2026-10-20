package com.agricontract.user.infrastructure.persistence.mapper;

import com.agricontract.user.domain.model.UserProfile;
import com.agricontract.user.domain.model.vo.ContactInfo;
import com.agricontract.user.domain.model.vo.UserId;
import com.agricontract.user.infrastructure.persistence.entity.UserProfileJpaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface UserProfileMapper {

    @Mapping(target = "userId", source = "userId.value")
    @Mapping(target = "email", source = "contactInfo.email")
    @Mapping(target = "phone", source = "contactInfo.phone")
    @Mapping(target = "address", source = "contactInfo.address")
    @Mapping(target = "version", ignore = true)
    UserProfileJpaEntity toJpaEntity(UserProfile domain);

    // Copies mutable state onto an already-managed entity; identity and audit columns are never overwritten.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "organizationName", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    // The lock projection is written only by the reputation event consumer, never by KYC/profile saves.
    @Mapping(target = "lockedUntil", ignore = true)
    @Mapping(target = "email", source = "contactInfo.email")
    @Mapping(target = "phone", source = "contactInfo.phone")
    @Mapping(target = "address", source = "contactInfo.address")
    void updateEntity(UserProfile domain, @MappingTarget UserProfileJpaEntity entity);

    default UserProfile toDomain(UserProfileJpaEntity entity) {
        return UserProfile.reconstitute(
                new UserId(entity.getUserId()),
                entity.getOrganizationName(),
                entity.getRole(),
                new ContactInfo(entity.getEmail(), entity.getPhone(), entity.getAddress()),
                entity.getVerificationStatus(),
                entity.getAuthorizationExpiresAt(),
                entity.getVerifiedByActorId(),
                entity.getVerifiedAt(),
                entity.getRejectionReason(),
                entity.getLockedUntil()
        );
    }
}
