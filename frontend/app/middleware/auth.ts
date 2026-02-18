export default defineNuxtRouteMiddleware(async (to) => {
  const auth = useAuth()

  if (!auth.token.value) {
    return navigateTo({
      path: '/login',
      query: { redirect: to.fullPath }
    })
  }

  if (!auth.user.value) {
    await auth.fetchMe().catch(() => null)
  }
})

