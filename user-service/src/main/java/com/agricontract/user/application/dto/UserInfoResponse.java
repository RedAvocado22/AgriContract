package com.agricontract.user.application.dto;

import com.agricontract.user.domain.model.vo.Role;
import lombok.Builder;

@Builder
public record UserInfoResponse(String userId,
                               String organizationName,
                               String email,
                               Role role
) {
}
