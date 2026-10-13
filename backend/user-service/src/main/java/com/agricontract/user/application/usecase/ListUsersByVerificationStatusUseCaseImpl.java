package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.AdminUserResponse;
import com.agricontract.user.application.dto.ListUsersByVerificationStatusCommand;
import com.agricontract.user.application.dto.PageResult;
import com.agricontract.user.domain.model.vo.VerificationStatus;
import com.agricontract.user.domain.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListUsersByVerificationStatusUseCaseImpl implements ListUsersByVerificationStatusUseCase {
    private final UserProfileRepository userProfileRepository;

    // No status given means the review queue, i.e. PENDING.
    @Override
    public PageResult<AdminUserResponse> execute(ListUsersByVerificationStatusCommand command) {
        VerificationStatus effectiveStatus = command.status() != null ? command.status() : VerificationStatus.PENDING;
        List<AdminUserResponse> content = userProfileRepository
                .findByVerificationStatus(effectiveStatus, command.page(), command.size()).stream()
                .map(AdminUserResponse::from)
                .toList();
        long total = userProfileRepository.countByVerificationStatus(effectiveStatus);
        return new PageResult<>(content, command.page(), command.size(), total);
    }
}
