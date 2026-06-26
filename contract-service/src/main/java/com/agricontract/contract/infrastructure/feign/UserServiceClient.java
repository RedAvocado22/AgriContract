package com.agricontract.contract.infrastructure.feign;

import com.agricontract.contract.application.dto.UserInfo;
import com.agricontract.contract.common.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "user-service", url = "${user-service.url}", configuration = FeignConfig.class)
public interface UserServiceClient {

    @GetMapping("/api/v1/users/{userId}")
    ApiResponse<UserInfo> getUser(@PathVariable("userId") String userId);
}
