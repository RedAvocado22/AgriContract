package com.agricontract.user.infrastructure.persistence.mapper;

import com.agricontract.user.domain.model.ReadAuditEntry;
import com.agricontract.user.infrastructure.persistence.entity.ReadAuditJpaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ReadAuditMapper {

    @Mapping(target = "id", ignore = true)
    ReadAuditJpaEntity toJpaEntity(ReadAuditEntry domain);
}
