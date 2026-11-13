package com.agricontract.keycloak;

import org.keycloak.Config;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

/** Registered under {@value #ID}; the realm turns it on through {@code eventsListeners}. */
public class RegistrationRoleEventListenerFactory implements EventListenerProviderFactory {

    public static final String ID = "agricontract-registration-role";

    @Override
    public EventListenerProvider create(KeycloakSession session) {
        return new RegistrationRoleEventListener(session);
    }

    @Override
    public void init(Config.Scope config) {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
    }

    @Override
    public String getId() {
        return ID;
    }
}
