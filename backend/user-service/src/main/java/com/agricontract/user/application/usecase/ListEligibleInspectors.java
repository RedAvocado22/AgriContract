package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.EligibleInspectorResponse;
import com.agricontract.user.domain.repository.UserProfileRepository;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListEligibleInspectors {
    private final UserProfileRepository users;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<EligibleInspectorResponse> execute() {
        return users.findEligibleInspectorIds(clock.instant()).stream()
                .map(EligibleInspectorResponse::new).toList();
    }
}
