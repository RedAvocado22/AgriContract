package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.KycDecisionResponse;
import com.agricontract.user.application.dto.RejectUserCommand;

public interface RejectUserUseCase {
    KycDecisionResponse execute(RejectUserCommand command);
}
