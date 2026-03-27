import { createApp } from 'vue'
import { createPinia } from 'pinia'
import piniaPluginPersistedstate from 'pinia-plugin-persistedstate'
import * as ElementPlusComponents from 'element-plus'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import 'element-plus/theme-chalk/index.css'

import App from './App.vue'
import router from './router'
import './assets/styles/google-theme.css'
import './assets/styles/app-theme.css'
import './utils'

if (process.env.NODE_ENV === 'development') {
  import('./mock')
}

const originalError = window.console.error
window.console.error = (...args) => {
  if (args[0] && typeof args[0] === 'string' && args[0].includes('ResizeObserver loop')) {
    return
  }
  originalError.apply(window.console, args)
}

const app = createApp(App)

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

for (const [key, component] of Object.entries(ElementPlusComponents)) {
  app.component(key, component)
}

const { ElMessage, ElMessageBox, ElLoading } = ElementPlusComponents
app.config.globalProperties.$message = ElMessage
app.config.globalProperties.$msgbox = ElMessageBox
app.config.globalProperties.$loading = ElLoading.service
app.directive('loading', ElementPlusComponents.ElLoadingDirective)

const pinia = createPinia()
pinia.use(piniaPluginPersistedstate)

app.use(pinia)
app.use(router)
app.mount('#app')
