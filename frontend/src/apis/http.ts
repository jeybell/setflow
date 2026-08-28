import axios from 'axios'
import { getActiveBaseUrl, isPrimaryBaseUrl, switchToFallbackBaseUrl } from './apiBase'

export const AUTH_TOKEN_KEY = 'auth_token'
export const AUTH_USERNAME_KEY = 'auth_username'
export const AUTH_ROLE_KEY = 'auth_role'

// 로그인 없이도 접근 가능한 경로. 이 경로에서 401을 받아도 로그인 화면으로 강제 이동시키지
// 않는다(공유 링크 방문자가 인증이 필요한 부가 API 호출 때문에 튕겨나가는 것을 방지).
export const PUBLIC_PATH_PREFIXES = ['/login', '/register', '/share/']

const http = axios.create({
  headers: {
    'Content-Type': 'application/json',
  },
})

http.interceptors.request.use((config) => {
  config.baseURL = getActiveBaseUrl()
  const token = localStorage.getItem(AUTH_TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      const isPublicPage = PUBLIC_PATH_PREFIXES.some((prefix) => location.pathname.startsWith(prefix))
      if (!isPublicPage) {
        localStorage.removeItem(AUTH_TOKEN_KEY)
        localStorage.removeItem(AUTH_USERNAME_KEY)
        localStorage.removeItem(AUTH_ROLE_KEY)
        if (location.pathname !== '/login') {
          location.href = '/login'
        }
      }
      return Promise.reject(error)
    }

    // 1차 백엔드가 아예 응답하지 않는 경우(서버 다운/절전)에만 폴백으로 전환 후 재시도.
    // 4xx/5xx처럼 서버가 살아있는데 에러를 준 경우는 폴백 대상이 아니다.
    const config = error.config
    if (!error.response && config && isPrimaryBaseUrl(config.baseURL) && switchToFallbackBaseUrl()) {
      return http(config)
    }

    return Promise.reject(error)
  },
)

export default http
