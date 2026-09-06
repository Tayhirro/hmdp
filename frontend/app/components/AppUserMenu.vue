<script setup lang="ts">
import { resolveImgUrl } from '~/utils/format'

const auth = useAuth()
const toast = useToast()
const route = useRoute()

const displayName = computed(() => auth.user.value?.nickName || '我的')
const avatarSrc = computed(() => resolveImgUrl(auth.user.value?.icon) || '/imgs/icons/default-icon.png')

async function onLogout() {
  await auth.logout()
  toast.add({
    title: '已退出登录',
    color: 'success',
    icon: 'i-lucide-check-circle'
  })

  if (route.path !== '/login') {
    await navigateTo('/login')
  }
}

const items = computed(() => ([
  {
    label: auth.user.value?.nickName || '我的账号',
    type: 'label' as const
  },
  { type: 'separator' as const },
  {
    label: '个人中心',
    icon: 'i-lucide-user',
    to: '/me'
  },
  {
    label: '退出登录',
    icon: 'i-lucide-log-out',
    color: 'error' as const,
    click: onLogout
  }
]))
</script>

<template>
  <div class="flex items-center gap-2">
    <UColorModeButton />

    <template v-if="auth.isLoggedIn">
      <UDropdownMenu
        :items="items"
        :content="{ align: 'end' }"
      >
        <UButton
          color="neutral"
          variant="ghost"
          class="rounded-full"
        >
          <UAvatar
            :src="avatarSrc"
            size="xs"
          />
          <span class="hidden sm:inline text-sm ml-1 max-w-28 truncate">
            {{ displayName }}
          </span>
          <UIcon
            name="i-lucide-chevron-down"
            class="size-4 ml-1 opacity-70"
          />
        </UButton>
      </UDropdownMenu>
    </template>

    <template v-else>
      <UButton
        to="/login"
        color="primary"
        size="sm"
      >
        登录
      </UButton>
    </template>
  </div>
</template>
