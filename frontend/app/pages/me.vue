<script setup lang="ts">
import { resolveImgUrl } from '~/utils/format'
import type { UserDTO } from '~/types/api'

definePageMeta({ middleware: ['auth'] })

const auth = useAuth()
const toast = useToast()
const { $apiData } = useNuxtApp()

const user = computed<UserDTO | null>(() => auth.user.value)
const avatarSrc = computed(() => resolveImgUrl(user.value?.icon) || '/imgs/icons/default-icon.png')

const signCount = ref<number | null>(null)
const loadingSign = ref(false)

async function refreshSignCount() {
  try {
    const count = await $apiData<number>('/user/sign/count')
    signCount.value = typeof count === 'number' ? count : null
  } catch {
    signCount.value = null
  }
}

async function doSign() {
  loadingSign.value = true
  try {
    await $apiData<void>('/user/sign', { method: 'POST' })
    toast.add({ title: '签到成功', color: 'success', icon: 'i-lucide-check-circle' })
    await refreshSignCount()
  } catch (error) {
    toast.add({
      title: '签到失败',
      description: (error as any)?.statusMessage || (error as any)?.message,
      color: 'error',
      icon: 'i-lucide-x-circle'
    })
  } finally {
    loadingSign.value = false
  }
}

async function doLogout() {
  await auth.logout()
  await navigateTo('/login')
}

await auth.fetchMe().catch(() => null)
await refreshSignCount()
</script>

<template>
  <div class="space-y-6">
    <UCard>
      <div class="flex items-center justify-between gap-4">
        <div class="flex items-center gap-3 min-w-0">
          <UAvatar :src="avatarSrc" size="lg" />
          <div class="min-w-0">
            <div class="text-lg font-semibold text-highlighted truncate">
              {{ user?.nickName || '—' }}
            </div>
            <div class="text-sm text-muted">
              ID: {{ user?.id || '—' }}
            </div>
          </div>
        </div>

        <UButton color="neutral" variant="outline" icon="i-lucide-log-out" @click="doLogout">
          退出
        </UButton>
      </div>
    </UCard>

    <div class="grid gap-4 sm:grid-cols-2">
      <UCard>
        <template #header>
          <div class="font-semibold text-highlighted">
            用户签到
          </div>
        </template>

        <div class="flex items-center justify-between">
          <div>
            <div class="text-sm text-muted">
              最近连续签到
            </div>
            <div class="text-2xl font-semibold text-highlighted">
              {{ signCount ?? '—' }}
            </div>
          </div>

          <UButton color="primary" :loading="loadingSign" @click="doSign">
            立即签到
          </UButton>
        </div>
      </UCard>

      <UCard>
        <template #header>
          <div class="font-semibold text-highlighted">
            快捷入口
          </div>
        </template>

        <div class="grid grid-cols-2 gap-2">
          <UButton to="/" color="neutral" variant="subtle" icon="i-lucide-compass">
            发现
          </UButton>
          <UButton to="/shops" color="neutral" variant="subtle" icon="i-lucide-store">
            商户
          </UButton>
          <UButton color="neutral" variant="subtle" icon="i-lucide-refresh-cw" @click="refreshSignCount">
            刷新签到
          </UButton>
          <UButton color="neutral" variant="subtle" icon="i-lucide-key-round" to="/login">
            切换账号
          </UButton>
        </div>
      </UCard>
    </div>
  </div>
</template>

