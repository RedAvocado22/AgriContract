export const getApiErrorMessage = (error: unknown, fallback: string) => {
  const apiError = error as {
    response?: { data?: { message?: string; error?: string } }
    message?: string
  }

  return apiError.response?.data?.message ?? apiError.response?.data?.error ?? apiError.message ?? fallback
}
