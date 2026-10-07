package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.AdminUserResponse;
import com.agricontract.user.domain.model.vo.VerificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ListUsersByVerificationStatusUseCase {
    Page<AdminUserResponse> execute(VerificationStatus status, Pageable pageable);
}
