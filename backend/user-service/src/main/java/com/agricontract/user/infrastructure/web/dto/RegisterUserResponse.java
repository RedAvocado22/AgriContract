package com.agricontract.user.infrastructure.web.dto;

import com.agricontract.user.application.dto.RegisterUserResult;
import com.agricontract.user.application.dto.UserProfileResponse;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.time.Instant;

// The own profile, plus for a new profile whether the first verification code was emailed (design §4.4).
// Both verification fields are left out for an existing profile, which gets no new code.
public record RegisterUserResponse(
        @JsonUnwrapped UserProfileResponse profile,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean verificationEmailSent,
        @JsonInclude(JsonInclude.Include.NON_NULL) Instant verificationCodeExpiresAt
) {
    public static RegisterUserResponse from(RegisterUserResult result) {
        return result.isNew()
                ? new RegisterUserResponse(result.profile(), result.verificationEmailSent(),
                        result.verificationCodeExpiresAt())
                : new RegisterUserResponse(result.profile(), null, null);
    }
}
