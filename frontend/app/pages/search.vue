<script setup lang="ts">
import type {
  BlogCard,
  PageResult,
  SearchScope,
  ShopSearchItem,
  UnifiedSearchResult,
  UserDTO
} from '~/types/api'
import { firstCsvItem, formatFen, resolveImgUrl } from '~/utils/format'
import { getApiErrorMessage } from '~/utils/api-error'

definePageMeta({ middleware: 'auth' })

type SearchTab = 'ALL' | SearchScope

const route = useRoute()
const router = useRouter()
const { $apiData } = useNuxtApp()
const toast = useToast()

const input = ref(typeof route.query.q === 'string' ? route.query.q : '')
const tab = computed<SearchTab>(() => {
  const value = route.query.scope
  return value === 'SHOP' || value === 'BLOG' || value === 'USER' ? value : 'ALL'
})
const keyword = computed(() => typeof route.query.q === 'string' ? route.query.q.trim() : '')

const shops = ref<ShopSearchItem[]>([])
const blogs = ref<BlogCard[]>([])
const users = ref<UserDTO[]>([])
const totals = reactive<Record<SearchScope, number>>({ SHOP: 0, BLOG: 0, USER: 0 })
const hasMoreByScope = reactive<Record<SearchScope, boolean>>({ SHOP: false, BLOG: false, USER: false })
const current = ref(1)
const loading = ref(false)

const tabs: Array<{ value: SearchTab, label: string }> = [
  { value: 'ALL', label: '综合' },
  { value: 'SHOP', label: '店铺' },
  { value: 'BLOG', label: '笔记' },
  { value: 'USER', label: '用户' }
]

function submit() {
  const q = input.value.trim()
  if (!q) return
  router.push({ path: '/search', query: { q, scope: tab.value === 'ALL' ? undefined : tab.value } })
}

function changeTab(value: SearchTab) {
  router.push({
    path: '/search',
    query: { q: keyword.value || undefined, scope: value === 'ALL' ? undefined : value }
  })
}

function clearResults() {
  shops.value = []
  blogs.value = []
  users.value = []
  totals.SHOP = 0
  totals.BLOG = 0
  totals.USER = 0
  hasMoreByScope.SHOP = false
  hasMoreByScope.BLOG = false
  hasMoreByScope.USER = false
}

function applyUnified(result?: UnifiedSearchResult) {
  clearResults()
  for (const section of result?.sections || []) {
    totals[section.scope] = section.total
    hasMoreByScope[section.scope] = section.hasMore
    if (section.scope === 'SHOP') shops.value = section.items as ShopSearchItem[]
    if (section.scope === 'BLOG') blogs.value = section.items as BlogCard[]
    if (section.scope === 'USER') users.value = section.items as UserDTO[]
  }
}

async function load(reset = false) {
  if (!keyword.value || loading.value) {
    if (!keyword.value) clearResults()
    return
  }
  if (reset) {
    current.value = 1
    clearResults()
  }
  loading.value = true
  try {
    if (tab.value === 'ALL') {
      const result = await $apiData<UnifiedSearchResult>(`/search?keyword=${encodeURIComponent(keyword.value)}&pageSize=5`)
      applyUnified(result)
      return
    }

    const path = tab.value === 'SHOP' ? 'shops' : tab.value === 'BLOG' ? 'blogs' : 'users'
    const pageSize = tab.value === 'SHOP' ? '' : '&pageSize=12'
    const page = await $apiData<PageResult<ShopSearchItem | BlogCard | UserDTO>>(
      `/search/${path}?keyword=${encodeURIComponent(keyword.value)}&current=${current.value}${pageSize}`
    )
    const list = page?.list || []
    if (tab.value === 'SHOP') shops.value.push(...list as ShopSearchItem[])
    if (tab.value === 'BLOG') blogs.value.push(...list as BlogCard[])
    if (tab.value === 'USER') users.value.push(...list as UserDTO[])
    totals[tab.value] = page?.total ?? 0
    hasMoreByScope[tab.value] = Boolean(page?.hasMore)
  } catch (error) {
    toast.add({
      title: '搜索失败',
      description: getApiErrorMessage(error),
      color: 'error'
    })
  } finally {
    loading.value = false
  }
}

async function loadMore() {
  if (tab.value === 'ALL' || !hasMoreByScope[tab.value]) return
  current.value += 1
  await load(false)
}

function cover(images: string) {
  return resolveImgUrl(firstCsvItem(images)) || '/imgs/blogs/blog1.jpg'
}

await load(true)
watch([keyword, tab], () => {
  input.value = keyword.value
  load(true)
})
</script>

