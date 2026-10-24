package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.KycDecisionResponse;
import com.agricontract.user.application.dto.VerifyUserCommand;

public interface VerifyUserUseCase {
    KycDecisionResponse execute(VerifyUserCommand command);
}
