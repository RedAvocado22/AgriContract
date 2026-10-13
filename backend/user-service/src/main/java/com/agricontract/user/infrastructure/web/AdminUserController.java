package com.agricontract.user.infrastructure.web;

import com.agricontract.user.application.dto.AdminUserResponse;
import com.agricontract.user.application.dto.AssignUserRoleCommand;
import com.agricontract.user.application.dto.ListUsersByVerificationStatusCommand;
import com.agricontract.user.application.dto.PageResult;
import com.agricontract.user.application.dto.RejectUserCommand;
import com.agricontract.user.application.dto.VerifyUserCommand;
import com.agricontract.user.application.usecase.AssignUserRoleUseCase;
import com.agricontract.user.application.usecase.ListUsersByVerificationStatusUseCase;
import com.agricontract.user.application.usecase.RejectUserUseCase;
import com.agricontract.user.application.usecase.VerifyUserUseCase;
import com.agricontract.user.common.ApiResponse;
import com.agricontract.user.common.PaginatedResponse;
import com.agricontract.user.infrastructure.web.dto.AdminUserQueryRequest;
import com.agricontract.user.infrastructure.web.dto.AssignUserRoleRequest;
import com.agricontract.user.infrastructure.web.dto.RejectUserRequest;
import com.agricontract.user.infrastructure.web.dto.VerifyUserRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

// Role checks (OPERATOR for KYC, ADMIN for role assignment) are enforced in SecurityConfig. The actor is the authenticated principal set by
// HeaderAuthenticationFilter, never re-read from the raw header, so decision metadata always matches who was authorized.
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {
    private final ListUsersByVerificationStatusUseCase listUsersByVerificationStatusUseCase;
    private final VerifyUserUseCase verifyUserUseCase;
    private final RejectUserUseCase rejectUserUseCase;
    private final AssignUserRoleUseCase assignUserRoleUseCase;

    @GetMapping
    public ResponseEntity<ApiResponse<PaginatedResponse<AdminUserResponse>>> listUsers(
            @ModelAttribute @Valid AdminUserQueryRequest request
    ) {
        PageResult<AdminUserResponse> result = listUsersByVerificationStatusUseCase.execute(
                new ListUsersByVerificationStatusCommand(request.getVerificationStatus(), request.getPage(), request.getSize()));
        return ResponseEntity.ok(ApiResponse.ok(PaginatedResponse.of(
                result.content(), result.page(), result.size(), result.totalElements())));
    }

    @PostMapping("/{userId}/verify")
    public ResponseEntity<ApiResponse<AdminUserResponse>> verify(
            @PathVariable String userId,
            @RequestBody @Valid VerifyUserRequest req,
            @AuthenticationPrincipal String actorId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(verifyUserUseCase.execute(
                new VerifyUserCommand(userId, actorId, req.authorizationExpiresAt()))));
    }

    @PostMapping("/{userId}/reject")
    public ResponseEntity<ApiResponse<AdminUserResponse>> reject(
            @PathVariable String userId,
            @RequestBody @Valid RejectUserRequest req,
            @AuthenticationPrincipal String actorId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(rejectUserUseCase.execute(
                new RejectUserCommand(userId, actorId, req.reason()))));
    }

    @PutMapping("/{userId}/role")
    public ResponseEntity<ApiResponse<AdminUserResponse>> assignRole(
            @PathVariable String userId,
            @RequestBody @Valid AssignUserRoleRequest req
    ) {
        return ResponseEntity.ok(ApiResponse.ok(assignUserRoleUseCase.execute(
                new AssignUserRoleCommand(userId, req.role()))));
    }
}