<template>
  <div class="space-y-6">
    <div class="mx-auto flex max-w-2xl gap-2">
      <UInput
        v-model="input"
        icon="i-lucide-search"
        placeholder="搜索店铺、笔记或用户"
        class="flex-1"
        @keyup.enter="submit"
      />
      <UButton
        :loading="loading"
        @click="submit"
      >
        搜索
      </UButton>
    </div>

    <div class="flex justify-center gap-2 overflow-x-auto">
      <UButton
        v-for="item in tabs"
        :key="item.value"
        :variant="tab === item.value ? 'solid' : 'outline'"
        @click="changeTab(item.value)"
      >
        {{ item.label }}
      </UButton>
    </div>

    <UCard v-if="!keyword">
      <div class="py-10 text-center text-sm text-muted">
        输入关键词后，可以同时查找店铺、探店笔记和用户。
      </div>
    </UCard>

    <template v-else>
      <section
        v-if="tab === 'ALL' || tab === 'SHOP'"
        class="space-y-3"
      >
        <div class="flex items-center justify-between">
          <h2 class="text-lg font-semibold text-highlighted">
            店铺 <span class="text-sm font-normal text-muted">{{ totals.SHOP }}</span>
          </h2>
          <UButton
            v-if="tab === 'ALL' && hasMoreByScope.SHOP"
            color="neutral"
            variant="ghost"
            @click="changeTab('SHOP')"
          >
            查看更多
          </UButton>
        </div>
        <div
          v-if="shops.length"
          class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3"
        >
          <NuxtLink
            v-for="shop in shops"
            :key="shop.id"
            :to="`/shops/${shop.id}`"
          >
            <UCard class="h-full">
              <img
                :src="cover(shop.images)"
                :alt="shop.name"
                class="aspect-video w-full rounded-lg object-cover"
              >
              <div class="mt-3 font-semibold text-highlighted">{{ shop.name }}</div>
              <div class="mt-1 text-sm text-muted">{{ shop.area }} · {{ shop.address }}</div>
              <div class="mt-1 text-sm text-muted">{{ (shop.score / 10).toFixed(1) }} 分 · ￥{{ formatFen(shop.avgPrice) }}/人</div>
            </UCard>
          </NuxtLink>
        </div>
        <p
          v-else-if="!loading"
          class="text-sm text-muted"
        >
          没有匹配的店铺
        </p>
      </section>

      <section
        v-if="tab === 'ALL' || tab === 'BLOG'"
        class="space-y-3"
      >
        <div class="flex items-center justify-between">
          <h2 class="text-lg font-semibold text-highlighted">
            笔记 <span class="text-sm font-normal text-muted">{{ totals.BLOG }}</span>
          </h2>
          <UButton
            v-if="tab === 'ALL' && hasMoreByScope.BLOG"
            color="neutral"
            variant="ghost"
            @click="changeTab('BLOG')"
          >
            查看更多
          </UButton>
        </div>
        <div
          v-if="blogs.length"
          class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3"
        >
          <BlogCardItem
            v-for="blog in blogs"
            :key="blog.id"
            :blog="blog"
          />
        </div>
        <p
          v-else-if="!loading"
          class="text-sm text-muted"
        >
          没有匹配的笔记
        </p>
      </section>

      <section
        v-if="tab === 'ALL' || tab === 'USER'"
        class="space-y-3"
      >
        <div class="flex items-center justify-between">
          <h2 class="text-lg font-semibold text-highlighted">
            用户 <span class="text-sm font-normal text-muted">{{ totals.USER }}</span>
          </h2>
          <UButton
            v-if="tab === 'ALL' && hasMoreByScope.USER"
            color="neutral"
            variant="ghost"
            @click="changeTab('USER')"
          >
            查看更多
          </UButton>
        </div>
        <div
          v-if="users.length"
          class="grid gap-3 sm:grid-cols-2 lg:grid-cols-3"
        >
          <NuxtLink
            v-for="user in users"
            :key="user.id"
            :to="`/users/${user.id}`"
          >
            <UCard class="h-full">
              <div class="flex items-center gap-3">
                <UAvatar
                  :src="resolveImgUrl(user.icon) || '/imgs/icons/default-icon.png'"
                  size="lg"
                />
                <div><div class="font-semibold text-highlighted">{{ user.nickName }}</div><div class="text-xs text-muted">用户 ID：{{ user.id }}</div></div>
              </div>
            </UCard>
          </NuxtLink>
        </div>
        <p
          v-else-if="!loading"
          class="text-sm text-muted"
        >
          没有匹配的用户
        </p>
      </section>

      <div
        v-if="tab !== 'ALL'"
        class="flex justify-center"
      >
        <UButton
          v-if="hasMoreByScope[tab]"
          variant="outline"
          :loading="loading"
          @click="loadMore"
        >
          加载更多
        </UButton>
        <span
          v-else-if="shops.length || blogs.length || users.length"
          class="text-sm text-muted"
        >没有更多了</span>
      </div>
    </template>
  </div>
</template>
