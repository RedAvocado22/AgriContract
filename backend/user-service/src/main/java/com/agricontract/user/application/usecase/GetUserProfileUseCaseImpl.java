package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.UserProfileResponse;
import com.agricontract.user.domain.model.vo.UserId;
import com.agricontract.user.domain.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class GetUserProfileUseCaseImpl implements GetUserProfileUseCase {
    private final UserProfileRepository userProfileRepository;

    @Override
    public UserProfileResponse execute(String userId) {
        return userProfileRepository.findById(new UserId(userId))
                .map(UserProfileResponse::of)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));
    }
}
