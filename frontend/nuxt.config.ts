// https://nuxt.com/docs/api/configuration/nuxt-config

// Nuxt typecheck (tsconfig.node.json) intentionally omits Node globals types.
// Read env vars without referencing `process` directly.
const runtimeProcess = (globalThis as typeof globalThis & {
  process?: { env?: Record<string, string | undefined> }
}).process
const env = runtimeProcess?.env ?? {}

export default defineNuxtConfig({
  modules: [
    '@nuxt/eslint',
    '@nuxt/ui'
  ],

  devtools: {
    enabled: true
  },

  css: ['~/assets/css/main.css'],
  ui: {
    // Avoid remote font metadata fetches (googleicons) in restricted networks.
    fonts: false
  },

  runtimeConfig: {
    public: {
      apiBase: env.NUXT_PUBLIC_API_BASE || '/api'
    }
  },

  routeRules: {
    '/api/**': {
      proxy: `${env.NUXT_DEV_PROXY_TARGET || 'http://localhost:9090'}/**`
    }
  },

  compatibilityDate: '2025-01-15',

  nitro: {
    devProxy: {
      '/api': {
        target: env.NUXT_DEV_PROXY_TARGET || 'http://localhost:9090',
        changeOrigin: true
      }
    }
  },

  eslint: {
    config: {
      stylistic: {
        commaDangle: 'never',
        braceStyle: '1tbs'
      }
    }
  }
})
