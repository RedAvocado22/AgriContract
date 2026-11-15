package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.AdminUserResponse;
import com.agricontract.user.application.dto.AssignUserRoleCommand;
import com.agricontract.user.application.port.TransactionRunner;
import com.agricontract.user.domain.exception.RoleSeparationViolationException;
import com.agricontract.user.domain.model.RoleChangeAuditEntry;
import com.agricontract.user.domain.model.UserProfile;
import com.agricontract.user.domain.model.vo.Role;
import com.agricontract.user.domain.model.vo.RoleChangeOutcome;
import com.agricontract.user.domain.model.vo.UserId;
import com.agricontract.user.domain.repository.RoleChangeAuditRepository;
import com.agricontract.user.domain.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.NoSuchElementException;

// Changes the role in user_db only; syncing the Keycloak realm role is a follow-up (user-service design §4.2b).
// The role update and its audit entry commit together; a no-op assignment writes neither.
// A refused OPERATOR <-> ARBITRATOR assignment is audited with its actor (#261): the domain reports the refusal
// instead of throwing, so the transaction commits that audit row like any other, and the 409 is raised only after
// the commit.
@Service
@RequiredArgsConstructor
public class AssignUserRoleUseCaseImpl implements AssignUserRoleUseCase {
    private final UserProfileRepository userProfileRepository;
    private final RoleChangeAuditRepository roleChangeAuditRepository;
    private final Clock clock;
    private final TransactionRunner transactionRunner;

    @Override
    public AdminUserResponse execute(AssignUserRoleCommand command) {
        Assignment assignment = transactionRunner.inTransaction(() -> assign(command));
        if (assignment.outcome() == RoleChangeOutcome.REJECTED_ROLE_SEPARATION) {
            throw new RoleSeparationViolationException(command.targetUserId(), assignment.profile().role(),
                    command.role());
        }
        return assignment.profile();
    }

    private Assignment assign(AssignUserRoleCommand command) {
        UserProfile profile = userProfileRepository.findById(new UserId(command.targetUserId()))
                .orElseThrow(() -> new NoSuchElementException("User not found: " + command.targetUserId()));

        Role oldRole = profile.getRole();
        RoleChangeOutcome outcome = profile.assignRole(command.actorId(), command.role());
        return switch (outcome) {
            case NO_CHANGE -> new Assignment(outcome, AdminUserResponse.from(profile));
            case REJECTED_ROLE_SEPARATION -> {
                roleChangeAuditRepository.append(RoleChangeAuditEntry.rejectedForSeparation(command.actorId(),
                        command.targetUserId(), oldRole, command.role(), now()));
                yield new Assignment(outcome, AdminUserResponse.from(profile));
            }
            case APPLIED -> {
                UserProfile saved = userProfileRepository.update(profile);
                roleChangeAuditRepository.append(RoleChangeAuditEntry.applied(command.actorId(),
                        command.targetUserId(), oldRole, command.role(), now()));
                yield new Assignment(outcome, AdminUserResponse.from(saved));
            }
        };
    }

    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private record Assignment(RoleChangeOutcome outcome, AdminUserResponse profile) {
    }
}
