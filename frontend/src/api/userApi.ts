import type { AxiosError } from 'axios'

import apiClient from './client'
import type {
  RegisterProfileInput,
  UserProfile,
  UserProfileResponse,
} from '../types/auth'
import { repairMojibake } from '../utils/textEncoding'

interface ApiResponse<T> {
  success: boolean
  message?: string
  data: T
}

export const toUserProfile = (profile: UserProfileResponse): UserProfile => ({
  id: profile.userId,
  name: repairMojibake(profile.organizationName),
  email: profile.email,
  organization: repairMojibake(profile.organizationName),
  role: profile.role,
  phone: profile.phone,
  address: repairMojibake(profile.address),
  verificationStatus: profile.verificationStatus,
})

export const isNotFoundError = (error: unknown) =>
  Boolean((error as AxiosError).response?.status === 404)

export const userApi = {
  async getMe() {
    const response = await apiClient.get<ApiResponse<UserProfileResponse>>(
      '/api/v1/users/me',
    )
    return toUserProfile(response.data.data)
  },

  async registerProfile(input: RegisterProfileInput) {
    const response = await apiClient.post<ApiResponse<UserProfileResponse>>(
      '/api/v1/users/register',
      input,
    )
    return toUserProfile(response.data.data)
  },
}
