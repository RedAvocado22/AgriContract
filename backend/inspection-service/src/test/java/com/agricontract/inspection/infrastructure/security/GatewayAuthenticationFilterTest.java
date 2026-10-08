package com.agricontract.inspection.infrastructure.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.*;

class GatewayAuthenticationFilterTest {
    private final GatewayAuthenticationFilter filter = new GatewayAuthenticationFilter("gateway-test-secret");

    @AfterEach
    void clearContext() { SecurityContextHolder.clearContext(); }

    @Test
    void validatedGatewayIdentityPopulatesContextAndIsClearedAfterRequest() throws Exception {
        var request = identityRequest();
        request.addHeader("X-Gateway-Secret", "gateway-test-secret");
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) ->
                assertThat(new SecurityContextInspector().requireInspectorId()).isEqualTo("inspector-a"));
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "forged-secret"})
    void clientHeadersDoNotAuthenticateWithoutValidGatewaySecret(String secret) throws Exception {
        var request = identityRequest();
        if (!secret.isEmpty()) request.addHeader("X-Gateway-Secret", secret);
        request.addHeader("X-Internal-Secret", "gateway-test-secret");
        var response = new MockHttpServletResponse();
        var reached = new AtomicBoolean();
        filter.doFilter(request, response, (req, res) -> reached.set(true));
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(reached).isFalse();
    }

    @Test
    void headersNeverOverrideExistingAuthenticatedIdentity() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "inspector-b", null, List.of(new SimpleGrantedAuthority("ROLE_INSPECTOR"))));
        filter.doFilter(identityRequest(), new MockHttpServletResponse(), (req, res) ->
                assertThat(new SecurityContextInspector().requireInspectorId()).isEqualTo("inspector-b"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"X-User-Id", "X-User-Role", "X-Gateway-Secret"})
    void ambiguousIdentityHeadersAreRejected(String header) throws Exception {
        var request = identityRequest();
        request.addHeader("X-Gateway-Secret", "gateway-test-secret");
        request.addHeader(header, "extra-value");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> fail("Ambiguous identity reached application"));
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void noDefaultOrBlankGatewaySecret() {
        assertThatThrownBy(() -> new GatewayAuthenticationFilter(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    private MockHttpServletRequest identityRequest() {
        var request = new MockHttpServletRequest();
        request.setServletPath("/api/v1/inspections/contract/milestones/milestone/reports");
        request.addHeader("X-User-Id", "inspector-a");
        request.addHeader("X-User-Role", "INSPECTOR");
        return request;
    }
}
