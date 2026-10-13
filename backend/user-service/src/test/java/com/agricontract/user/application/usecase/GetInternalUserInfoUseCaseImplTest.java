package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.InternalUserInfoResponse;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetInternalUserInfoUseCaseImplTest {

    @Mock private UserProfileRepository userProfileRepository;
    @InjectMocks private GetInternalUserInfoUseCaseImpl useCase;

    @Test
    void execute_existingUser_returnsContactEmailFromProfile() {
        UserProfile profile = UserProfile.create(new UserId("user-1"), "Nong Trai ABC", Role.SELLER,
                new ContactInfo("seller@test.com", "0901234567", "Ha Noi"));
        when(userProfileRepository.findById(new UserId("user-1"))).thenReturn(Optional.of(profile));

        InternalUserInfoResponse response = useCase.execute("user-1");

        assertThat(response.userId()).isEqualTo("user-1");
        assertThat(response.organizationName()).isEqualTo("Nong Trai ABC");
        assertThat(response.email()).isEqualTo("seller@test.com");
        assertThat(response.role()).isEqualTo(Role.SELLER);
        assertThat(response.verificationStatus()).isEqualTo(VerificationStatus.PENDING);
    }

    @Test
    void execute_unknownUser_throwsNotFound() {
        when(userProfileRepository.findById(new UserId("missing"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("missing"))
                .isInstanceOf(NoSuchElementException.class);
    }
}
