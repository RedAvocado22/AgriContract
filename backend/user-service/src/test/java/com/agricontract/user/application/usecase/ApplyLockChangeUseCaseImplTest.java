package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.ApplyLockChangeCommand;
import com.agricontract.user.application.dto.LockChangeOutcome;
import com.agricontract.user.application.port.LockChangePublisher;
import com.agricontract.user.domain.model.UserProfile;
import com.agricontract.user.domain.model.vo.ContactInfo;
import com.agricontract.user.domain.model.vo.LockAction;
import com.agricontract.user.domain.model.vo.Role;
import com.agricontract.user.domain.model.vo.UserId;
import com.agricontract.user.domain.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplyLockChangeUseCaseImplTest {

    private static final String USER_ID = "7d4c2f1e-3b5a-4c6d-8e9f-0a1b2c3d4e5f";
    private static final UserId ID = new UserId(USER_ID);
    private static final Instant UNTIL = Instant.parse("2026-12-31T16:59:59.999Z");

    @Mock private UserProfileRepository repository;
    @Mock private LockChangePublisher publisher;

    private ApplyLockChangeUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new ApplyLockChangeUseCaseImpl(repository, publisher);
    }

    @Test
    void appliedLock_publishesWithTheReloadedProfile() {
        ApplyLockChangeCommand command = command(LockAction.LOCKED, UNTIL, 3);
        UserProfile reloaded = profile(UNTIL);
        when(repository.applyLock(ID, UNTIL, 3)).thenReturn(true);
        when(repository.findById(ID)).thenReturn(Optional.of(reloaded));

        assertThat(useCase.execute(command)).isEqualTo(LockChangeOutcome.APPLIED);

        verify(publisher).lockChanged(reloaded, command);
        verify(repository, never()).applyUnlock(any(), any(), anyLong());
    }

    @Test
    void appliedUnlock_usesTheUnlockStatement() {
        ApplyLockChangeCommand command = command(LockAction.UNLOCKED, null, 4);
        UserProfile reloaded = profile(null);
        when(repository.applyUnlock(ID, null, 4)).thenReturn(true);
        when(repository.findById(ID)).thenReturn(Optional.of(reloaded));

        assertThat(useCase.execute(command)).isEqualTo(LockChangeOutcome.APPLIED);

        verify(publisher).lockChanged(reloaded, command);
        verify(repository, never()).applyLock(any(), any(), anyLong());
    }

    @Test
    void olderRevision_isStaleAndPublishesNothing() {
        when(repository.applyLock(ID, UNTIL, 1)).thenReturn(false);
        when(repository.existsById(ID)).thenReturn(true);

        assertThat(useCase.execute(command(LockAction.LOCKED, UNTIL, 1))).isEqualTo(LockChangeOutcome.STALE);

        verifyNoInteractions(publisher);
    }

    @Test
    void unknownUser_publishesNothing() {
        when(repository.applyUnlock(ID, UNTIL, 2)).thenReturn(false);
        when(repository.existsById(ID)).thenReturn(false);

        assertThat(useCase.execute(command(LockAction.UNLOCKED, UNTIL, 2))).isEqualTo(LockChangeOutcome.UNKNOWN_USER);

        verifyNoInteractions(publisher);
    }

    private static ApplyLockChangeCommand command(LockAction action, Instant until, long revision) {
        return new ApplyLockChangeCommand("0b6f5c3e-9d1a-4e2b-8c7d-6a5b4c3d2e1f",
                "0b6f5c3e-9d1a-4e2b-8c7d-6a5b4c3d2e1f", USER_ID, action, until, "SIDE_SELLING", revision);
    }

    private static UserProfile profile(Instant lockedUntil) {
        UserProfile created = UserProfile.create(ID, "HTX Ca Phe", Role.SELLER,
                new ContactInfo("seller@example.com", null, null));
        return UserProfile.reconstitute(ID, created.getOrganizationName(), created.getRole(),
                created.getContactInfo(), created.getVerificationStatus(), null, null, null, null, lockedUntil);
    }
}
