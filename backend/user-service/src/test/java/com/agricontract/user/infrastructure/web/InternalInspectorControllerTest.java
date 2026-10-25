package com.agricontract.user.infrastructure.web;

import com.agricontract.user.application.dto.EligibleInspectorResponse;
import com.agricontract.user.application.usecase.ListEligibleInspectors;
import com.agricontract.user.infrastructure.config.SecurityConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InternalInspectorController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {"gateway.internal-secret=gateway", "service.internal-secret=service"})
class InternalInspectorControllerTest {
    private static final String PATH = "/internal/v1/users/inspectors/eligible";
    @Autowired MockMvc mvc;
    @MockBean ListEligibleInspectors inspectors;

    @Test void onlyServiceCredentialReturnsMinimalIdentityArray() throws Exception {
        when(inspectors.execute()).thenReturn(List.of(new EligibleInspectorResponse("1349fbf6-44b6-450c-b01d-4b5abaf7c326")));
        mvc.perform(get(PATH).header("X-Internal-Secret", "service")).andExpect(status().isOk())
                .andExpect(content().json("[{\"userId\":\"1349fbf6-44b6-450c-b01d-4b5abaf7c326\"}]", true));
    }
    @Test void publicJwtAndGatewayRolesCannotReadPool() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isForbidden());
        mvc.perform(get(PATH).header("Authorization", "Bearer end-user-token")).andExpect(status().isForbidden());
        for (String role : List.of("ADMIN", "OPERATOR", "ARBITRATOR", "INSPECTOR")) {
            mvc.perform(get(PATH).header("X-Gateway-Secret", "gateway")
                    .header("X-User-Id", "actor").header("X-User-Role", role)).andExpect(status().isForbidden());
        }
        mvc.perform(get(PATH).header("X-Internal-Secret", "wrong")).andExpect(status().isForbidden());
        mvc.perform(get(PATH).header("X-Internal-Secret", "service", "wrong")).andExpect(status().isForbidden());
        verifyNoInteractions(inspectors);
    }

    @Test void contextPathDoesNotTurnGatewayIdentityIntoServiceAuthority() throws Exception {
        mvc.perform(get("/service" + PATH).contextPath("/service")
                .header("X-Gateway-Secret", "gateway").header("X-User-Id", "actor")
                .header("X-User-Role", "ADMIN")).andExpect(status().isForbidden());
        verifyNoInteractions(inspectors);
        when(inspectors.execute()).thenReturn(List.of());
        mvc.perform(get("/service" + PATH).contextPath("/service")
                .header("X-Internal-Secret", "service")).andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }
}
