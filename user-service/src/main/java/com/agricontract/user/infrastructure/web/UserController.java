package com.agricontract.user.infrastructure.web;

import com.agricontract.user.application.dto.RegisterUserCommand;
import com.agricontract.user.application.dto.UserProfileResponse;
import com.agricontract.user.application.usecase.GetUserProfileUseCase;
import com.agricontract.user.application.usecase.RegisterUserUseCase;
import com.agricontract.user.domain.model.vo.UserId;
import com.agricontract.user.domain.repository.UserProfileRepository;
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
    private final GetUserProfileUseCase getUserProfileUseCase;
    private final RegisterUserUseCase registerUserUseCase;
    private final UserProfileRepository userProfileRepository;

    @PostMapping("/register")
    public ResponseEntity<UserProfileResponse> register(
            @RequestBody RegisterUserRequest req,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String userId = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        boolean exists = userProfileRepository.existsById(new UserId(userId));

        UserProfileResponse result = registerUserUseCase.execute(
                new RegisterUserCommand(userId, req.organizationName(), req.role(), email, req.phone(), req.address())
        );

        return exists
                ? ResponseEntity.ok(result)
                : ResponseEntity.status(201).body(result);
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getMyProfile(
            @AuthenticationPrincipal Jwt jwt
    ) {
        String userId = jwt.getSubject();

        return ResponseEntity.ok().body(getUserProfileUseCase.execute(userId));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserProfileResponse> getProfile(@PathVariable String userId) {
        return ResponseEntity.ok().body(getUserProfileUseCase.execute(userId));
    }
}
