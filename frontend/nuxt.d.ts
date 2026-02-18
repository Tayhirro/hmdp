declare module '#app' {
  interface NuxtApp {
    $apiRaw: any
    $apiData: <T>(request: any, options?: any) => Promise<T | undefined>
  }
}

declare module 'vue' {
  interface ComponentCustomProperties {
    $apiRaw: any
    $apiData: <T>(request: any, options?: any) => Promise<T | undefined>
  }
}

export {}

