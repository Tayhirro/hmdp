<script setup lang="ts">
import type { BlogCard, BlogDetail, BlogLikeState, CursorPage, ShopType } from '~/types/api'
import { firstCsvItem, resolveImgUrl } from '~/utils/format'

definePageMeta({
  middleware: 'auth'
})

interface ApiErrorDetails {
  message?: string
  statusCode?: number
  statusMessage?: string
}

function getApiErrorDetails(error: unknown): ApiErrorDetails {
  return typeof error === 'object' && error !== null ? error as ApiErrorDetails : {}
}

const { $apiData } = useNuxtApp()
const toast = useToast()

const search = ref('')

async function toSearch() {
  const q = search.value.trim()
  if (!q) return

  await navigateTo({
    path: '/search',
    query: { q }
  })
}

const { data: types } = await useAsyncData('shop-types', () => $apiData<ShopType[]>('/shop-type/list'))

function typeIconUrl(icon: string) {
  return resolveImgUrl(icon) || '/imgs/types/ms.png'
}

const blogs = ref<BlogCard[]>([])
const hotCursor = ref<string>()
const isLoadingMore = ref(false)
const isReachEnd = ref(false)
const pendingLikeIds = reactive(new Set<number>())

const { data: firstBlogs } = await useAsyncData(
  'hot-blogs-first',
  () => $apiData<CursorPage<BlogCard>>('/blog/hot?limit=10')
)
blogs.value = (firstBlogs.value?.list ?? []).map(b => ({ ...b, isLike: Boolean(b.isLike) }))
hotCursor.value = firstBlogs.value?.nextCursor
isReachEnd.value = !firstBlogs.value?.hasMore

function blogCover(images: string) {
  return resolveImgUrl(firstCsvItem(images)) || '/imgs/blogs/blog1.jpg'
}

async function loadMoreBlogs() {
  if (isLoadingMore.value || isReachEnd.value) return

  isLoadingMore.value = true
  try {
    const query = new URLSearchParams({ limit: '10' })
    if (hotCursor.value) query.set('cursor', hotCursor.value)
    const page = await $apiData<CursorPage<BlogCard>>(`/blog/hot?${query}`)
    blogs.value.push(...(page?.list ?? []).map(b => ({ ...b, isLike: Boolean(b.isLike) })))
    hotCursor.value = page?.nextCursor
    isReachEnd.value = !page?.hasMore
  } catch (error) {
    const apiError = getApiErrorDetails(error)
    toast.add({
      title: '加载失败',
      description: apiError.statusMessage || apiError.message,
      color: 'error',
      icon: 'i-lucide-x-circle'
    })
  } finally {
    isLoadingMore.value = false
  }
}

/**
 * 客户端只表达意图，不推算结果：请求中禁止同博客重复提交；
 * 成功时以服务端 { liked, likeCount } 覆盖本地，结果不明时 GET 回源校准。
 */
async function likeBlog(blog: BlogCard) {
  if (pendingLikeIds.has(blog.id)) return

  const shouldLike = !blog.isLike
  pendingLikeIds.add(blog.id)
  try {
    const state = await $apiData<BlogLikeState>(`/blog/${blog.id}/like`, {
      method: shouldLike ? 'PUT' : 'DELETE'
    })
    if (!state) {
      throw new Error('点赞状态响应为空')
    }
    blog.isLike = state.liked
    blog.liked = state.likeCount
  } catch (error) {
    const apiError = getApiErrorDetails(error)
    if (apiError.statusCode === 401) {
      toast.add({
        title: '请先登录',
        color: 'warning',
        icon: 'i-lucide-shield-alert'
      })
      await navigateTo({ path: '/login', query: { redirect: '/' } })
      return
    }

    try {
      const refreshed = await $apiData<BlogDetail>(`/blog/${blog.id}`)
      if (refreshed) {
        blog.isLike = Boolean(refreshed.isLike)
        blog.liked = refreshed.liked
      }
    } catch {
      // 网络仍不可用时保留原状态，不在客户端猜测写入结果。
    }

    toast.add({
      title: '操作失败',
      description: apiError.statusMessage || apiError.message,
      color: 'error',
      icon: 'i-lucide-x-circle'
    })
  } finally {
    pendingLikeIds.delete(blog.id)
  }
}
</script>

