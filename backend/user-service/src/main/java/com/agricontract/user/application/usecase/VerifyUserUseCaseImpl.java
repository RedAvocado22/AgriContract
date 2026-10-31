package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.KycDecisionResponse;
import com.agricontract.user.application.dto.VerifyUserCommand;
import com.agricontract.user.application.port.KycResultPublisher;
import com.agricontract.user.domain.model.UserProfile;
import com.agricontract.user.domain.model.vo.UserId;
import com.agricontract.user.domain.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class VerifyUserUseCaseImpl implements VerifyUserUseCase {
    private final UserProfileRepository userProfileRepository;
    private final KycResultPublisher kycResultPublisher;
    private final Clock clock;

    @Override
    @Transactional
    public KycDecisionResponse execute(VerifyUserCommand command) {
        UserProfile profile = userProfileRepository.findById(new UserId(command.targetUserId()))
                .orElseThrow(() -> new NoSuchElementException("User not found: " + command.targetUserId()));

        profile.verify(command.actorId(), Instant.now(clock).truncatedTo(ChronoUnit.MICROS), command.authorizationExpiresAt());

        UserProfile saved = userProfileRepository.update(profile);
        // Same transaction as the update: a 409 or a rollback leaves no command behind.
        kycResultPublisher.decided(saved);
        return KycDecisionResponse.from(saved);
    }
}
