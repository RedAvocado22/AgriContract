package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.AdminUserResponse;
import com.agricontract.user.application.dto.RejectUserCommand;
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
public class RejectUserUseCaseImpl implements RejectUserUseCase {
    private final UserProfileRepository userProfileRepository;
    private final Clock clock;

    @Override
    @Transactional
    public AdminUserResponse execute(RejectUserCommand command) {
        UserProfile profile = userProfileRepository.findById(new UserId(command.targetUserId()))
                .orElseThrow(() -> new NoSuchElementException("User not found: " + command.targetUserId()));

        profile.reject(command.actorId(), Instant.now(clock).truncatedTo(ChronoUnit.MICROS), command.reason());

        UserProfile saved = userProfileRepository.update(profile);
        // TODO: publish notification.user_kyc_result_requested via outbox once user-service has AMQP
        return AdminUserResponse.from(saved);
    }
}
