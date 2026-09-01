<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Sun, Moon, MessageSquarePlus, LogOut, HelpCircle, Shield, X } from '@lucide/vue'
import ToastHost from '../components/ui/ToastHost.vue'
import { useTheme } from '../composables/useTheme'
import { isLoading } from '../composables/useHttpLoading'
import { useAuthStore } from '../stores/authStore'
import { HELP_CONTENT } from '../lib/helpContent'

const { theme, toggleTheme } = useTheme()
const loading = isLoading()
const busy = computed(() => loading.value > 0)

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const isAdmin = computed(() => authStore.isAdmin)

const handleLogout = () => {
  authStore.logout()
  router.push('/login')
}

// ── 화면별 도움말 팝오버 (#148)
const showHelp = ref(false)
const currentHelp = computed(() => HELP_CONTENT[route.name as string] ?? null)
const toggleHelp = () => { showHelp.value = !showHelp.value }
watch(() => route.name, () => { showHelp.value = false })
const onHelpKey = (e: KeyboardEvent) => { if (e.key === 'Escape') showHelp.value = false }
onMounted(() => document.addEventListener('keydown', onHelpKey))
onBeforeUnmount(() => document.removeEventListener('keydown', onHelpKey))
</script>

<template>
  <div class="min-h-screen bg-background text-foreground flex flex-col">
    <header class="sticky top-0 z-50 bg-background/80 backdrop-blur border-b border-border">
      <!-- 상단 로딩 바 -->
      <div class="absolute bottom-0 left-0 right-0 h-0.5 overflow-hidden">
        <div
          class="h-full bg-primary origin-left transition-all duration-300"
          :class="busy ? 'opacity-100 animate-loading-bar' : 'opacity-0 w-full'"
        />
      </div>

      <div class="max-w-5xl mx-auto px-2 sm:px-6 h-12 sm:h-14 flex items-center gap-1.5 sm:gap-8">
        <RouterLink to="/" class="flex items-center gap-1.5 sm:gap-2 text-sm sm:text-base font-bold text-primary tracking-tight shrink-0">
          <img src="/logo.png" alt="Setflow" class="w-6 h-6 sm:w-7 sm:h-7 rounded-md" />
          Setflow
        </RouterLink>
        <nav class="flex gap-0.5 sm:gap-1 flex-1">
          <RouterLink
            to="/"
            exact-active-class="bg-primary-soft text-primary font-semibold"
            class="whitespace-nowrap px-2 sm:px-3 py-1.5 rounded-md text-sm font-medium text-muted-foreground hover:bg-muted hover:text-foreground transition-colors"
          >
            홈
          </RouterLink>
          <RouterLink
            to="/setlists"
            active-class="bg-primary-soft text-primary font-semibold"
            class="whitespace-nowrap px-2 sm:px-3 py-1.5 rounded-md text-sm font-medium text-muted-foreground hover:bg-muted hover:text-foreground transition-colors"
          >
            콘티
          </RouterLink>
          <RouterLink
            to="/songs"
            active-class="bg-primary-soft text-primary font-semibold"
            class="whitespace-nowrap px-2 sm:px-3 py-1.5 rounded-md text-sm font-medium text-muted-foreground hover:bg-muted hover:text-foreground transition-colors"
          >
            악보
          </RouterLink>
        </nav>
        <div class="flex items-center gap-0.5 shrink-0 relative">
          <!-- 화면별 도움말 (#148) -->
          <button
            v-if="currentHelp"
            type="button"
            @click="toggleHelp"
            aria-label="도움말"
            title="도움말"
            class="inline-flex items-center justify-center w-8 h-8 sm:w-9 sm:h-9 rounded-md transition-colors"
            :class="showHelp ? 'bg-primary-soft text-primary' : 'text-muted-foreground hover:bg-muted hover:text-foreground'"
          >
            <HelpCircle class="w-4 h-4" />
          </button>
          <!-- 바깥 클릭 닫기 오버레이 + 팝오버 (아이콘 그룹 우측 끝 정렬) -->
          <template v-if="showHelp && currentHelp">
            <div class="fixed inset-0 z-40" @click="showHelp = false" />
            <div
              class="absolute right-0 top-full mt-2 z-50 w-[min(20rem,calc(100vw-1.5rem))] rounded-lg border border-border bg-card shadow-lg p-4 text-left"
            >
              <div class="flex items-start justify-between gap-2 mb-2">
                <h3 class="text-sm font-semibold text-foreground">{{ currentHelp.title }} 도움말</h3>
                <button type="button" aria-label="닫기" class="text-muted-foreground hover:text-foreground shrink-0" @click="showHelp = false">
                  <X class="w-4 h-4" />
                </button>
              </div>
              <ul class="flex flex-col gap-1.5">
                <li v-for="(line, i) in currentHelp.lines" :key="i" class="flex gap-1.5 text-xs text-muted-foreground leading-relaxed">
                  <span class="text-primary shrink-0">•</span>
                  <span>{{ line }}</span>
                </li>
              </ul>
            </div>
          </template>
          <RouterLink
            v-if="isAdmin"
            to="/admin"
            active-class="bg-primary-soft text-primary"
            aria-label="관리자"
            title="관리자"
            class="inline-flex items-center justify-center w-8 h-8 sm:w-9 sm:h-9 rounded-md text-muted-foreground hover:bg-muted hover:text-foreground transition-colors"
          >
            <Shield class="w-4 h-4" />
          </RouterLink>
          <RouterLink
            to="/feature-requests"
            active-class="bg-primary-soft text-primary"
            aria-label="기능 요청"
            title="기능 요청"
            class="inline-flex items-center justify-center w-8 h-8 sm:w-9 sm:h-9 rounded-md text-muted-foreground hover:bg-muted hover:text-foreground transition-colors"
          >
            <MessageSquarePlus class="w-4 h-4" />
          </RouterLink>
          <button
            type="button"
            @click="toggleTheme"
            :aria-label="theme === 'dark' ? '라이트 모드로 전환' : '다크 모드로 전환'"
            class="inline-flex items-center justify-center w-8 h-8 sm:w-9 sm:h-9 rounded-md text-muted-foreground hover:bg-muted hover:text-foreground transition-colors"
          >
            <Sun v-if="theme === 'dark'" class="w-4 h-4" />
            <Moon v-else class="w-4 h-4" />
          </button>
          <button
            type="button"
            @click="handleLogout"
            aria-label="로그아웃"
            title="로그아웃"
            class="inline-flex items-center justify-center w-8 h-8 sm:w-9 sm:h-9 rounded-md text-muted-foreground hover:bg-muted hover:text-foreground transition-colors"
          >
            <LogOut class="w-4 h-4" />
          </button>
        </div>
      </div>
    </header>

    <main
      class="flex-1 max-w-5xl w-full mx-auto px-3 sm:px-6 py-4 sm:py-8 transition-opacity duration-200"
      :class="busy ? 'opacity-60 pointer-events-none' : 'opacity-100'"
    >
      <slot />
    </main>

    <ToastHost />
  </div>
</template>
