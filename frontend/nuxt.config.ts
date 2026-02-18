// https://nuxt.com/docs/api/configuration/nuxt-config

// Nuxt typecheck (tsconfig.node.json) intentionally omits Node globals types.
// Read env vars without referencing `process` directly.
const env = ((globalThis as any).process?.env || {}) as Record<string, string | undefined>

export default defineNuxtConfig({
  modules: [
    '@nuxt/eslint',
    '@nuxt/ui'
  ],

  ui: {
    // Avoid remote font metadata fetches (googleicons) in restricted networks.
    fonts: false
  },

  devtools: {
    enabled: true
  },

  css: ['~/assets/css/main.css'],

  runtimeConfig: {
    public: {
      apiBase: env.NUXT_PUBLIC_API_BASE || '/api'
    }
  },

  routeRules: {
    '/api/**': {
      proxy: `${env.NUXT_DEV_PROXY_TARGET || 'http://localhost:8081'}/**`
    }
  },

  nitro: {
    devProxy: {
      '/api': {
        target: env.NUXT_DEV_PROXY_TARGET || 'http://localhost:8081',
        changeOrigin: true
      }
    }
  },

  compatibilityDate: '2025-01-15',

  eslint: {
    config: {
      stylistic: {
        commaDangle: 'never',
        braceStyle: '1tbs'
      }
    }
  }
})
