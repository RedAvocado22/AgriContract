package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.EmailVerificationSentResponse;
import com.agricontract.user.application.service.EmailVerificationCodeIssuer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// Resends the code (design §4.4); register sends the first one. A new code replaces the pending one.
@Service
@RequiredArgsConstructor
public class RequestEmailVerificationUseCaseImpl implements RequestEmailVerificationUseCase {
    private final EmailVerificationCodeIssuer issuer;

    @Override
    public EmailVerificationSentResponse execute(String userId) {
        return issuer.issue(userId);
    }
}
