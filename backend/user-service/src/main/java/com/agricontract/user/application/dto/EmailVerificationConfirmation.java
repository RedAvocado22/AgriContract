package com.agricontract.user.application.dto;

// Outcome of a confirm that got as far as checking the code. A wrong code is a result, not an exception, so the
// counted attempt commits like any other write (D44: no noRollbackFor); the web layer turns Rejected into a 400.
public sealed interface EmailVerificationConfirmation {

    record Verified(UserProfileResponse profile) implements EmailVerificationConfirmation {
    }

    record Rejected(int attemptsLeft) implements EmailVerificationConfirmation {
    }
}
