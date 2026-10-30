package com.agricontract.user.infrastructure.web;

import com.agricontract.user.application.usecase.GetInternalUserInfoUseCase;
import com.agricontract.user.application.usecase.ListEligibleInspectorsUseCase;
import com.agricontract.user.infrastructure.config.HeaderAuthenticationFilter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InternalInspectorBlankSecretTest {
    private static final String PATH = "/internal/v1/users/inspectors/eligible";

    @ParameterizedTest
    @ValueSource(strings = {"", " \t "})
    void blankConfiguredSecretNeverGrantsInternalAccess(String configuredSecret) throws Exception {
        var inspectors = mock(ListEligibleInspectorsUseCase.class);
        var mvc = MockMvcBuilders.standaloneSetup(new InternalUserController(mock(GetInternalUserInfoUseCase.class), inspectors))
                .addFilters(new HeaderAuthenticationFilter("gateway", configuredSecret))
                .build();

        mvc.perform(get(PATH).header("X-Internal-Secret", configuredSecret))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mvc.perform(get(PATH).header("X-Internal-Secret", "wrong"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(PATH).header("X-Gateway-Secret", "gateway"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(inspectors);
    }
}
