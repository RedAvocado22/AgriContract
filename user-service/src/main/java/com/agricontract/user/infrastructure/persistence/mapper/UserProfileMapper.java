package com.agricontract.user.infrastructure.persistence.mapper;

import com.agricontract.user.domain.model.UserProfile;
import com.agricontract.user.infrastructure.persistence.entity.UserProfileJpaEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserProfileMapper {

    UserProfileJpaEntity toJpaEntity(UserProfile domain);

    UserProfile toDomain(UserProfileJpaEntity entity);
}