<template>
  <div class="space-y-6">
    <UCard class="overflow-hidden">
      <div class="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
        <div>
          <p class="text-sm text-muted">
            今天想去哪家店？
          </p>
          <h1 class="text-2xl font-semibold text-highlighted">
            发现好店与探店笔记
          </h1>
        </div>

        <div class="flex gap-2 w-full md:max-w-md">
          <UInput
            v-model="search"
            icon="i-lucide-search"
            placeholder="搜索店铺、笔记或用户"
            class="flex-1"
            @keyup.enter="toSearch"
          />
          <UButton
            color="primary"
            @click="toSearch"
          >
            搜索
          </UButton>
        </div>
      </div>
    </UCard>

    <section class="space-y-3">
      <div class="flex items-center justify-between">
        <h2 class="text-lg font-semibold text-highlighted">
          分类
        </h2>
        <UButton
          to="/shops"
          color="neutral"
          variant="ghost"
          trailing-icon="i-lucide-arrow-right"
        >
          全部商户
        </UButton>
      </div>

      <div class="grid grid-cols-5 sm:grid-cols-8 md:grid-cols-10 gap-3">
        <UButton
          v-for="t in (types || [])"
          :key="t.id"
          :to="{ path: '/shops', query: { typeId: String(t.id) } }"
          color="neutral"
          variant="ghost"
          class="flex-col gap-2 h-auto py-3"
        >
          <img
            :src="typeIconUrl(t.icon)"
            :alt="t.name"
            class="size-10 rounded-xl bg-muted object-contain"
          >
          <span class="text-xs text-default line-clamp-1">
            {{ t.name }}
          </span>
        </UButton>
      </div>
    </section>

    <section class="space-y-3">
      <div class="flex items-center justify-between">
        <h2 class="text-lg font-semibold text-highlighted">
          热门笔记
        </h2>
      </div>

      <div
        v-if="blogs.length === 0"
        class="text-sm text-muted"
      >
        暂无数据
      </div>

      <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <UCard
          v-for="b in blogs"
          :key="b.id"
          class="overflow-hidden"
        >
          <div class="flex flex-col gap-3">
            <NuxtLink
              :to="`/blogs/${b.id}`"
              class="aspect-video rounded-lg overflow-hidden bg-muted block"
            >
              <img
                :src="blogCover(b.images)"
                :alt="b.title"
                class="w-full h-full object-cover"
              >
            </NuxtLink>

            <div class="space-y-2">
              <NuxtLink
                :to="`/blogs/${b.id}`"
                class="text-base font-semibold text-highlighted line-clamp-2 hover:text-primary"
              >
                {{ b.title }}
              </NuxtLink>

              <div class="flex items-center justify-between gap-3">
                <NuxtLink
                  :to="`/users/${b.userId}`"
                  class="flex items-center gap-2 min-w-0"
                >
                  <UAvatar
                    :src="resolveImgUrl(b.icon) || '/imgs/icons/default-icon.png'"
                    size="xs"
                  />
                  <span class="text-sm text-muted truncate">
                    {{ b.name || '匿名用户' }}
                  </span>
                </NuxtLink>

                <UButton
                  size="xs"
                  variant="ghost"
                  :color="b.isLike ? 'primary' : 'neutral'"
                  :loading="pendingLikeIds.has(b.id)"
                  :disabled="pendingLikeIds.has(b.id)"
                  @click="likeBlog(b)"
                >
                  <UIcon
                    name="i-lucide-thumbs-up"
                    class="size-4"
                    :class="b.isLike ? 'text-primary' : 'text-muted'"
                  />
                  <span
                    class="ml-1 text-sm"
                    :class="b.isLike ? 'text-primary' : 'text-muted'"
                  >
                    {{ b.liked }}
                  </span>
                </UButton>
              </div>
            </div>
          </div>
        </UCard>
      </div>

      <div class="flex justify-center pt-2">
        <UButton
          variant="subtle"
          color="neutral"
          :loading="isLoadingMore"
          :disabled="isReachEnd"
          @click="loadMoreBlogs"
        >
          {{ isReachEnd ? '没有更多了' : '加载更多' }}
        </UButton>
      </div>
    </section>
  </div>
</template>
