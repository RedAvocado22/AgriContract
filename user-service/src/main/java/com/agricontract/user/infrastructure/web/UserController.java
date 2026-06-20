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
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Email") String email
    ) {

        if (!StringUtils.hasText(email)) {
            throw new IllegalArgumentException("X-User-Email header is missing");
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
            @RequestHeader("X-User-Id") String userId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(getUserProfileUseCase.execute(userId)));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile(
            @PathVariable String userId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(getUserProfileUseCase.execute(userId)));
    }

}
