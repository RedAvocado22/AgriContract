package com.agricontract.user.application.dto;

import com.agricontract.user.common.PaginatedRequest;
import com.agricontract.user.domain.model.vo.VerificationStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AdminUserQueryRequest extends PaginatedRequest {
    private VerificationStatus verificationStatus;
}
