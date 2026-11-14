package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.EligibleInspectorResponse;
import com.agricontract.user.domain.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;

// One query, so no transaction: there is no snapshot to keep consistent across reads.
@Service
@RequiredArgsConstructor
public class ListEligibleInspectorsUseCaseImpl implements ListEligibleInspectorsUseCase {
    private final UserProfileRepository users;
    private final Clock clock;

    @Override
    public List<EligibleInspectorResponse> execute() {
        return users.findEligibleInspectorIds(clock.instant()).stream()
                .map(EligibleInspectorResponse::new).toList();
    }
}
