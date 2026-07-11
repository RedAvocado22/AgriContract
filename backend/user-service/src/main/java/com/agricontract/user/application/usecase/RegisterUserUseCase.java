package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.RegisterUserCommand;
import com.agricontract.user.application.dto.RegisterUserResult;

public interface RegisterUserUseCase {
    RegisterUserResult execute(RegisterUserCommand command);

}
