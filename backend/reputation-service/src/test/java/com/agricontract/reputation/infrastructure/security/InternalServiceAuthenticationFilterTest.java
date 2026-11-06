package com.agricontract.reputation.infrastructure.security;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.assertThat;

class InternalServiceAuthenticationFilterTest {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void missingOrBlankConfigurationNeverAuthenticates(String configured) throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Internal-Secret", configured == null ? "" : configured);
        new InternalServiceAuthenticationFilter(configured).doFilter(request, new MockHttpServletResponse(),
                (req, res) -> assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void servicePrincipalHasNoEndUserIdentityAndContextIsCleared() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Internal-Secret", "test-secret");
        request.addHeader("X-User-Id", "forged-user");
        request.addHeader("X-User-Role", "ADMIN");
        AtomicReference<Authentication> captured = new AtomicReference<>();
        new InternalServiceAuthenticationFilter("test-secret").doFilter(request, new MockHttpServletResponse(),
                (req, res) -> captured.set(SecurityContextHolder.getContext().getAuthentication()));
        assertThat(captured.get().getPrincipal()).isEqualTo("internal-service");
        assertThat(captured.get().getAuthorities()).extracting(Object::toString).containsExactly("SERVICE_INTERNAL");
        assertThat(captured.get().getCredentials()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
