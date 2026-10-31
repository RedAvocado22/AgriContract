package com.agricontract.user.infrastructure.messaging;

import com.agricontract.eventing.EventEnvelope;
import com.agricontract.eventing.outbox.OutboxWriter;
import com.agricontract.user.application.port.KycResultPublisher;
import com.agricontract.user.domain.model.UserProfile;
import com.agricontract.user.domain.model.vo.VerificationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * notification.user_kyc_result_requested (notification-commands.yaml). The decision starts its own chain, so
 * correlationId is the command's eventId and there is no causationId. Times come from the stored decision, not the
 * clock: decidedAt must equal verifiedAt.
 */
@Component
@RequiredArgsConstructor
public class OutboxKycResultPublisher implements KycResultPublisher {

    static final String PRODUCER = "user-service";
    static final String EVENT_TYPE = "notification.user_kyc_result_requested";

    private final OutboxWriter outbox;

    @Override
    public void decided(UserProfile profile) {
        VerificationStatus result = profile.getVerificationStatus();
        if (result != VerificationStatus.VERIFIED && result != VerificationStatus.REJECTED) {
            throw new IllegalStateException("No KYC decision to publish for status " + result);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", profile.getUserId().value());
        payload.put("recipientEmail", profile.getContactInfo().email());
        payload.put("organizationName", profile.getOrganizationName());
        payload.put("result", result.name());
        // reason is optional, not nullable: it is left out entirely for VERIFIED.
        if (result == VerificationStatus.REJECTED) {
            payload.put("reason", profile.getRejectionReason());
        }
        payload.put("decidedAt", profile.getVerifiedAt().toString());

        UUID eventId = UUID.randomUUID();
        outbox.append(new EventEnvelope(eventId, EVENT_TYPE, 1, profile.getVerifiedAt(), PRODUCER,
                profile.getUserId().value(), eventId.toString(), null, null, payload));
    }
}
