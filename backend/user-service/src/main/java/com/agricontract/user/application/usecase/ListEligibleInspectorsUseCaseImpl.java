package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.EligibleInspectorResponse;
import com.agricontract.user.domain.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ListEligibleInspectorsUseCaseImpl implements ListEligibleInspectorsUseCase {
    private final UserProfileRepository users;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<EligibleInspectorResponse> execute() {
        return users.findEligibleInspectorIds(clock.instant()).stream()
                .map(EligibleInspectorResponse::new).toList();
    }
}
