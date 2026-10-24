package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.PublicUserResponse;
import com.agricontract.user.domain.model.UserProfile;
import com.agricontract.user.domain.model.vo.ContactInfo;
import com.agricontract.user.domain.model.vo.Role;
import com.agricontract.user.domain.model.vo.UserId;
import com.agricontract.user.domain.model.vo.VerificationStatus;
import com.agricontract.user.domain.repository.UserProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetUserInfoUseCaseImplTest {

    private static final String USER_ID = "seller-1";

    @Mock private UserProfileRepository userProfileRepository;

    @InjectMocks private GetUserInfoUseCaseImpl useCase;

    @Test
    void execute_returnsPublicFieldsOnly() {
        when(userProfileRepository.findById(new UserId(USER_ID))).thenReturn(Optional.of(UserProfile.create(
                new UserId(USER_ID), "HTX Ca Phe", Role.SELLER,
                new ContactInfo("seller@agricontract.test", "0900000000", "Dak Lak"))));

        PublicUserResponse response = useCase.execute(USER_ID);

        assertThat(response).isEqualTo(new PublicUserResponse(USER_ID, "HTX Ca Phe", Role.SELLER, VerificationStatus.PENDING));
    }

    @Test
    void execute_unknownUser_throwsNotFound() {
        when(userProfileRepository.findById(new UserId(USER_ID))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(USER_ID)).isInstanceOf(NoSuchElementException.class);
    }
}
