package com.agricontract.user.infrastructure.web;

import com.agricontract.user.application.dto.EmailVerificationSentResponse;
import com.agricontract.user.application.dto.RegisterUserCommand;
import com.agricontract.user.application.usecase.ConfirmEmailVerificationUseCase;
import com.agricontract.user.application.usecase.RequestEmailVerificationUseCase;
import com.agricontract.user.infrastructure.web.dto.ConfirmEmailVerificationRequest;
import com.agricontract.user.application.dto.RegisterUserResult;
import com.agricontract.user.application.dto.PublicUserResponse;
import com.agricontract.user.application.dto.UserProfileResponse;
import com.agricontract.user.application.usecase.GetUserInfoUseCase;
import com.agricontract.user.application.usecase.GetUserProfileUseCase;
import com.agricontract.user.application.usecase.RegisterUserUseCase;
import com.agricontract.user.common.ApiResponse;
import com.agricontract.user.domain.model.vo.Role;
import com.agricontract.user.infrastructure.web.dto.RegisterUserRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final GetUserProfileUseCase getUserProfileUseCase;
    private final RegisterUserUseCase registerUserUseCase;
    private final GetUserInfoUseCase getUserInfoUseCase;
    private final RequestEmailVerificationUseCase requestEmailVerificationUseCase;
    private final ConfirmEmailVerificationUseCase confirmEmailVerificationUseCase;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserProfileResponse>> register(
            @RequestBody @Valid RegisterUserRequest req,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String rawRoles
    ) {
        Role role = Arrays.stream(rawRoles.split(","))
                .map(String::trim)
                .flatMap(token -> Arrays.stream(Role.values())
                        .filter(r -> r.name().equalsIgnoreCase(token)))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No valid role found in X-User-Role: " + rawRoles));

        RegisterUserResult result = registerUserUseCase.execute(
                new RegisterUserCommand(userId, req.organizationName(), role, req.email(), req.phone(), req.address())
        );

        return result.isNew()
                ? ResponseEntity.status(201).body(ApiResponse.ok(result.profile()))
                : ResponseEntity.ok(ApiResponse.ok(result.profile()));
    }

    // The owner is the authenticated principal set by HeaderAuthenticationFilter, never the raw X-User-Id header:
    // an internal-secret call carries no principal and must not read a full profile by sending any X-User-Id.
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile(
            @AuthenticationPrincipal String userId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(getUserProfileUseCase.execute(userId)));
    }

    // Emails a code to the caller's contact address (design §4.4).
    @PostMapping("/me/email-verification")
    public ResponseEntity<ApiResponse<EmailVerificationSentResponse>> requestEmailVerification(
            @AuthenticationPrincipal String userId
    ) {
        return ResponseEntity.status(202).body(ApiResponse.ok(requestEmailVerificationUseCase.execute(userId)));
    }

    @PostMapping("/me/email-verification/confirm")
    public ResponseEntity<ApiResponse<UserProfileResponse>> confirmEmailVerification(
            @AuthenticationPrincipal String userId,
            @RequestBody @Valid ConfirmEmailVerificationRequest req
    ) {
        return ResponseEntity.ok(ApiResponse.ok(confirmEmailVerificationUseCase.execute(userId, req.code())));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<PublicUserResponse>> getProfile(
            @PathVariable String userId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(getUserInfoUseCase.execute(userId)));
    }

}
