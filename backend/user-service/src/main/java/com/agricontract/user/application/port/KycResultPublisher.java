package com.agricontract.user.application.port;

import com.agricontract.user.domain.model.UserProfile;

// Writes notification.user_kyc_result_requested to the outbox. Must run inside the caller's transaction, so no
// email is requested for a decision that rolled back or lost the optimistic-lock race.
public interface KycResultPublisher {

    void decided(UserProfile profile);
}
