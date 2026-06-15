package com.agricontract.user.infrastructure.persistence.mapper;

import com.agricontract.user.domain.model.UserProfile;
import com.agricontract.user.domain.model.vo.ContactInfo;
import com.agricontract.user.domain.model.vo.UserId;
import com.agricontract.user.infrastructure.persistence.entity.UserProfileJpaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserProfileMapper {

    @Mapping(target = "userId", source = "userId.value")
    @Mapping(target = "email", source = "contactInfo.email")
    @Mapping(target = "phone", source = "contactInfo.phone")
    @Mapping(target = "address", source = "contactInfo.address")
    UserProfileJpaEntity toJpaEntity(UserProfile domain);

    default UserProfile toDomain(UserProfileJpaEntity entity) {
        return UserProfile.reconstitute(
                new UserId(entity.getUserId()),
                entity.getOrganizationName(),
                entity.getRole(),
                new ContactInfo(entity.getEmail(), entity.getPhone(), entity.getAddress()),
                entity.getVerificationStatus()
        );
    }
}
