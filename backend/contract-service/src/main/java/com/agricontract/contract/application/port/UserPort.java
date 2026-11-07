package com.agricontract.contract.application.port;

import com.agricontract.contract.application.dto.UserInfo;
import com.agricontract.contract.application.exception.UserServiceUnavailableException;

import java.util.Optional;

public interface UserPort {
    UserInfo getUser(String userId);

    /**
     * Fresh read for a high-risk action. Empty when user-service has no such user; any other failure (error answer,
     * timeout, unreachable) throws {@link UserServiceUnavailableException} so the caller can fail closed.
     */
    Optional<UserInfo> findUser(String userId);
}
