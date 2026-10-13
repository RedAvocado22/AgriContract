package com.agricontract.user.infrastructure.web;

import com.agricontract.user.application.dto.InternalUserInfoResponse;
import com.agricontract.user.application.usecase.GetInternalUserInfoUseCase;
import com.agricontract.user.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Not routed through the gateway; HeaderAuthenticationFilter requires X-Internal-Secret for /internal/**.
@RestController
@RequestMapping("/internal/v1/users")
@RequiredArgsConstructor
public class InternalUserController {
    private final GetInternalUserInfoUseCase getInternalUserInfoUseCase;

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<InternalUserInfoResponse>> getUser(@PathVariable String userId) {
        return ResponseEntity.ok(ApiResponse.ok(getInternalUserInfoUseCase.execute(userId)));
    }
}
