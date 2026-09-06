<script setup lang="ts">
import type { BlogDetail } from '~/types/api'

definePageMeta({ middleware: 'auth' })

const route = useRoute()
const auth = useAuth()
const { $apiData } = useNuxtApp()
const id = computed(() => Number(route.params.id))

await auth.fetchMe()
const { data: blog } = await useAsyncData(
  () => `blog-edit-${id.value}`,
  () => $apiData<BlogDetail>(`/blog/${id.value}`),
  { watch: [id] }
)

if (blog.value && auth.user.value?.id !== blog.value.userId) {
  throw createError({ statusCode: 403, statusMessage: '只能编辑自己的笔记' })
}

async function onSaved(blogId: number) {
  await navigateTo(`/blogs/${blogId}`)
}
</script>

<template>
  <div class="mx-auto max-w-3xl space-y-4">
    <h1 class="text-2xl font-semibold text-highlighted">
      编辑笔记
    </h1>
    <BlogEditor
      v-if="blog"
      :blog="blog"
      @saved="onSaved"
    />
  </div>
</template>
