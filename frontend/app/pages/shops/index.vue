<script setup lang="ts">
definePageMeta({
  middleware: 'auth'
})

import type { Shop, ShopType } from '~/types/api'
import { firstCsvItem, formatFen, resolveImgUrl } from '~/utils/format'

const { $apiData } = useNuxtApp()
const toast = useToast()
const route = useRoute()
const router = useRouter()

const types = ref<ShopType[]>([])
const shops = ref<Shop[]>([])

const isLoading = ref(false)
const isReachEnd = ref(false)
const current = ref(1)

const searchInput = ref(typeof route.query.q === 'string' ? route.query.q : '')

const selectedTypeId = computed<number | null>(() => {
  const raw = route.query.typeId
  const value = typeof raw === 'string' ? Number(raw) : NaN
  return Number.isFinite(value) ? value : null
})

const effectiveTypeId = computed<number | null>(() => {
  return selectedTypeId.value ?? types.value[0]?.id ?? null
})

const q = computed(() => (typeof route.query.q === 'string' ? route.query.q.trim() : ''))

function typeIconUrl(icon: string) {
  return resolveImgUrl(icon) || '/imgs/types/ms.png'
}

function shopCover(images: string) {
  return resolveImgUrl(firstCsvItem(images)) || '/imgs/blogs/blog1.jpg'
}

function shopScore(score?: number) {
  if (!score) return '0.0'
  return (score / 10).toFixed(1)
}

function applySearch() {
  const value = searchInput.value.trim()
  router.push({
    path: '/shops',
    query: {
      q: value || undefined,
      typeId: value ? undefined : (effectiveTypeId.value ? String(effectiveTypeId.value) : undefined)
    }
  })
}

function pickType(id: number) {
  router.push({
    path: '/shops',
    query: {
      typeId: String(id)
    }
  })
}

async function loadTypes() {
  const data = await $apiData<ShopType[]>('/shop-type/list')
  types.value = data ?? []
}

async function loadShops(reset = false) {
  if (isLoading.value) return

  if (reset) {
    shops.value = []
    current.value = 1
    isReachEnd.value = false
  }

  isLoading.value = true
  try {
    const page = current.value

    let url = ''
    if (q.value) {
      url = `/shop/of/name?name=${encodeURIComponent(q.value)}&current=${page}`
    } else if (effectiveTypeId.value) {
      url = `/shop/of/type?typeId=${effectiveTypeId.value}&current=${page}`
    } else {
      isReachEnd.value = true
      return
    }

    const data = await $apiData<Shop[]>(url)
    const list = data ?? []
    if (list.length === 0) {
      isReachEnd.value = true
      return
    }
    shops.value.push(...list)
  } catch (error) {
    toast.add({
      title: '加载失败',
      description: (error as any)?.statusMessage || (error as any)?.message,
      color: 'error',
      icon: 'i-lucide-x-circle'
    })
  } finally {
    isLoading.value = false
  }
}

async function loadMore() {
  if (isReachEnd.value || isLoading.value) return
  current.value += 1
  await loadShops(false)
}

await loadTypes()
await loadShops(true)

watch([() => route.query.typeId, () => route.query.q], async () => {
  searchInput.value = typeof route.query.q === 'string' ? route.query.q : ''
  await loadShops(true)
})
</script>

<template>
  <div class="space-y-6">
    <div class="flex flex-col gap-3">
      <div class="flex gap-2">
        <UInput
          v-model="searchInput"
          icon="i-lucide-search"
          placeholder="搜索商户名"
          class="flex-1"
          @keyup.enter="applySearch"
        />
        <UButton color="primary" @click="applySearch">
          搜索
        </UButton>
      </div>

      <div v-if="types.length" class="flex gap-2 overflow-x-auto pb-1">
        <UButton
          v-for="t in types"
          :key="t.id"
          color="neutral"
          :variant="effectiveTypeId === t.id && !q ? 'subtle' : 'ghost'"
          class="shrink-0"
          @click="pickType(t.id)"
        >
          <img :src="typeIconUrl(t.icon)" :alt="t.name" class="size-5 rounded mr-2">
          <span class="text-sm">
            {{ t.name }}
          </span>
        </UButton>
      </div>
    </div>

    <div v-if="shops.length === 0" class="text-sm text-muted">
      暂无商户
    </div>

    <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      <NuxtLink
        v-for="s in shops"
        :key="s.id"
        :to="`/shops/${s.id}`"
        class="block"
      >
        <UCard class="h-full hover:shadow-sm transition-shadow">
          <div class="flex flex-col gap-3">
            <div class="aspect-video rounded-lg overflow-hidden bg-muted">
              <img :src="shopCover(s.images)" :alt="s.name" class="w-full h-full object-cover">
            </div>

            <div class="space-y-2">
              <div class="text-base font-semibold text-highlighted line-clamp-1">
                {{ s.name }}
              </div>

              <div class="flex items-center justify-between text-sm text-muted">
                <div class="flex items-center gap-1">
                  <UIcon name="i-lucide-star" class="size-4 text-primary" />
                  <span>{{ shopScore(s.score) }}</span>
                  <span>·</span>
                  <span>{{ s.comments }} 条</span>
                </div>
                <div v-if="s.avgPrice">
                  ￥{{ formatFen(s.avgPrice) }}/人
                </div>
              </div>

              <div class="text-sm text-muted line-clamp-1">
                {{ s.area }} · {{ s.address }}
              </div>
            </div>
          </div>
        </UCard>
      </NuxtLink>
    </div>

    <div class="flex justify-center pt-2">
      <UButton
        variant="subtle"
        color="neutral"
        :loading="isLoading"
        :disabled="isReachEnd"
        @click="loadMore"
      >
        {{ isReachEnd ? '没有更多了' : '加载更多' }}
      </UButton>
    </div>
  </div>
</template>
