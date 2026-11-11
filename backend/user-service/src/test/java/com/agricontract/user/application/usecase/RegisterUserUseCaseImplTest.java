package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.RegisterUserCommand;
import com.agricontract.user.application.dto.RegisterUserResult;
import com.agricontract.user.domain.exception.EmailAlreadyClaimedException;
import com.agricontract.user.domain.model.UserProfile;
import com.agricontract.user.domain.model.vo.ContactInfo;
import com.agricontract.user.domain.model.vo.Role;
import com.agricontract.user.domain.model.vo.UserId;
import com.agricontract.user.domain.repository.UserProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisterUserUseCaseImplTest {

    private static final UserId USER_ID = new UserId("user-1");

    @Mock private UserProfileRepository userProfileRepository;
    @InjectMocks private RegisterUserUseCaseImpl useCase;

    private static RegisterUserCommand command(String email) {
        return new RegisterUserCommand("user-1", "HTX Ca Phe", Role.SELLER, email, null, null);
    }

    @Test
    void newUser_storedWithUnverifiedEmail() {
        when(userProfileRepository.findById(USER_ID)).thenReturn(Optional.empty());
        when(userProfileRepository.isEmailVerifiedByAnotherUser("seller@example.com", USER_ID)).thenReturn(false);
        when(userProfileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RegisterUserResult result = useCase.execute(command("seller@example.com"));

        assertThat(result.isNew()).isTrue();
        assertThat(result.profile().email()).isEqualTo("seller@example.com");
        assertThat(result.profile().emailVerified()).isFalse();
    }

    @Test
    void newUser_addressVerifiedByAnotherUser_refused() {
        when(userProfileRepository.findById(USER_ID)).thenReturn(Optional.empty());
        when(userProfileRepository.isEmailVerifiedByAnotherUser("taken@example.com", USER_ID)).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(command("taken@example.com")))
                .isInstanceOf(EmailAlreadyClaimedException.class);
        verify(userProfileRepository, never()).save(any());
    }

    @Test
    void existingUser_returnedUnchanged_withoutAddressCheck() {
        UserProfile existing = UserProfile.create(USER_ID, "HTX Ca Phe", Role.SELLER,
                new ContactInfo("first@example.com", null, null));
        when(userProfileRepository.findById(USER_ID)).thenReturn(Optional.of(existing));

        RegisterUserResult result = useCase.execute(command("other@example.com"));

        assertThat(result.isNew()).isFalse();
        assertThat(result.profile().email()).isEqualTo("first@example.com");
        verify(userProfileRepository, never()).isEmailVerifiedByAnotherUser(any(), any());
        verify(userProfileRepository, never()).save(any());
    }
}
