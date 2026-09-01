<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { isAxiosError } from 'axios'
import { useAuthStore } from '../stores/authStore'
import Button from '../components/ui/Button.vue'
import Input from '../components/ui/Input.vue'
import Label from '../components/ui/Label.vue'
import Card from '../components/ui/Card.vue'

interface ApiErrorResponse { message?: string }

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const form = reactive({ username: '', password: '' })
const errorMessage = ref('')
const isSubmitting = ref(false)
const isGuestLoggingIn = ref(false)

const apiError = (e: unknown, fallback: string) =>
  isAxiosError<ApiErrorResponse>(e) ? (e.response?.data?.message ?? fallback) : fallback

const goToRedirect = async () => {
  const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
  await router.push(redirect)
}

const handleSubmit = async () => {
  errorMessage.value = ''
  isSubmitting.value = true
  try {
    await authStore.login(form.username, form.password)
    await goToRedirect()
  } catch (e) {
    errorMessage.value = apiError(e, '로그인에 실패했습니다.')
  } finally {
    isSubmitting.value = false
  }
}

const handleGuestLogin = async () => {
  errorMessage.value = ''
  isGuestLoggingIn.value = true
  try {
    await authStore.loginAsGuest()
    await goToRedirect()
  } catch (e) {
    errorMessage.value = apiError(e, '게스트 로그인에 실패했습니다.')
  } finally {
    isGuestLoggingIn.value = false
  }
}
</script>

<template>
  <div class="min-h-dvh bg-background text-foreground flex items-center justify-center px-4">
    <Card class="w-full max-w-sm p-6">
      <div class="flex flex-col items-center gap-1.5 mb-6">
        <img src="/logo.png" alt="Setflow" class="w-10 h-10 rounded-lg" />
        <h1 class="text-lg font-bold text-foreground">Setflow 로그인</h1>
      </div>

      <p v-if="errorMessage" class="text-sm text-destructive bg-destructive-soft rounded-md px-3 py-2 mb-4">
        {{ errorMessage }}
      </p>

      <form class="flex flex-col gap-3" @submit.prevent="handleSubmit">
        <div class="flex flex-col gap-1.5">
          <Label for="login-username">아이디</Label>
          <Input id="login-username" v-model="form.username" type="text" autocomplete="username" required />
        </div>
        <div class="flex flex-col gap-1.5">
          <Label for="login-password">비밀번호</Label>
          <Input id="login-password" v-model="form.password" type="password" autocomplete="current-password" required />
        </div>
        <Button type="submit" class="mt-2 w-full" :disabled="isSubmitting || isGuestLoggingIn">
          {{ isSubmitting ? '로그인 중...' : '로그인' }}
        </Button>
      </form>

      <div class="flex items-center gap-2 my-4">
        <div class="h-px flex-1 bg-border" />
        <span class="text-xs text-muted-foreground">또는</span>
        <div class="h-px flex-1 bg-border" />
      </div>

      <Button
        type="button"
        variant="outline"
        class="w-full"
        :disabled="isSubmitting || isGuestLoggingIn"
        @click="handleGuestLogin"
      >
        {{ isGuestLoggingIn ? '입장 중...' : '게스트로 시작하기' }}
      </Button>

      <p class="text-sm text-muted-foreground text-center mt-4">
        계정이 없으신가요?
        <RouterLink to="/register" class="text-primary font-medium hover:underline">회원가입</RouterLink>
      </p>
    </Card>
  </div>
</template>
