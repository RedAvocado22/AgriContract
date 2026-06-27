import axios from 'axios'

import { env } from '../config/env'
import { useAuthStore } from '../stores/authStore'

const apiClient = axios.create({
  baseURL: env.apiBaseUrl,
})

apiClient.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken

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
  (error) => Promise.reject(error),
)

export default apiClient
