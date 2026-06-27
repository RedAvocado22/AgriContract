const toBoolean = (value: string | undefined, fallback: boolean) => {
  if (value === undefined) {
    return fallback
  }

  return value.toLowerCase() === 'true'
}

export const env = {
  apiBaseUrl: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080',
  keycloakUrl: import.meta.env.VITE_KEYCLOAK_URL ?? 'http://localhost:8180',
  keycloakRealm: import.meta.env.VITE_KEYCLOAK_REALM ?? 'agricontract',
  keycloakClientId:
    import.meta.env.VITE_KEYCLOAK_CLIENT_ID ?? 'agricontract-frontend',
  useMocks: toBoolean(import.meta.env.VITE_USE_MOCKS, false),
}
