import { ref } from 'vue'

export type Theme = 'dark' | 'light'

const STORAGE_KEY = 'theme'

function getInitialTheme(): Theme {
  const saved = localStorage.getItem(STORAGE_KEY)
  if (saved === 'light' || saved === 'dark') return saved
  return 'light' // 기본값: 라이트
}

const theme = ref<Theme>(getInitialTheme())

function applyTheme(value: Theme) {
  const root = document.documentElement
  root.classList.toggle('dark', value === 'dark')
  localStorage.setItem(STORAGE_KEY, value)
}

export function useTheme() {
  function setTheme(value: Theme) {
    theme.value = value
    applyTheme(value)
  }

  function toggleTheme() {
    setTheme(theme.value === 'dark' ? 'light' : 'dark')
  }

  return { theme, setTheme, toggleTheme }
}

// 앱 부팅 시 1회 호출 — 화면 깜빡임(FOUC) 방지
export function initTheme() {
  applyTheme(theme.value)
}
