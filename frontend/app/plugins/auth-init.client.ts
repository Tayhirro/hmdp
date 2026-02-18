export default defineNuxtPlugin(async () => {
  const auth = useAuth()
  if (!auth.token.value) return

  await auth.fetchMe().catch(() => null)
})

