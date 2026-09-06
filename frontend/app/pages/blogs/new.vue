<script setup lang="ts">
definePageMeta({ middleware: 'auth' })

async function onSaved(blogId: number) {
  await navigateTo(`/blogs/${blogId}`)
}
</script>

<template>
  <div class="mx-auto max-w-3xl space-y-4">
    <div>
      <p class="text-sm text-muted">
        记录真实体验
      </p>
      <h1 class="text-2xl font-semibold text-highlighted">
        发布探店笔记
      </h1>
    </div>
    <BlogEditor @saved="onSaved" />
  </div>
</template>
