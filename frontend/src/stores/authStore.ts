import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { login as loginApi, register as registerApi, guestLogin as guestLoginApi } from '../apis/authApi'
import { AUTH_TOKEN_KEY, AUTH_USERNAME_KEY, AUTH_ROLE_KEY } from '../apis/http'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem(AUTH_TOKEN_KEY))
  const username = ref<string | null>(localStorage.getItem(AUTH_USERNAME_KEY))
  const role = ref<string | null>(localStorage.getItem(AUTH_ROLE_KEY))
  const isAuthenticated = computed(() => !!token.value)
  const isAdmin = computed(() => role.value === 'ADMIN')

  const setSession = (t: string, u: string, r: string) => {
    token.value = t
    username.value = u
    role.value = r
    localStorage.setItem(AUTH_TOKEN_KEY, t)
    localStorage.setItem(AUTH_USERNAME_KEY, u)
    localStorage.setItem(AUTH_ROLE_KEY, r)
  }

  const login = async (loginUsername: string, password: string) => {
    const res = await loginApi(loginUsername, password)
    setSession(res.token, res.username, res.role)
  }

  const register = async (registerUsername: string, password: string, inviteCode: string) => {
    const res = await registerApi(registerUsername, password, inviteCode)
    setSession(res.token, res.username, res.role)
  }

  const loginAsGuest = async () => {
    const res = await guestLoginApi()
    setSession(res.token, res.username, res.role)
  }

  const logout = () => {
    token.value = null
    username.value = null
    role.value = null
    localStorage.removeItem(AUTH_TOKEN_KEY)
    localStorage.removeItem(AUTH_USERNAME_KEY)
    localStorage.removeItem(AUTH_ROLE_KEY)
  }

  return { token, username, role, isAuthenticated, isAdmin, login, register, loginAsGuest, logout }
})
