<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { isAxiosError } from 'axios'
import { Music } from '@lucide/vue'
import { useAuthStore } from '../stores/authStore'
import Button from '../components/ui/Button.vue'
import Input from '../components/ui/Input.vue'
import Label from '../components/ui/Label.vue'
import Card from '../components/ui/Card.vue'

interface ApiErrorResponse { message?: string }

const router = useRouter()
const authStore = useAuthStore()

const form = reactive({ username: '', password: '', passwordConfirm: '', inviteCode: '' })
const errorMessage = ref('')
const isSubmitting = ref(false)

const apiError = (e: unknown, fallback: string) =>
  isAxiosError<ApiErrorResponse>(e) ? (e.response?.data?.message ?? fallback) : fallback

const handleSubmit = async () => {
  errorMessage.value = ''
  if (form.password !== form.passwordConfirm) {
    errorMessage.value = '비밀번호가 일치하지 않습니다.'
    return
  }
  isSubmitting.value = true
  try {
    await authStore.register(form.username, form.password, form.inviteCode)
    await router.push('/')
  } catch (e) {
    errorMessage.value = apiError(e, '회원가입에 실패했습니다.')
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <div class="min-h-dvh bg-background text-foreground flex items-center justify-center px-4">
    <Card class="w-full max-w-sm p-6">
      <div class="flex flex-col items-center gap-1.5 mb-6">
        <Music class="w-6 h-6 text-primary" />
        <h1 class="text-lg font-bold text-foreground">Worship Sheet 회원가입</h1>
      </div>

      <p v-if="errorMessage" class="text-sm text-destructive bg-destructive-soft rounded-md px-3 py-2 mb-4">
        {{ errorMessage }}
      </p>

      <form class="flex flex-col gap-3" @submit.prevent="handleSubmit">
        <div class="flex flex-col gap-1.5">
          <Label for="register-username">아이디</Label>
          <Input id="register-username" v-model="form.username" type="text" autocomplete="username" minlength="3" required />
        </div>
        <div class="flex flex-col gap-1.5">
          <Label for="register-password">비밀번호</Label>
          <Input id="register-password" v-model="form.password" type="password" autocomplete="new-password" minlength="8" required />
        </div>
        <div class="flex flex-col gap-1.5">
          <Label for="register-password-confirm">비밀번호 확인</Label>
          <Input id="register-password-confirm" v-model="form.passwordConfirm" type="password" autocomplete="new-password" minlength="8" required />
        </div>
        <div class="flex flex-col gap-1.5">
          <Label for="register-invite-code">초대코드</Label>
          <Input id="register-invite-code" v-model="form.inviteCode" type="text" autocomplete="off" required />
          <p class="text-xs text-muted-foreground">가입에는 관리자에게 받은 초대코드가 필요합니다.</p>
        </div>
        <Button type="submit" class="mt-2 w-full" :disabled="isSubmitting">
          {{ isSubmitting ? '가입 중...' : '회원가입' }}
        </Button>
      </form>

      <p class="text-sm text-muted-foreground text-center mt-4">
        이미 계정이 있으신가요?
        <RouterLink to="/login" class="text-primary font-medium hover:underline">로그인</RouterLink>
      </p>
    </Card>
  </div>
</template>
