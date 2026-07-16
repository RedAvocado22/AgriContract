import Keycloak from 'keycloak-js'

import { env } from '../config/env'

export const keycloak = new Keycloak({
  url: env.keycloakUrl,
  realm: env.keycloakRealm,
  clientId: env.keycloakClientId,
})

let initialization: Promise<boolean> | null = null

export const initializeKeycloak = () => {
  if (!initialization) {
    initialization = keycloak.init({
      onLoad: 'check-sso',
      pkceMethod: 'S256',
      checkLoginIframe: false,
    })
  }

  return initialization
}

export const loginWithKeycloak = () =>
  keycloak.login({
    redirectUri: `${window.location.origin}/dashboard`,
  })

export const logoutFromKeycloak = () =>
  keycloak.logout({
    redirectUri: `${window.location.origin}/login`,
  })

export const refreshKeycloakToken = async () => {
  if (!keycloak.authenticated) {
    return null
  }

  await keycloak.updateToken(30)
  return keycloak.token ?? null
}
