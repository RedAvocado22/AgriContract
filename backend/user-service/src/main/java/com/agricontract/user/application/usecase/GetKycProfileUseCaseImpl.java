package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.AdminUserResponse;
import com.agricontract.user.application.dto.GetKycProfileCommand;
import com.agricontract.user.domain.model.ReadAuditEntry;
import com.agricontract.user.domain.model.UserProfile;
import com.agricontract.user.domain.model.vo.UserId;
import com.agricontract.user.domain.repository.ReadAuditRepository;
import com.agricontract.user.domain.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class GetKycProfileUseCaseImpl implements GetKycProfileUseCase {
    private final UserProfileRepository userProfileRepository;
    private final ReadAuditRepository readAuditRepository;
    private final Clock clock;

    // Audit first, data second: if the entry cannot be stored the exception propagates and nothing is returned.
    // An unknown user is not audited, since no profile data was read.
    @Override
    public AdminUserResponse execute(GetKycProfileCommand command) {
        UserProfile profile = userProfileRepository.findById(new UserId(command.targetUserId()))
                .orElseThrow(() -> new NoSuchElementException("User not found: " + command.targetUserId()));

        readAuditRepository.append(ReadAuditEntry.kycProfileRead(command.actorId(), command.actorRole(),
                command.targetUserId(), Instant.now(clock).truncatedTo(ChronoUnit.MICROS)));

        return AdminUserResponse.from(profile);
    }
}
