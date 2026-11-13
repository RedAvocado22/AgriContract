package com.agricontract.keycloak;

import org.jboss.logging.Logger;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserModel;

import java.util.Set;

/**
 * On a self-registration (REGISTER), gives the new user the realm role named by the {@code accountType} attribute
 * they chose on the registration page (D47). Only BUYER and SELLER can be chosen; staff roles are never granted
 * here. A missing or unknown value grants nothing (user-service then refuses /users/register: fail closed).
 * Runs inside the registration request, so the first token issued afterwards already carries the role.
 */
public class RegistrationRoleEventListener implements EventListenerProvider {

    static final String ATTRIBUTE = "accountType";
    static final Set<String> SELF_SERVICE_ROLES = Set.of("BUYER", "SELLER");
    private static final Logger LOG = Logger.getLogger(RegistrationRoleEventListener.class);

    private final KeycloakSession session;

    public RegistrationRoleEventListener(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public void onEvent(Event event) {
        if (event.getType() != EventType.REGISTER || event.getUserId() == null) {
            return;
        }
        RealmModel realm = session.realms().getRealm(event.getRealmId());
        UserModel user = realm == null ? null : session.users().getUserById(realm, event.getUserId());
        if (user == null) {
            LOG.warnf("REGISTER event for unknown user %s in realm %s", event.getUserId(), event.getRealmId());
            return;
        }
        String chosen = user.getFirstAttribute(ATTRIBUTE);
        if (chosen == null || !SELF_SERVICE_ROLES.contains(chosen)) {
            LOG.warnf("User %s registered with accountType '%s'; no role granted", user.getId(), chosen);
            return;
        }
        RoleModel role = realm.getRole(chosen);
        if (role == null) {
            LOG.errorf("Realm %s has no role %s; user %s registered without a role", realm.getName(), chosen,
                    user.getId());
            return;
        }
        if (!user.hasRole(role)) {
            user.grantRole(role);
            LOG.infof("User %s registered as %s", user.getId(), chosen);
        }
    }

    @Override
    public void onEvent(AdminEvent event, boolean includeRepresentation) {
        // Accounts created by an admin get their roles from the admin, not from this listener.
    }

    @Override
    public void close() {
    }
}
