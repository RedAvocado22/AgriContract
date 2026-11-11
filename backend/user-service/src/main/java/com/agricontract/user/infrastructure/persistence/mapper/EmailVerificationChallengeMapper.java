package com.agricontract.user.infrastructure.persistence.mapper;

import com.agricontract.user.domain.model.EmailVerificationChallenge;
import com.agricontract.user.infrastructure.persistence.entity.EmailVerificationChallengeJpaEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface EmailVerificationChallengeMapper {

    EmailVerificationChallengeJpaEntity toJpaEntity(EmailVerificationChallenge domain);

    default EmailVerificationChallenge toDomain(EmailVerificationChallengeJpaEntity entity) {
        return new EmailVerificationChallenge(entity.getUserId(), entity.getEmail(), entity.getCodeHash(),
                entity.getSentAt(), entity.getExpiresAt(), entity.getAttemptsLeft());
    }
}
