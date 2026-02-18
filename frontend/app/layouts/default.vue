<script setup lang="ts">
const route = useRoute()

function isActivePath(path: string) {
  if (path === '/') return route.path === '/'
  return route.path === path || route.path.startsWith(`${path}/`)
}
</script>

<template>
  <div class="min-h-screen bg-muted/30">
    <UHeader>
      <template #left>
        <NuxtLink to="/" class="flex items-center gap-2">
          <AppLogo class="shrink-0" />
        </NuxtLink>

        <div class="hidden md:flex items-center gap-1 ml-4">
          <UButton
            to="/"
            color="neutral"
            :variant="isActivePath('/') ? 'subtle' : 'ghost'"
            icon="i-lucide-compass"
            size="sm"
          >
            发现
          </UButton>
          <UButton
            to="/shops"
            color="neutral"
            :variant="isActivePath('/shops') ? 'subtle' : 'ghost'"
            icon="i-lucide-store"
            size="sm"
          >
            商户
          </UButton>
          <UButton
            to="/me"
            color="neutral"
            :variant="isActivePath('/me') ? 'subtle' : 'ghost'"
            icon="i-lucide-user"
            size="sm"
          >
            我的
          </UButton>
        </div>
      </template>

      <template #right>
        <AppUserMenu />
      </template>
    </UHeader>

    <UMain>
      <UContainer class="py-6">
        <slot />
      </UContainer>
    </UMain>

    <AppMobileNav />
  </div>
</template>

