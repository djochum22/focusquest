import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { configureApiClient } from './api/client'
import { useAuthStore } from './stores/authStore'
// Bundled with the app rather than loaded from a font service, so it works offline.
import '@fontsource-variable/atkinson-hyperlegible-next'
import '@fontsource/young-serif'
import './style.css'

const app = createApp(App)
app.use(createPinia())

const auth = useAuthStore()
configureApiClient({
  getToken: () => auth.token,
  // The backend rejected our token mid-session: drop it and send the user to sign in again.
  onUnauthorized: () => {
    auth.logout()
    const current = router.currentRoute.value
    if (current.meta.public) return
    void router.replace({
      name: 'login',
      query: { redirect: current.fullPath, expired: '1' },
    })
  },
})

app.use(router)
app.mount('#app')
