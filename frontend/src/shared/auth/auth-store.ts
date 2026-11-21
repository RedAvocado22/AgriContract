import { create } from "zustand"
import type { KeycloakTokenParsed } from "keycloak-js"

export const APP_ROLES = [
  "BUYER",
  "SELLER",
  "INSPECTOR",
  "OPERATOR",
  "ARBITRATOR",
  "ADMIN",
] as const
export type AppRole = (typeof APP_ROLES)[number]
export const ROLE_LABELS: Record<AppRole, string> = {
  BUYER: "Bên mua",
  SELLER: "Bên bán",
  INSPECTOR: "Giám định viên",
  OPERATOR: "Điều phối viên",
  ARBITRATOR: "Trọng tài viên",
  ADMIN: "Quản trị viên",
}

export function identityFromToken(
  token?: string,
  claims?: KeycloakTokenParsed
) {
  return {
    authenticated: Boolean(token && claims?.sub),
    token,
    userId: claims?.sub,
    email: typeof claims?.email === "string" ? claims.email : undefined,
    roles: APP_ROLES.filter((role) =>
      claims?.realm_access?.roles?.includes(role)
    ),
    // Missing auth_time must require step-up, never substitute iat or the local clock.
    authTime:
      typeof claims?.auth_time === "number" ? claims.auth_time : undefined,
  }
}

type AuthState = ReturnType<typeof identityFromToken> & {
  status: "initializing" | "ready" | "error"
  error?: string
}

// Tokens stay in memory. Do not add Zustand persist/localStorage here.
export const useAuthStore = create<AuthState>(() => ({
  ...identityFromToken(),
  status: "initializing",
}))
