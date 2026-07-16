import axios from 'axios'

import { env } from '../config/env'
import { API_ERROR_EVENT } from '../components/feedback/ApiErrorNotifier'
import { loginWithKeycloak, refreshKeycloakToken } from '../auth/keycloak'
import { useAuthStore } from '../stores/authStore'

const apiClient = axios.create({
  baseURL: env.apiBaseUrl,
})

apiClient.interceptors.request.use(async (config) => {
  const refreshedToken = env.useMocks ? null : await refreshKeycloakToken()
  if (refreshedToken) {
    useAuthStore.getState().setSession(refreshedToken)
  }
  const token = refreshedToken ?? useAuthStore.getState().accessToken

  if (token) {
    if (config.headers && 'set' in config.headers) {
      config.headers.set('Authorization', `Bearer ${token}`)
    } else {
      config.headers = {
        Authorization: `Bearer ${token}`,
      } as typeof config.headers
    }
  }

  return config
})

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error?.response?.status as number | undefined

    if (status === 401 && !env.useMocks) {
      useAuthStore.getState().logout()
      void loginWithKeycloak()
    } else if (status === 403) {
      window.dispatchEvent(new CustomEvent(API_ERROR_EVENT, { detail: 'Bạn không có quyền thực hiện thao tác này.' }))
    } else if (status && status >= 500) {
      window.dispatchEvent(new CustomEvent(API_ERROR_EVENT, { detail: 'Máy chủ đang gặp sự cố. Vui lòng thử lại sau.' }))
    }

    return Promise.reject(error)
  },
)

export default apiClient
