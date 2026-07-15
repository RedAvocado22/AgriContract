import axios from 'axios'

import { env } from '../config/env'
import type { LoginInput } from '../types/auth'

export const TOKEN_STORAGE_KEY = 'agricontract.accessToken'
export const REFRESH_TOKEN_STORAGE_KEY = 'agricontract.refreshToken'

interface KeycloakTokenResponse {
  access_token: string
  refresh_token?: string
  expires_in?: number
  token_type?: string
}

export const authApi = {
  async login(input: LoginInput) {
    if (env.useMocks) {
      return {
        accessToken: `mock-token-${input.identifier || 'buyer'}`,
        refreshToken: undefined,
        expiresIn: 3600,
        tokenType: 'Bearer',
      }
    }

    const form = new URLSearchParams()
    form.set('grant_type', 'password')
    form.set('client_id', env.keycloakClientId)
    form.set('username', input.identifier)
    form.set('password', input.password)

    const response = await axios.post<KeycloakTokenResponse>(
      `${env.keycloakUrl}/realms/${env.keycloakRealm}/protocol/openid-connect/token`,
      form,
      {
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
        },
      },
    )

    return {
      accessToken: response.data.access_token,
      refreshToken: response.data.refresh_token,
      expiresIn: response.data.expires_in,
      tokenType: response.data.token_type,
    }
  },
}
