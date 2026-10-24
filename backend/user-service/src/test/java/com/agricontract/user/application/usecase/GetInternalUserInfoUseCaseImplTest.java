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

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetInternalUserInfoUseCaseImplTest {

    private static final UserId USER_ID = new UserId("seller-1");
    private static final Instant EXPIRES_AT = Instant.parse("2027-06-30T16:59:59.999Z");
    private static final Instant LOCKED_UNTIL = Instant.parse("2026-11-01T00:00:00Z");

    @Mock private UserProfileRepository userProfileRepository;
    @InjectMocks private GetInternalUserInfoUseCaseImpl useCase;

    @Test
    void execute_returnsAllEligibilityFacts() {
        when(userProfileRepository.findById(USER_ID)).thenReturn(Optional.of(profile(
                VerificationStatus.VERIFIED, EXPIRES_AT, LOCKED_UNTIL)));

        InternalUserInfoResponse response = useCase.execute("seller-1");

        assertThat(response).isEqualTo(new InternalUserInfoResponse("seller-1", "HTX Ca Phe",
                "seller@agricontract.test", Role.SELLER, VerificationStatus.VERIFIED, EXPIRES_AT, LOCKED_UNTIL));
    }

    @Test
    void execute_keepsUnlimitedAuthorizationAndNoLockAsNull() {
        when(userProfileRepository.findById(USER_ID)).thenReturn(Optional.of(profile(
                VerificationStatus.PENDING, null, null)));

        InternalUserInfoResponse response = useCase.execute("seller-1");

        assertThat(response.verificationStatus()).isEqualTo(VerificationStatus.PENDING);
        assertThat(response.authorizationExpiresAt()).isNull();
        assertThat(response.lockedUntil()).isNull();
    }

    @Test
    void execute_unknownUser_throwsNotFound() {
        when(userProfileRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("seller-1")).isInstanceOf(NoSuchElementException.class);
    }

    private static UserProfile profile(VerificationStatus status, Instant expiresAt, Instant lockedUntil) {
        return UserProfile.reconstitute(USER_ID, "HTX Ca Phe", Role.SELLER,
                new ContactInfo("seller@agricontract.test", "0900000000", "Dak Lak"),
                status, expiresAt, "operator-1", Instant.parse("2026-10-07T03:00:00Z"), null, lockedUntil);
    }
}
