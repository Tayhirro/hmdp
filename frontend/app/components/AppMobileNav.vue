<script setup lang="ts">
const route = useRoute()

const items = [
  { to: '/', label: '发现', icon: 'i-lucide-compass' },
  { to: '/feed', label: 'Feed', icon: 'i-lucide-layout-list' },
  { to: '/search', label: '搜索', icon: 'i-lucide-search' },
  { to: '/shops', label: '商户', icon: 'i-lucide-store' },
  { to: '/me', label: '我的', icon: 'i-lucide-user' }
]

function isActivePath(path: string) {
  if (path === '/') return route.path === '/'
  return route.path === path || route.path.startsWith(`${path}/`)
}
</script>

<template>
  <div class="md:hidden sticky bottom-0 z-10 border-t border-default bg-elevated/90 backdrop-blur">
    <UContainer class="px-2">
      <div class="grid grid-cols-5 gap-1 py-2">
        <UButton
          v-for="item in items"
          :key="item.to"
          :to="item.to"
          :color="isActivePath(item.to) ? 'primary' : 'neutral'"
          :variant="isActivePath(item.to) ? 'subtle' : 'ghost'"
          class="flex-col h-auto py-2"
        >
          <UIcon
            :name="item.icon"
            class="size-5"
          />
          <span class="text-xs mt-1">{{ item.label }}</span>
        </UButton>
      </div>
    </UContainer>
  </div>
</template>
