package com.agricontract.contract.infrastructure.feign;

import com.agricontract.contract.application.dto.UserInfo;
import com.agricontract.contract.application.port.UserPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserPortAdapter implements UserPort {

    private final UserServiceClient userServiceClient;

    @Override
    public UserInfo getUser(String userId) {
        return userServiceClient.getUser(userId).getData();
    }
}
