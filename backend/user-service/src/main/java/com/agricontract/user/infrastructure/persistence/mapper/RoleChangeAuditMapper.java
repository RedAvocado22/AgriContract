package com.agricontract.user.infrastructure.persistence.mapper;

import com.agricontract.user.domain.model.RoleChangeAuditEntry;
import com.agricontract.user.infrastructure.persistence.entity.RoleChangeAuditJpaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface RoleChangeAuditMapper {

    @Mapping(target = "id", ignore = true)
    RoleChangeAuditJpaEntity toJpaEntity(RoleChangeAuditEntry domain);
}
