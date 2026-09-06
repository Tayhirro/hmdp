<script setup lang="ts">
import { resolveImgUrl } from '~/utils/format'
import type { UserDTO, UserInfo } from '~/types/api'
import { getApiErrorMessage } from '~/utils/api-error'

definePageMeta({ middleware: ['auth'] })

const auth = useAuth()
const toast = useToast()
const { $apiData } = useNuxtApp()

const user = computed<UserDTO | null>(() => auth.user.value)
const avatarSrc = computed(() => resolveImgUrl(user.value?.icon) || '/imgs/icons/default-icon.png')

const signCount = ref<number | null>(null)
const loadingSign = ref(false)
const savingProfile = ref(false)
const profile = reactive<Pick<UserInfo, 'city' | 'introduce' | 'gender'> & { birthday: string }>({
  city: '',
  introduce: '',
  gender: 2,
  birthday: ''
})

async function loadProfile() {
  if (!user.value?.id) return
  const info = await $apiData<UserInfo | null>(`/user/info/${user.value.id}`)
  if (!info) return
  profile.city = info.city || ''
  profile.introduce = info.introduce || ''
  profile.gender = info.gender ?? 2
  profile.birthday = info.birthday || ''
}

async function saveProfile() {
  savingProfile.value = true
  try {
    await $apiData<unknown>('/user/info', {
      method: 'PUT',
      body: {
        city: profile.city,
        introduce: profile.introduce,
        gender: profile.gender,
        birthday: profile.birthday || null
      }
    })
    toast.add({ title: '资料已保存', color: 'success', icon: 'i-lucide-check-circle' })
  } catch (error) {
    toast.add({
      title: '保存失败',
      description: getApiErrorMessage(error),
      color: 'error'
    })
  } finally {
    savingProfile.value = false
  }
}

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
    await $apiData<unknown>('/user/sign', { method: 'POST' })
    toast.add({ title: '签到成功', color: 'success', icon: 'i-lucide-check-circle' })
    await refreshSignCount()
  } catch (error) {
    toast.add({
      title: '签到失败',
      description: getApiErrorMessage(error),
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
await loadProfile().catch(() => null)
</script>

<template>
  <div class="space-y-6">
    <UCard>
      <div class="flex items-center justify-between gap-4">
        <div class="flex items-center gap-3 min-w-0">
          <UAvatar
            :src="avatarSrc"
            size="lg"
          />
          <div class="min-w-0">
            <div class="text-lg font-semibold text-highlighted truncate">
              {{ user?.nickName || '—' }}
            </div>
            <div class="text-sm text-muted">
              ID: {{ user?.id || '—' }}
            </div>
          </div>
        </div>

        <UButton
          color="neutral"
          variant="outline"
          icon="i-lucide-log-out"
          @click="doLogout"
        >
          退出
        </UButton>
      </div>
    </UCard>

    <div class="grid gap-4 lg:grid-cols-2">
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

          <UButton
            color="primary"
            :loading="loadingSign"
            @click="doSign"
          >
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
          <UButton
            to="/"
            color="neutral"
            variant="subtle"
            icon="i-lucide-compass"
          >
            发现
          </UButton>
          <UButton
            to="/shops"
            color="neutral"
            variant="subtle"
            icon="i-lucide-store"
          >
            商户
          </UButton>
          <UButton
            color="neutral"
            variant="subtle"
            icon="i-lucide-refresh-cw"
            @click="refreshSignCount"
          >
            刷新签到
          </UButton>
          <UButton
            color="neutral"
            variant="subtle"
            icon="i-lucide-key-round"
            to="/login"
          >
            切换账号
          </UButton>
          <UButton
            :to="`/users/${user?.id}`"
            color="neutral"
            variant="subtle"
            icon="i-lucide-notebook-tabs"
          >
            我的笔记
          </UButton>
          <UButton
            to="/blogs/new"
            color="primary"
            variant="subtle"
            icon="i-lucide-square-pen"
          >
            发布笔记
          </UButton>
          <UButton
            to="/manage"
            color="neutral"
            variant="subtle"
            icon="i-lucide-settings"
          >
            内容管理
          </UButton>
        </div>
      </UCard>
    </div>

    <UCard>
      <template #header>
        <div class="font-semibold text-highlighted">
          编辑个人资料
        </div>
      </template>

      <div class="grid gap-4 md:grid-cols-2">
        <UFormField label="所在城市">
          <UInput
            v-model="profile.city"
            placeholder="例如：杭州"
            class="w-full"
          />
        </UFormField>
        <UFormField label="生日">
          <UInput
            v-model="profile.birthday"
            type="date"
            class="w-full"
          />
        </UFormField>
        <UFormField label="性别">
          <select
            v-model.number="profile.gender"
            class="w-full rounded-md border border-default bg-default px-3 py-2 text-sm"
          >
            <option :value="2">
              未设置
            </option>
            <option :value="0">
              男
            </option>
            <option :value="1">
              女
            </option>
          </select>
        </UFormField>
        <UFormField
          label="个人介绍"
          class="md:col-span-2"
        >
          <UTextarea
            v-model="profile.introduce"
            :rows="4"
            maxlength="128"
            class="w-full"
          />
        </UFormField>
      </div>
      <div class="flex justify-end mt-4">
        <UButton
          :loading="savingProfile"
          @click="saveProfile"
        >
          保存资料
        </UButton>
      </div>
    </UCard>
  </div>
</template>
