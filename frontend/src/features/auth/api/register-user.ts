import axios from "axios"

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "/api"

interface RegisterUserInput {
  organizationName: string
  email: string
  phone?: string
  address?: string
}

interface UserProfile {
  userId: string
  organizationName: string
  role: string
  email: string
  phone: string | null
  address: string | null
  verificationStatus: string
  authorizationExpiresAt: string | null
}

interface ApiEnvelope<T> {
  success: boolean
  data: T
  error: string | null
}

class RegisterValidationError extends Error {
  constructor(message: string) {
    super(message)
    this.name = "RegisterValidationError"
  }
}

// The email travels in the body; the gateway no longer injects X-User-Email (#314).
// 201 creates the profile, 200 returns the one already stored, so a retry is a success too.
async function registerUser(input: RegisterUserInput, accessToken?: string): Promise<UserProfile> {
  try {
    const response = await axios.post<ApiEnvelope<UserProfile>>(
      `${API_BASE_URL}/v1/users/register`,
      input,
      { headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : undefined }
    )
    return response.data.data
  } catch (error) {
    if (axios.isAxiosError<ApiEnvelope<unknown>>(error) && error.response?.status === 400) {
      throw new RegisterValidationError(error.response.data?.error ?? "Thông tin đăng ký không hợp lệ.")
    }
    throw error
  }
}

export { registerUser, RegisterValidationError }
export type { RegisterUserInput, UserProfile }
