export type UserRole = 'SELLER' | 'BUYER' | 'ADMIN'

export interface UserProfile {
  id: string
  name: string
  email: string
  organization: string
  role: UserRole
  phone?: string
  address?: string
  verificationStatus?: 'PENDING' | 'VERIFIED' | 'REJECTED'
}

export interface UserProfileResponse {
  userId: string
  organizationName: string
  role: UserRole
  email: string
  phone?: string
  address?: string
  verificationStatus: 'PENDING' | 'VERIFIED' | 'REJECTED'
}

export interface RegisterProfileInput {
  organizationName: string
  phone?: string
  address?: string
}

export interface LoginInput {
  email: string
  password: string
}
