package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.AdminUserResponse;
import com.agricontract.user.domain.model.vo.VerificationStatus;
import com.agricontract.user.domain.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListUsersByVerificationStatusUseCaseImpl implements ListUsersByVerificationStatusUseCase {
    private final UserProfileRepository userProfileRepository;

    // No status given means the review queue, i.e. PENDING.
    @Override
    public Page<AdminUserResponse> execute(VerificationStatus status, Pageable pageable) {
        VerificationStatus effectiveStatus = status != null ? status : VerificationStatus.PENDING;
        List<AdminUserResponse> content = userProfileRepository.findByVerificationStatus(effectiveStatus, pageable).stream()
                .map(AdminUserResponse::from)
                .toList();
        long total = userProfileRepository.countByVerificationStatus(effectiveStatus);
        return new PageImpl<>(content, pageable, total);
    }
}
