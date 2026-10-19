package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.KycQueueItemResponse;
import com.agricontract.user.application.dto.ListUsersByVerificationStatusCommand;
import com.agricontract.user.application.dto.PageResult;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListUsersByVerificationStatusUseCaseImplTest {

    @Mock private UserProfileRepository userProfileRepository;

    @InjectMocks private ListUsersByVerificationStatusUseCaseImpl useCase;

    private UserProfile pendingProfile() {
        return UserProfile.create(new UserId("seller-1"), "HTX Ca Phe",
                Role.SELLER, new ContactInfo("seller@agricontract.test", null, null));
    }

    @Test
    void execute_noStatus_defaultsToPendingQueue() {
        when(userProfileRepository.findByVerificationStatus(VerificationStatus.PENDING, 0, 20))
                .thenReturn(List.of(pendingProfile()));
        when(userProfileRepository.countByVerificationStatus(VerificationStatus.PENDING)).thenReturn(1L);

        PageResult<KycQueueItemResponse> result = useCase.execute(new ListUsersByVerificationStatusCommand(null, 0, 20));

        assertThat(result.content()).extracting(KycQueueItemResponse::userId).containsExactly("seller-1");
        assertThat(result.page()).isZero();
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    void execute_givenStatusAndPage_passesThemToRepository() {
        when(userProfileRepository.findByVerificationStatus(VerificationStatus.REJECTED, 2, 5)).thenReturn(List.of());
        when(userProfileRepository.countByVerificationStatus(VerificationStatus.REJECTED)).thenReturn(10L);

        PageResult<KycQueueItemResponse> result = useCase.execute(
                new ListUsersByVerificationStatusCommand(VerificationStatus.REJECTED, 2, 5));

        assertThat(result.content()).isEmpty();
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.size()).isEqualTo(5);
        assertThat(result.totalElements()).isEqualTo(10);
    }
}
