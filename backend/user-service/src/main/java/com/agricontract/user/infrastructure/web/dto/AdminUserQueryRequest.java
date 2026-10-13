package com.agricontract.user.infrastructure.web.dto;

import com.agricontract.user.domain.model.vo.VerificationStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

// Queue order is fixed server-side (oldest registration first), so no sort parameters are accepted.
@Data
public class AdminUserQueryRequest {
    private VerificationStatus verificationStatus;

    @Min(0)
    @Max(10000)
    private int page = 0;

    @Min(1)
    @Max(100)
    private int size = 20;
}
