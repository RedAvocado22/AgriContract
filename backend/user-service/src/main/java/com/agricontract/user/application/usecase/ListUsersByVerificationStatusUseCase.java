package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.KycQueueItemResponse;
import com.agricontract.user.application.dto.ListUsersByVerificationStatusCommand;
import com.agricontract.user.application.dto.PageResult;

public interface ListUsersByVerificationStatusUseCase {
    PageResult<KycQueueItemResponse> execute(ListUsersByVerificationStatusCommand command);
}
