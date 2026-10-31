package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.AdminUserResponse;
import com.agricontract.user.application.dto.AssignUserRoleCommand;
import com.agricontract.user.domain.model.RoleChangeAuditEntry;
import com.agricontract.user.domain.model.UserProfile;
import com.agricontract.user.domain.model.vo.Role;
import com.agricontract.user.domain.model.vo.UserId;
import com.agricontract.user.domain.repository.RoleChangeAuditRepository;
import com.agricontract.user.domain.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.NoSuchElementException;

// Changes the role in user_db only; syncing the Keycloak realm role is a follow-up (user-service design §4.2b).
// The role update and its audit entry commit together; a no-op assignment writes neither.
@Service
@RequiredArgsConstructor
public class AssignUserRoleUseCaseImpl implements AssignUserRoleUseCase {
    private final UserProfileRepository userProfileRepository;
    private final RoleChangeAuditRepository roleChangeAuditRepository;
    private final Clock clock;

    @Override
    @Transactional
    public AdminUserResponse execute(AssignUserRoleCommand command) {
        UserProfile profile = userProfileRepository.findById(new UserId(command.targetUserId()))
                .orElseThrow(() -> new NoSuchElementException("User not found: " + command.targetUserId()));

        Role oldRole = profile.getRole();
        if (!profile.assignRole(command.actorId(), command.role())) {
            return AdminUserResponse.from(profile);
        }
        UserProfile saved = userProfileRepository.update(profile);
        roleChangeAuditRepository.append(new RoleChangeAuditEntry(command.actorId(), command.targetUserId(),
                oldRole, command.role(), Instant.now(clock).truncatedTo(ChronoUnit.MICROS)));
        return AdminUserResponse.from(saved);
    }
}
