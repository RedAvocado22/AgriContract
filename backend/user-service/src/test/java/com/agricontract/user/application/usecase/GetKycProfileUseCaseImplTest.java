package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.AdminUserResponse;
import com.agricontract.user.application.dto.GetKycProfileCommand;
import com.agricontract.user.domain.exception.ReadAuditUnavailableException;
import com.agricontract.user.domain.model.ReadAuditEntry;
import com.agricontract.user.domain.model.UserProfile;
import com.agricontract.user.domain.model.vo.AuditResourceType;
import com.agricontract.user.domain.model.vo.ContactInfo;
import com.agricontract.user.domain.model.vo.Role;
import com.agricontract.user.domain.model.vo.UserId;
import com.agricontract.user.domain.repository.ReadAuditRepository;
import com.agricontract.user.domain.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetKycProfileUseCaseImplTest {

    private static final String TARGET_ID = "seller-1";
    private static final String ACTOR_ID = "operator-1";
    private static final Instant NOW = Instant.parse("2026-10-19T03:00:00.123456789Z");

    @Mock private UserProfileRepository userProfileRepository;
    @Mock private ReadAuditRepository readAuditRepository;

    private GetKycProfileUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetKycProfileUseCaseImpl(userProfileRepository, readAuditRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private UserProfile pendingProfile() {
        return UserProfile.create(new UserId(TARGET_ID), "HTX Ca Phe",
                Role.SELLER, new ContactInfo("seller@agricontract.test", "0901234567", null));
    }

    @Test
    void execute_existingUser_auditsReadThenReturnsFullProfile() {
        when(userProfileRepository.findById(new UserId(TARGET_ID))).thenReturn(Optional.of(pendingProfile()));

        AdminUserResponse response = useCase.execute(new GetKycProfileCommand(TARGET_ID, ACTOR_ID, "OPERATOR"));

        ArgumentCaptor<ReadAuditEntry> captor = ArgumentCaptor.forClass(ReadAuditEntry.class);
        verify(readAuditRepository).append(captor.capture());
        ReadAuditEntry entry = captor.getValue();
        assertThat(entry.actorId()).isEqualTo(ACTOR_ID);
        assertThat(entry.actorRole()).isEqualTo("OPERATOR");
        assertThat(entry.resourceType()).isEqualTo(AuditResourceType.KYC_PROFILE);
        assertThat(entry.resourceId()).isEqualTo(TARGET_ID);
        assertThat(entry.caseContext()).isNull();
        assertThat(entry.accessedAt()).isEqualTo(Instant.parse("2026-10-19T03:00:00.123456Z"));

        assertThat(response.userId()).isEqualTo(TARGET_ID);
        assertThat(response.email()).isEqualTo("seller@agricontract.test");
        assertThat(response.phone()).isEqualTo("0901234567");
    }

    @Test
    void execute_unknownUser_notFoundAndNotAudited() {
        when(userProfileRepository.findById(new UserId(TARGET_ID))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new GetKycProfileCommand(TARGET_ID, ACTOR_ID, "OPERATOR")))
                .isInstanceOf(NoSuchElementException.class);

        verifyNoInteractions(readAuditRepository);
    }

    @Test
    void execute_auditWriteFails_propagatesAndReturnsNothing() {
        when(userProfileRepository.findById(new UserId(TARGET_ID))).thenReturn(Optional.of(pendingProfile()));
        doThrow(new ReadAuditUnavailableException(TARGET_ID, new RuntimeException("db down")))
                .when(readAuditRepository).append(any());

        assertThatThrownBy(() -> useCase.execute(new GetKycProfileCommand(TARGET_ID, ACTOR_ID, "OPERATOR")))
                .isInstanceOf(ReadAuditUnavailableException.class);
    }
}
