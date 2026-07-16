import { loginWithKeycloak, logoutFromKeycloak } from '../auth/keycloak'
import { env } from '../config/env'
import type { LoginInput } from '../types/auth'

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

    await loginWithKeycloak()
    return undefined
  },

  async logout() {
    if (!env.useMocks) {
      await logoutFromKeycloak()
    }
  },
}
