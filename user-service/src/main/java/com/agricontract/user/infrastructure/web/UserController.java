package com.agricontract.user.infrastructure.web;

import com.agricontract.user.application.dto.RegisterUserCommand;
import com.agricontract.user.application.dto.RegisterUserResult;
import com.agricontract.user.application.dto.UserInfoResponse;
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
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final GetUserProfileUseCase getUserProfileUseCase;
    private final RegisterUserUseCase registerUserUseCase;
    private final GetUserInfoUseCase getUserInfoUseCase;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserProfileResponse>> register(
            @RequestBody @Valid RegisterUserRequest req,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Email") String email,
            @RequestHeader("X-User-Role") String rawRoles
    ) {
        Role role = Arrays.stream(rawRoles.split(","))
                .map(String::trim)
                .flatMap(token -> Arrays.stream(Role.values())
                        .filter(r -> r.name().equalsIgnoreCase(token)))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No valid role found in X-User-Role: " + rawRoles));

        RegisterUserResult result = registerUserUseCase.execute(
                new RegisterUserCommand(userId, req.organizationName(), role, email, req.phone(), req.address())
        );

        return result.isNew()
                ? ResponseEntity.status(201).body(ApiResponse.ok(result.profile()))
                : ResponseEntity.ok(ApiResponse.ok(result.profile()));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile(
            @RequestHeader("X-User-Id") String userId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(getUserProfileUseCase.execute(userId)));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserInfoResponse>> getProfile(
            @PathVariable String userId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(getUserInfoUseCase.execute(userId)));
    }

}
