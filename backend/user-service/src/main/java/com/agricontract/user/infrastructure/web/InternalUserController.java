package com.agricontract.user.infrastructure.web;

import com.agricontract.user.application.dto.InternalUserInfoResponse;
import com.agricontract.user.application.usecase.GetInternalUserInfoUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Service-to-service only: HeaderAuthenticationFilter requires X-Internal-Secret and the gateway never routes /internal/**.
// The body is the bare DTO, not ApiResponse, because callers read the fields at the top level.
@RestController
@RequestMapping("/internal/v1/users")
@RequiredArgsConstructor
public class InternalUserController {
    private final GetInternalUserInfoUseCase getInternalUserInfoUseCase;

    @GetMapping("/{userId}")
    public ResponseEntity<InternalUserInfoResponse> getUser(@PathVariable String userId) {
        return ResponseEntity.ok(getInternalUserInfoUseCase.execute(userId));
    }
}
