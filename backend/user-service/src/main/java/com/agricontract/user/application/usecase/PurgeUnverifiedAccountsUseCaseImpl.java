package com.agricontract.user.application.usecase;

import com.agricontract.user.application.exception.IdentityProviderUnavailableException;
import com.agricontract.user.application.port.IdentityAccounts;
import com.agricontract.user.application.port.TransactionRunner;
import com.agricontract.user.domain.model.UnverifiedAccountPolicy;
import com.agricontract.user.domain.model.UserProfile;
import com.agricontract.user.domain.model.vo.UserId;
import com.agricontract.user.domain.repository.EmailVerificationChallengeRepository;
import com.agricontract.user.domain.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Deletes self-registered accounts whose email was not verified in time (D59, design §2.3).
 * <p>
 * Candidates come from Keycloak, so an account that never reached the profile form is found too. Keycloak's flag is
 * only a copy: before deleting, the profile row is locked and read. If it says verified, Keycloak missed the update
 * at confirm time; the flag is set again and nothing is deleted. Otherwise the profile rows go and the Keycloak
 * account is deleted last, inside the same transaction, so a Keycloak failure keeps the rows. A confirm locks the
 * same row first, so it either finishes before (and the account is kept) or waits and then finds no profile.
 * One account failing does not stop the others; the next run tries it again.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PurgeUnverifiedAccountsUseCaseImpl implements PurgeUnverifiedAccountsUseCase {
    private final IdentityAccounts accounts;
    private final UserProfileRepository userProfileRepository;
    private final EmailVerificationChallengeRepository challengeRepository;
    private final UnverifiedAccountPolicy policy;
    private final Clock clock;
    private final TransactionRunner transactionRunner;

    @Override
    public int execute() {
        Instant cutoff = policy.deletionCutoff(Instant.now(clock));
        List<String> candidates;
        try {
            candidates = accounts.unverifiedSelfRegisteredCreatedBefore(cutoff);
        } catch (IdentityProviderUnavailableException e) {
            log.warn("event=unverified_account_purge_skipped reason=keycloak_unavailable");
            return 0;
        }
        int deleted = 0;
        for (String userId : candidates) {
            try {
                if (purge(userId)) {
                    deleted++;
                }
            } catch (RuntimeException e) {
                log.warn("event=unverified_account_purge_failed userId={} reason={}", userId,
                        e.getClass().getSimpleName());
            }
        }
        return deleted;
    }

    // True when the account was deleted, false when user-service knows it is verified.
    private boolean purge(String userId) {
        boolean verified = transactionRunner.inTransaction(() -> {
            Optional<UserProfile> profile = userProfileRepository.findByIdForUpdate(new UserId(userId));
            if (profile.isPresent() && profile.get().isEmailVerified()) {
                return true;
            }
            if (profile.isPresent()) {
                challengeRepository.deleteByUserId(userId);
                userProfileRepository.deleteById(profile.get().getUserId());
            }
            accounts.delete(userId);
            return false;
        });
        if (verified) {
            accounts.markEmailVerified(userId);
            log.info("event=keycloak_email_verified_resynced userId={}", userId);
            return false;
        }
        log.info("event=unverified_account_deleted userId={}", userId);
        return true;
    }
}
