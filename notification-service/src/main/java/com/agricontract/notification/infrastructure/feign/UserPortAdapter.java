package com.agricontract.notification.infrastructure.feign;

import com.agricontract.notification.application.port.UserPort;
import com.agricontract.notification.application.port.dto.UserInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserPortAdapter implements UserPort {

    private final UserServiceClient userServiceClient;

    @Override
    public UserInfo getUser(String userId) {
        try {
            return userServiceClient.getUser(userId).getData();
        } catch (Exception e) {
            log.warn("Failed to fetch user info for userId={}: {}", userId, e.getMessage());
            return null;
        }
    }
}
