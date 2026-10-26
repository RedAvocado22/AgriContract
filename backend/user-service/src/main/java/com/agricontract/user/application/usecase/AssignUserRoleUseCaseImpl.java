package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.AdminUserResponse;
import com.agricontract.user.application.dto.AssignUserRoleCommand;
import com.agricontract.user.domain.model.UserProfile;
import com.agricontract.user.domain.model.vo.UserId;
import com.agricontract.user.domain.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

// Changes the role in user_db only; syncing the Keycloak realm role is a follow-up (user-service design §4.2b).
@Service
@RequiredArgsConstructor
public class AssignUserRoleUseCaseImpl implements AssignUserRoleUseCase {
    private final UserProfileRepository userProfileRepository;

    @Override
    @Transactional
    public AdminUserResponse execute(AssignUserRoleCommand command) {
        UserProfile profile = userProfileRepository.findById(new UserId(command.targetUserId()))
                .orElseThrow(() -> new NoSuchElementException("User not found: " + command.targetUserId()));

        if (!profile.assignRole(command.role())) {
            return AdminUserResponse.from(profile);
        }
        return AdminUserResponse.from(userProfileRepository.update(profile));
    }
}
