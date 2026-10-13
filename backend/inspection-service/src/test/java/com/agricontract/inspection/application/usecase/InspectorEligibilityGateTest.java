package com.agricontract.inspection.application.usecase;

import com.agricontract.inspection.application.exception.EligibilityUnavailableException;
import com.agricontract.inspection.application.exception.InspectorNotEligibleException;
import com.agricontract.inspection.application.port.InspectorEligibilityPort;
import com.agricontract.inspection.domain.model.InspectorEligibility;
import com.agricontract.inspection.infrastructure.security.SecurityContextInspector;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class InspectorEligibilityGateTest {
    private static final Instant NOW = Instant.parse("2026-10-08T03:00:00Z");
    private static final String ACTOR = "1349fbf6-44b6-450c-b01d-4b5abaf7c326";
    private final InspectorEligibilityPort users = mock(InspectorEligibilityPort.class);
    private final InspectorEligibilityGate gate = new InspectorEligibilityGate(
            new SecurityContextInspector(), users, Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void authenticatedInspector() {
        authenticate("INSPECTOR");
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void verifiedInspectorWithUnexpiredLicensePassesEligibilityOnly() {
        when(users.load(ACTOR)).thenReturn(profile("INSPECTOR", "VERIFIED", NOW.plusSeconds(1)));
        assertThat(gate.requireEligibleInspector()).isEqualTo(ACTOR);
        verify(users).load(ACTOR);
    }

    @Test
    void explicitUnlimitedLicenseIsAllowed() {
        when(users.load(ACTOR)).thenReturn(profile("INSPECTOR", "VERIFIED", null));
        assertThat(gate.requireEligibleInspector()).isEqualTo(ACTOR);
    }

    @ParameterizedTest
    @ValueSource(strings = {"BUYER", "SELLER", "ADMIN", "OPERATOR", "ARBITRATOR"})
    void otherAuthenticatedRolesCannotUseInspectorGate(String role) {
        authenticate(role);
        assertThatThrownBy(gate::requireEligibleInspector).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(users);
    }

    @ParameterizedTest
    @ValueSource(strings = {"BUYER", "SELLER", "ADMIN", "OPERATOR", "ARBITRATOR"})
    void authoritativeProfileMustAlsoHaveInspectorRole(String role) {
        when(users.load(ACTOR)).thenReturn(profile(role, "VERIFIED", null));
        assertThatThrownBy(gate::requireEligibleInspector).isInstanceOf(InspectorNotEligibleException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "REJECTED", "UNKNOWN"})
    void unverifiedInspectorIsDenied(String status) {
        when(users.load(ACTOR)).thenReturn(profile("INSPECTOR", status, null));
        assertThatThrownBy(gate::requireEligibleInspector).isInstanceOf(InspectorNotEligibleException.class);
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 0})
    void expiryAtOrBeforeNowIsDenied(long offset) {
        when(users.load(ACTOR)).thenReturn(profile("INSPECTOR", "VERIFIED", NOW.plusSeconds(offset)));
        assertThatThrownBy(gate::requireEligibleInspector).isInstanceOf(InspectorNotEligibleException.class);
    }

    @Test
    void dependencyFailureFailsClosed() {
        when(users.load(ACTOR)).thenThrow(new EligibilityUnavailableException());
        assertThatThrownBy(gate::requireEligibleInspector).isInstanceOf(EligibilityUnavailableException.class);
    }

    @Test
    void missingProfileFailsClosed() {
        assertThatThrownBy(gate::requireEligibleInspector).isInstanceOf(EligibilityUnavailableException.class);
    }

    @Test
    void mismatchedProfileFailsClosed() {
        when(users.load(ACTOR)).thenReturn(new InspectorEligibility("someone-else", "INSPECTOR", "VERIFIED", null));
        assertThatThrownBy(gate::requireEligibleInspector).isInstanceOf(EligibilityUnavailableException.class);
    }

    @Test
    void missingAuthenticationIsDenied() {
        SecurityContextHolder.clearContext();
        assertThatThrownBy(gate::requireEligibleInspector)
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        verifyNoInteractions(users);
    }

    @Test
    void unauthenticatedPrincipalIsDenied() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(ACTOR, null));
        assertThatThrownBy(gate::requireEligibleInspector)
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void anonymousPrincipalIsDeniedEvenWithInspectorAuthority() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "test", ACTOR, List.of(new SimpleGrantedAuthority("ROLE_INSPECTOR"))));
        assertThatThrownBy(gate::requireEligibleInspector)
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    private void authenticate(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                ACTOR, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private InspectorEligibility profile(String role, String status, Instant expiry) {
        return new InspectorEligibility(ACTOR, role, status, expiry);
    }
}
