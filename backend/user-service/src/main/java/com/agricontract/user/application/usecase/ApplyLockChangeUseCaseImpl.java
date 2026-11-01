package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.ApplyLockChangeCommand;
import com.agricontract.user.application.dto.LockChangeOutcome;
import com.agricontract.user.application.port.LockChangePublisher;
import com.agricontract.user.domain.model.UserProfile;
import com.agricontract.user.domain.model.vo.LockAction;
import com.agricontract.user.domain.model.vo.UserId;
import com.agricontract.user.domain.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lock projection (user-service-phase2-design.md §2.2). reputation-service decides; this only follows its latest
 * revision. The revision check and the write are one UPDATE, so two deliveries racing cannot both apply, and an
 * older revision stays ignored after a restart because the revision is stored, not kept in memory.
 */
@Service
@RequiredArgsConstructor
public class ApplyLockChangeUseCaseImpl implements ApplyLockChangeUseCase {
    private final UserProfileRepository userProfileRepository;
    private final LockChangePublisher lockChangePublisher;

    @Override
    @Transactional
    public LockChangeOutcome execute(ApplyLockChangeCommand command) {
        UserId userId = new UserId(command.userId());
        boolean applied = command.action() == LockAction.LOCKED
                ? userProfileRepository.applyLock(userId, command.until(), command.lockRevision())
                : userProfileRepository.applyUnlock(userId, command.until(), command.lockRevision());
        if (!applied) {
            return userProfileRepository.existsById(userId) ? LockChangeOutcome.STALE : LockChangeOutcome.UNKNOWN_USER;
        }

        UserProfile profile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Profile vanished after lock update: " + userId.value()));
        lockChangePublisher.lockChanged(profile, command);
        return LockChangeOutcome.APPLIED;
    }
}
