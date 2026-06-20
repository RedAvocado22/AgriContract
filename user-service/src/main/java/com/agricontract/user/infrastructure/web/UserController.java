package com.agricontract.user.infrastructure.web;

import com.agricontract.user.application.dto.RegisterUserCommand;
import com.agricontract.user.application.dto.RegisterUserResult;
import com.agricontract.user.application.dto.UserProfileResponse;
import com.agricontract.user.application.usecase.GetUserProfileUseCase;
import com.agricontract.user.application.usecase.RegisterUserUseCase;
import com.agricontract.user.common.ApiResponse;
import com.agricontract.user.infrastructure.web.dto.RegisterUserRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final GetUserProfileUseCase getUserProfileUseCase;
    private final RegisterUserUseCase registerUserUseCase;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserProfileResponse>> register(
            @RequestBody @Valid RegisterUserRequest req,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String userId = jwt.getSubject();
        String email = jwt.getClaimAsString("email");

        if (!StringUtils.hasText(email)) {
            throw new IllegalArgumentException("email claim is missing from JWT token");
        }

        RegisterUserResult result = registerUserUseCase.execute(
                new RegisterUserCommand(userId, req.organizationName(), req.role(), email, req.phone(), req.address())
        );


        return result.isNew()
                ? ResponseEntity.status(201).body(ApiResponse.ok(result.profile()))
                : ResponseEntity.ok(ApiResponse.ok(result.profile()));


    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile(
            @AuthenticationPrincipal Jwt jwt
    ) {
        String userId = jwt.getSubject();

        return ResponseEntity.ok(ApiResponse.ok(getUserProfileUseCase.execute(userId)));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile(
            @PathVariable String userId,
            @RequestHeader(value = "X-Internal-Call", required = false) String internalCall
    ) {
        if (!"true".equals(internalCall)) {
            return ResponseEntity.status(403).body(ApiResponse.error("Forbidden"));
        }
        return ResponseEntity.ok(ApiResponse.ok(getUserProfileUseCase.execute(userId)));
    }

}
