package com.agricontract.user.infrastructure.persistence.mapper;

import com.agricontract.user.domain.model.UserProfile;
import com.agricontract.user.domain.model.vo.ContactInfo;
import com.agricontract.user.infrastructure.persistence.entity.UserProfileJpaEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserProfileMapper {

    default UserProfileJpaEntity toJpaEntity(UserProfile domain) {
        return UserProfileJpaEntity.builder()
                .userId(domain.getUserId().value())
                .organizationName(domain.getOrganizationName())
                .role(domain.getRole())
                .email(domain.getContactInfo().email())
                .phone(domain.getContactInfo().phone())
                .address(domain.getContactInfo().address())
                .verificationStatus(domain.getVerificationStatus())
                .build();
    }

    default UserProfile toDomain(UserProfileJpaEntity entity) {
        return UserProfile.reconstitute(
                entity.getUserId(),
                entity.getOrganizationName(),
                entity.getRole(),
                new ContactInfo(entity.getEmail(), entity.getPhone(), entity.getAddress()),
                entity.getVerificationStatus()
        );
    }
}
