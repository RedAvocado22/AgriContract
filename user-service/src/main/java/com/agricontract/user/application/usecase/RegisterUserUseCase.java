package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.RegisterUserCommand;
import com.agricontract.user.application.dto.UserProfileResponse;

public interface RegisterUserUseCase {
    UserProfileResponse execute(RegisterUserCommand command);
}
