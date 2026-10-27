package com.agricontract.contract.infrastructure.feign;

import com.agricontract.contract.application.dto.UserInfo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "user-service", url = "${user-service.url}", configuration = FeignConfig.class)
public interface UserServiceClient {

    // Service-to-service read (getInternalUserInfo): bare body, not ApiResponse-wrapped. The public
    // /api/v1/users/{userId} carries no email and requires an end-user principal.
    @GetMapping("/internal/v1/users/{userId}")
    UserInfo getUser(@PathVariable("userId") String userId);
}
