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

    @ParameterizedTest
    @org.junit.jupiter.params.provider.NullAndEmptySource
    @ValueSource(strings = {" ", "\t", " \t "})
    void incompleteRoleDoesNotEstablishIdentityOrInvokeChain(String role) throws Exception {
        var request = identityRequest();
        request.removeHeader("X-User-Role");
        if (role != null) request.addHeader("X-User-Role", role);
        request.addHeader("X-Gateway-Secret", "gateway-test-secret");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> fail("Incomplete identity reached application"));
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void arbitratorIsRecognizedButCannotActAsInspector() throws Exception {
        var request = identityRequest();
        request.removeHeader("X-User-Role");
        request.addHeader("X-User-Role", "ARBITRATOR");
        request.addHeader("X-Gateway-Secret", "gateway-test-secret");
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                    .extracting("authority").containsExactly("ROLE_ARBITRATOR");
            assertThatThrownBy(() -> new SecurityContextInspector().requireInspectorId())
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        });
    }

    @Test
    void validMultipleRolesAreTrimmedAndDeduplicated() throws Exception {
        var request = identityRequest();
        request.removeHeader("X-User-Role");
        request.addHeader("X-User-Role", " BUYER, INSPECTOR,INSPECTOR ");
        request.addHeader("X-Gateway-Secret", "gateway-test-secret");
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                    .extracting("authority").containsExactly("ROLE_BUYER", "ROLE_INSPECTOR");
            assertThat(new SecurityContextInspector().requireInspectorId()).isEqualTo("inspector-a");
        });
    }

    @Test
    void healthBypassesIdentityValidation() throws Exception {
        var request = new MockHttpServletRequest("GET", "/actuator/health");
        var reached = new AtomicBoolean();
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> reached.set(true));
        assertThat(reached).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private MockHttpServletRequest identityRequest() {
        var request = new MockHttpServletRequest();
        request.setServletPath("/api/v1/inspections/contract/milestones/milestone/reports");
        request.addHeader("X-User-Id", "inspector-a");
        request.addHeader("X-User-Role", "INSPECTOR");
        return request;
    }
}
