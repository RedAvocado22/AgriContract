package com.agricontract.contract.infrastructure.feign;

import com.agricontract.contract.application.dto.UserInfo;
import com.agricontract.contract.application.exception.UserServiceUnavailableException;
import com.agricontract.contract.application.port.UserPort;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class UserPortAdapter implements UserPort {

    private final UserServiceClient userServiceClient;

    @Override
    public UserInfo getUser(String userId) {
        return userServiceClient.getUser(userId);
    }

    @Override
    public Optional<UserInfo> findUser(String userId) {
        try {
            return Optional.ofNullable(userServiceClient.getUser(userId));
        } catch (FeignException.NotFound missing) {
            return Optional.empty();
        } catch (FeignException failed) {
            throw new UserServiceUnavailableException("user-service did not answer for " + userId
                    + " (HTTP " + failed.status() + ")", failed);
        }
    }
}
