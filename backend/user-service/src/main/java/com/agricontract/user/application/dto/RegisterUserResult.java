package com.agricontract.user.application.dto;

import java.time.Instant;

// verificationCodeExpiresAt: set when a new profile's first code was emailed, null when it was not (the client offers
// a resend) and always null for an existing profile, which gets no new code.
public record RegisterUserResult(UserProfileResponse profile, boolean isNew, Instant verificationCodeExpiresAt) {

    public static RegisterUserResult existing(UserProfileResponse profile) {
        return new RegisterUserResult(profile, false, null);
    }

    public static RegisterUserResult created(UserProfileResponse profile, Instant verificationCodeExpiresAt) {
        return new RegisterUserResult(profile, true, verificationCodeExpiresAt);
    }

    public boolean verificationEmailSent() {
        return verificationCodeExpiresAt != null;
    }
}
