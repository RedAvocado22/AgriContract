import { create } from 'zustand'

import type { UserProfile, UserRole } from '../types/auth'

type ProfileStatus = 'unknown' | 'missing' | 'ready'

interface AuthState {
  accessToken: string | null
  user: UserProfile | null
  isAuthenticated: boolean
  profileStatus: ProfileStatus
  setSession: (accessToken: string | null) => void
  setUser: (user: UserProfile) => void
  markProfileMissing: () => void
  loginWithMock: (payload: { email: string; role: UserRole }) => void
  logout: () => void
}

const buildMockUser = (email: string, role: UserRole): UserProfile => ({
  id: `mock-${role.toLowerCase()}`,
  email,
  role,
  name: role === 'SELLER' ? 'Mai Nguyen' : role === 'BUYER' ? 'Tuan Tran' : 'Minh Anh Le',
  organization:
    role === 'SELLER'
      ? 'Dak Lak Coffee Cooperative'
      : role === 'BUYER'
        ? 'Saigon Agricultural Trading'
        : 'AgriContract Operations Center',
})

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: null,
  user: null,
  isAuthenticated: false,
  profileStatus: 'unknown',
  setSession: (accessToken) => {
    set({
      accessToken,
      isAuthenticated: Boolean(accessToken),
      profileStatus: accessToken ? 'unknown' : 'missing',
    })
  },
  setUser: (user) => {
    set({
      user,
      isAuthenticated: true,
      profileStatus: 'ready',
    })
  },
  markProfileMissing: () => {
    set({
      user: null,
      isAuthenticated: true,
      profileStatus: 'missing',
    })
  },
  loginWithMock: ({ email, role }) => {
    set({
      accessToken: `mock-token-${role.toLowerCase()}`,
      user: buildMockUser(email, role),
      isAuthenticated: true,
      profileStatus: 'ready',
    })
  },
  logout: () => {
    window.localStorage.removeItem('agricontract.accessToken')
    set({
      accessToken: null,
      user: null,
      isAuthenticated: false,
      profileStatus: 'unknown',
    })
  },
}))
