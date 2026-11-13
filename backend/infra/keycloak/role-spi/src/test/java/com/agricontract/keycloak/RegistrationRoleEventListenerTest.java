package com.agricontract.keycloak;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.keycloak.events.Event;
import org.keycloak.events.EventType;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RealmProvider;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserProvider;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegistrationRoleEventListenerTest {

    private final KeycloakSession session = mock(KeycloakSession.class);
    private final RealmModel realm = mock(RealmModel.class);
    private final UserModel user = mock(UserModel.class);
    private final RoleModel buyer = mock(RoleModel.class);
    private final RoleModel seller = mock(RoleModel.class);
    private final RoleModel operator = mock(RoleModel.class);
    private final RegistrationRoleEventListener listener = new RegistrationRoleEventListener(session);

    @BeforeEach
    void realmWithOneNewUser() {
        RealmProvider realms = mock(RealmProvider.class);
        UserProvider users = mock(UserProvider.class);
        when(session.realms()).thenReturn(realms);
        when(session.users()).thenReturn(users);
        when(realms.getRealm("realm-1")).thenReturn(realm);
        when(users.getUserById(realm, "user-1")).thenReturn(user);
        when(user.getId()).thenReturn("user-1");
        when(realm.getRole("BUYER")).thenReturn(buyer);
        when(realm.getRole("SELLER")).thenReturn(seller);
        when(realm.getRole("OPERATOR")).thenReturn(operator);
    }

    @Test
    void sellerChoiceGrantsSeller() {
        when(user.getFirstAttribute("accountType")).thenReturn("SELLER");
        listener.onEvent(event(EventType.REGISTER));
        verify(user).grantRole(seller);
        verify(user, never()).grantRole(buyer);
    }

    @Test
    void buyerChoiceGrantsBuyer() {
        when(user.getFirstAttribute("accountType")).thenReturn("BUYER");
        listener.onEvent(event(EventType.REGISTER));
        verify(user).grantRole(buyer);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"OPERATOR", "ADMIN", "ARBITRATOR", "INSPECTOR", "buyer", "BUYER "})
    void missingUnknownOrStaffChoiceGrantsNothing(String chosen) {
        when(user.getFirstAttribute("accountType")).thenReturn(chosen);
        listener.onEvent(event(EventType.REGISTER));
        verify(user, never()).grantRole(any());
    }

    @Test
    void otherEventsAreIgnored() {
        when(user.getFirstAttribute("accountType")).thenReturn("SELLER");
        listener.onEvent(event(EventType.LOGIN));
        listener.onEvent(event(EventType.UPDATE_PROFILE));
        verify(user, never()).grantRole(any());
    }

    @Test
    void roleAlreadyHeldIsNotGrantedTwice() {
        when(user.getFirstAttribute("accountType")).thenReturn("BUYER");
        when(user.hasRole(buyer)).thenReturn(true);
        listener.onEvent(event(EventType.REGISTER));
        verify(user, never()).grantRole(any());
    }

    @Test
    void realmWithoutTheRoleGrantsNothing() {
        when(user.getFirstAttribute("accountType")).thenReturn("SELLER");
        when(realm.getRole("SELLER")).thenReturn(null);
        listener.onEvent(event(EventType.REGISTER));
        verify(user, never()).grantRole(any());
    }

    private static Event event(EventType type) {
        Event event = new Event();
        event.setType(type);
        event.setRealmId("realm-1");
        event.setUserId("user-1");
        return event;
    }
}
