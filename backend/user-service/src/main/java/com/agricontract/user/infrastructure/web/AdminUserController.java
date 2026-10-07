package com.agricontract.user.infrastructure.web;

import com.agricontract.user.application.dto.AdminUserQueryRequest;
import com.agricontract.user.application.dto.AdminUserResponse;
import com.agricontract.user.application.dto.RejectUserCommand;
import com.agricontract.user.application.dto.VerifyUserCommand;
import com.agricontract.user.application.usecase.ListUsersByVerificationStatusUseCase;
import com.agricontract.user.application.usecase.RejectUserUseCase;
import com.agricontract.user.application.usecase.VerifyUserUseCase;
import com.agricontract.user.common.ApiResponse;
import com.agricontract.user.common.PaginatedResponse;
import com.agricontract.user.infrastructure.web.dto.RejectUserRequest;
import com.agricontract.user.infrastructure.web.dto.VerifyUserRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// Role check (ADMIN|OPERATOR) is enforced in SecurityConfig; the actor is always the gateway-injected X-User-Id.
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {
    private final ListUsersByVerificationStatusUseCase listUsersByVerificationStatusUseCase;
    private final VerifyUserUseCase verifyUserUseCase;
    private final RejectUserUseCase rejectUserUseCase;

    @GetMapping
    public ResponseEntity<ApiResponse<PaginatedResponse<AdminUserResponse>>> listUsers(
            @ModelAttribute @Valid AdminUserQueryRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(PaginatedResponse.from(
                listUsersByVerificationStatusUseCase.execute(request.getVerificationStatus(), request.toPageable()))));
    }

    @PostMapping("/{userId}/verify")
    public ResponseEntity<ApiResponse<AdminUserResponse>> verify(
            @PathVariable String userId,
            @RequestBody VerifyUserRequest req,
            @RequestHeader("X-User-Id") String actorId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(verifyUserUseCase.execute(
                new VerifyUserCommand(userId, actorId, req.authorizationExpiresAt()))));
    }

    @PostMapping("/{userId}/reject")
    public ResponseEntity<ApiResponse<AdminUserResponse>> reject(
            @PathVariable String userId,
            @RequestBody @Valid RejectUserRequest req,
            @RequestHeader("X-User-Id") String actorId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(rejectUserUseCase.execute(
                new RejectUserCommand(userId, actorId, req.reason()))));
    }
}
