package com.agricontract.user.infrastructure.web;

import com.agricontract.user.application.dto.AdminUserResponse;
import com.agricontract.user.application.dto.AssignUserRoleCommand;
import com.agricontract.user.application.dto.GetKycProfileCommand;
import com.agricontract.user.application.dto.KycDecisionResponse;
import com.agricontract.user.application.dto.KycQueueItemResponse;
import com.agricontract.user.application.dto.ListUsersByVerificationStatusCommand;
import com.agricontract.user.application.dto.PageResult;
import com.agricontract.user.application.dto.RejectUserCommand;
import com.agricontract.user.application.dto.VerifyUserCommand;
import com.agricontract.user.application.usecase.AssignUserRoleUseCase;
import com.agricontract.user.application.usecase.GetKycProfileUseCase;
import com.agricontract.user.application.usecase.ListUsersByVerificationStatusUseCase;
import com.agricontract.user.application.usecase.RejectUserUseCase;
import com.agricontract.user.application.usecase.VerifyUserUseCase;
import com.agricontract.user.common.ApiResponse;
import com.agricontract.user.common.PaginatedResponse;
import com.agricontract.user.domain.model.vo.Role;
import com.agricontract.user.infrastructure.web.dto.AdminUserQueryRequest;
import com.agricontract.user.infrastructure.web.dto.AssignUserRoleRequest;
import com.agricontract.user.infrastructure.web.dto.RejectUserRequest;
import com.agricontract.user.infrastructure.web.dto.VerifyUserRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

// Role checks (OPERATOR for KYC, ADMIN for role assignment) are enforced in SecurityConfig. The actor is the authenticated principal set by
// HeaderAuthenticationFilter, never re-read from the raw header, so decision and audit metadata always match who was authorized.
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {
    private static final String ROLE_PREFIX = "ROLE_";
    private static final Set<String> APP_ROLES = Arrays.stream(Role.values()).map(Enum::name).collect(Collectors.toSet());

    private final ListUsersByVerificationStatusUseCase listUsersByVerificationStatusUseCase;
    private final GetKycProfileUseCase getKycProfileUseCase;
    private final VerifyUserUseCase verifyUserUseCase;
    private final RejectUserUseCase rejectUserUseCase;
    private final AssignUserRoleUseCase assignUserRoleUseCase;

    @GetMapping
    public ResponseEntity<ApiResponse<PaginatedResponse<KycQueueItemResponse>>> listUsers(
            @ModelAttribute @Valid AdminUserQueryRequest request
    ) {
        PageResult<KycQueueItemResponse> result = listUsersByVerificationStatusUseCase.execute(
                new ListUsersByVerificationStatusCommand(request.getVerificationStatus(), request.getPage(), request.getSize()));
        return ResponseEntity.ok(ApiResponse.ok(PaginatedResponse.of(
                result.content(), result.page(), result.size(), result.totalElements())));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<AdminUserResponse>> getKycProfile(
            @PathVariable String userId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(ApiResponse.ok(getKycProfileUseCase.execute(
                new GetKycProfileCommand(userId, authentication.getName(), appRoles(authentication)))));
    }

    @PostMapping("/{userId}/verify")
    public ResponseEntity<ApiResponse<KycDecisionResponse>> verify(
            @PathVariable String userId,
            @RequestBody @Valid VerifyUserRequest req,
            @AuthenticationPrincipal String actorId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(verifyUserUseCase.execute(
                new VerifyUserCommand(userId, actorId, req.authorizationExpiresAt()))));
    }

    @PostMapping("/{userId}/reject")
    public ResponseEntity<ApiResponse<KycDecisionResponse>> reject(
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

    // Application roles from the token, sorted, without Keycloak defaults such as default-roles-agricontract.
    private static String appRoles(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith(ROLE_PREFIX))
                .map(authority -> authority.substring(ROLE_PREFIX.length()))
                .filter(APP_ROLES::contains)
                .sorted()
                .collect(Collectors.joining(","));
    }
}
