package com.agricontract.contract.application.service;

import com.agricontract.contract.application.dto.UserInfo;
import com.agricontract.contract.application.exception.SignerNotEligibleException;
import com.agricontract.contract.application.port.UserPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * Who may start signing (signature design §6 InitiateSign steps 1-2, user-service design §5): a fresh password
 * entry, a verified profile, an authorization that has not ended, no reputation lock, and an email on file to send
 * the code to. user-service is asked directly; if it cannot answer the caller gets an error, never a pass.
 */
@Service
@Slf4j
public class SignerEligibility {
    static final String VERIFIED = "VERIFIED";

    private final UserPort userPort;
    private final Clock clock;
    private final long authMaxAgeSeconds;

    public SignerEligibility(UserPort userPort, Clock clock,
                             @Value("${contract.signature.auth-max-age-seconds}") long authMaxAgeSeconds) {
        this.userPort = userPort;
        this.clock = clock;
        this.authMaxAgeSeconds = authMaxAgeSeconds;
    }

    /** authTime: when the caller last entered the password (JWT auth_time), null if the gateway did not pass it. */
    public void requireFreshLogin(Instant authTime) {
        Instant now = clock.instant();
        if (authTime == null || authTime.isAfter(now.plusSeconds(60))
                || authTime.isBefore(now.minusSeconds(authMaxAgeSeconds))) {
            throw new SignerNotEligibleException(SignerNotEligibleException.STEP_UP_REQUIRED,
                    "Enter your password again to sign (the last login is older than " + authMaxAgeSeconds + "s).");
        }
    }

    /** Returns the profile to email the code to. Throws UserServiceUnavailableException when it cannot check. */
    public UserInfo requireEligible(String userId) {
        Instant now = clock.instant();
        UserInfo user = userPort.findUser(userId).orElseThrow(() -> refuse(userId,
                SignerNotEligibleException.UNKNOWN_USER, "No profile was found for this account."));
        if (!VERIFIED.equals(user.verificationStatus())) {
            throw refuse(userId, SignerNotEligibleException.NOT_VERIFIED,
                    "Your profile has not been verified yet.");
        }
        if (user.authorizationExpiresAt() != null && !now.isBefore(user.authorizationExpiresAt())) {
            throw refuse(userId, SignerNotEligibleException.AUTHORIZATION_EXPIRED,
                    "Your authorization to sign for the organization ended on " + user.authorizationExpiresAt() + ".");
        }
        if (user.lockedUntil() != null && now.isBefore(user.lockedUntil())) {
            throw refuse(userId, SignerNotEligibleException.LOCKED,
                    "Your account is locked from new contracts until " + user.lockedUntil() + ".");
        }
        if (user.email() == null || user.email().isBlank()) {
            throw refuse(userId, SignerNotEligibleException.NO_EMAIL,
                    "Your profile has no email to send the signing code to.");
        }
        return user;
    }

    private static SignerNotEligibleException refuse(String userId, String code, String message) {
        log.warn("Signing refused for {}: {}", userId, code);
        return new SignerNotEligibleException(code, message);
    }
}
