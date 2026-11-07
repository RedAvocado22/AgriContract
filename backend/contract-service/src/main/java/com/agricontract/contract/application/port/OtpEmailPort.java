package com.agricontract.contract.application.port;

import com.agricontract.contract.application.dto.OtpEmail;
import com.agricontract.contract.application.exception.OtpEmailNotSentException;

public interface OtpEmailPort {

    /**
     * Returns only when notification-service confirms the mail server accepted the email. Any other outcome
     * (error response, timeout, connection failure) throws {@link OtpEmailNotSentException}; the email may still
     * arrive, so the caller must make the code unusable. Never retried with the same requestId.
     */
    void send(OtpEmail email);
}
