<script setup lang="ts">
definePageMeta({
  middleware: 'auth'
})

import type { Blog, ShopType } from '~/types/api'
import { firstCsvItem, resolveImgUrl } from '~/utils/format'

const { $apiData } = useNuxtApp()
const toast = useToast()

const search = ref('')

async function toShopSearch() {
  const q = search.value.trim()
  if (!q) return

  await navigateTo({
    path: '/shops',
    query: { q }
  })
}

const { data: types } = await useAsyncData('shop-types', () => $apiData<ShopType[]>('/shop-type/list'))

function typeIconUrl(icon: string) {
  return resolveImgUrl(icon) || '/imgs/types/ms.png'
}

const blogPage = ref(1)
const blogs = ref<Blog[]>([])
const isLoadingMore = ref(false)
const isReachEnd = ref(false)

const { data: firstBlogs } = await useAsyncData('hot-blogs-1', () => $apiData<Blog[]>('/blog/hot?current=1'))
blogs.value = firstBlogs.value ?? []

function blogCover(images: string) {
  return resolveImgUrl(firstCsvItem(images)) || '/imgs/blogs/blog1.jpg'
}

async function loadMoreBlogs() {
  if (isLoadingMore.value || isReachEnd.value) return

  isLoadingMore.value = true
  const next = blogPage.value + 1

  try {
    const more = await $apiData<Blog[]>(`/blog/hot?current=${next}`)
    if (!more || more.length === 0) {
      isReachEnd.value = true
      return
    }
    blogs.value.push(...more)
    blogPage.value = next
  } catch (error) {
    toast.add({
      title: '加载失败',
      description: (error as any)?.statusMessage || (error as any)?.message,
      color: 'error',
      icon: 'i-lucide-x-circle'
    })
  } finally {
    isLoadingMore.value = false
  }
}

async function likeBlog(blog: Blog) {
  try {
    await $apiData<void>(`/blog/like/${blog.id}`, { method: 'PUT' })
    blog.liked = (blog.liked || 0) + 1
  } catch (error) {
    const statusCode = (error as any)?.statusCode
    if (statusCode === 401) {
      toast.add({
        title: '请先登录',
        color: 'warning',
        icon: 'i-lucide-shield-alert'
      })
      await navigateTo({ path: '/login', query: { redirect: '/' } })
      return
    }

    toast.add({
      title: '操作失败',
      description: (error as any)?.statusMessage || (error as any)?.message,
      color: 'error',
      icon: 'i-lucide-x-circle'
    })
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
            placeholder="搜索商户名"
            class="flex-1"
            @keyup.enter="toShopSearch"
          />
          <UButton color="primary" @click="toShopSearch">
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
        <UButton to="/shops" color="neutral" variant="ghost" trailing-icon="i-lucide-arrow-right">
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

      <div v-if="blogs.length === 0" class="text-sm text-muted">
        暂无数据
      </div>

      <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <UCard v-for="b in blogs" :key="b.id" class="overflow-hidden">
          <div class="flex flex-col gap-3">
            <div class="aspect-video rounded-lg overflow-hidden bg-muted">
              <img :src="blogCover(b.images)" :alt="b.title" class="w-full h-full object-cover">
            </div>

            <div class="space-y-2">
              <div class="text-base font-semibold text-highlighted line-clamp-2">
                {{ b.title }}
              </div>

              <div class="flex items-center justify-between gap-3">
                <div class="flex items-center gap-2 min-w-0">
                  <UAvatar :src="resolveImgUrl(b.icon) || '/imgs/icons/default-icon.png'" size="xs" />
                  <span class="text-sm text-muted truncate">
                    {{ b.name || '匿名用户' }}
                  </span>
                </div>

                <UButton size="xs" variant="ghost" color="neutral" @click="likeBlog(b)">
                  <UIcon name="i-lucide-thumbs-up" class="size-4" />
                  <span class="ml-1 text-sm">
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
