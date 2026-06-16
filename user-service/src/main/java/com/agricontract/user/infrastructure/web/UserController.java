package com.agricontract.user.infrastructure.web;

import com.agricontract.user.application.dto.RegisterUserCommand;
import com.agricontract.user.application.dto.UserProfileResponse;
import com.agricontract.user.application.usecase.GetUserProfileUseCase;
import com.agricontract.user.application.usecase.RegisterUserUseCase;
import com.agricontract.user.domain.model.vo.Role;
import com.agricontract.user.infrastructure.web.dto.RegisterUserRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final RegisterUserUseCase registerUserUseCase;
    private final GetUserProfileUseCase getUserProfileUseCase;

    @PostMapping("/register")
    public ResponseEntity<UserProfileResponse> register(
            @RequestBody RegisterUserRequest req,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String userId = jwt.getSubject();
        String email = jwt.getClaimAsString("email");

        UserProfileResponse response = registerUserUseCase.execute(
                RegisterUserCommand.builder()
                        .userId(userId)
                        .organizationName(req.organizationName())
                        .role(Role.valueOf(req.role().toUpperCase()))
                        .email(email)
                        .phone(req.phone())
                        .address(req.address())
                        .build()
        );
        return ResponseEntity.status(201).body(response);
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getMyProfile(
            @AuthenticationPrincipal Jwt jwt
    ) {
        String userId = jwt.getSubject();
        return ResponseEntity.ok(getUserProfileUseCase.execute(userId));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserProfileResponse> getProfile(@PathVariable String userId) {
        return ResponseEntity.ok(getUserProfileUseCase.execute(userId));
    }
}
