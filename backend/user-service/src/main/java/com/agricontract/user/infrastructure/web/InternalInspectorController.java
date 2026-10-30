package com.agricontract.user.infrastructure.web;

import com.agricontract.user.application.dto.EligibleInspectorResponse;
import com.agricontract.user.application.usecase.ListEligibleInspectors;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class InternalInspectorController {
    private final ListEligibleInspectors inspectors;

    @GetMapping("/internal/v1/users/inspectors/eligible")
    public List<EligibleInspectorResponse> eligible() {
        return inspectors.execute();
    }
}
