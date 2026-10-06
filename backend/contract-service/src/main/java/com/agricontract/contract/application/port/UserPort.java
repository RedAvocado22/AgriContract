package com.agricontract.contract.application.port;

import com.agricontract.contract.application.dto.UserInfo;

public interface UserPort {
    UserInfo getUser(String userId);
}
