import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { createPinia } from 'pinia'
import piniaPluginPersistedstate from 'pinia-plugin-persistedstate'

// 全局样式
import './assets/styles/google-theme.css'

// 导入工具
import './utils'
// 导入模拟数据
if (process.env.NODE_ENV === 'development') {
  import('./mock')
}

// 引入Element Plus
import * as ElementPlusComponents from 'element-plus'
import 'element-plus/theme-chalk/index.css'

import * as ElementPlusIconsVue from '@element-plus/icons-vue'

// 抑制ResizeObserver错误
const originalError = window.console.error;
window.console.error = (...args) => {
  if (args[0] && typeof args[0] === 'string' && args[0].includes('ResizeObserver loop')) {
    return;
  }
  originalError.apply(window.console, args);
};

const app = createApp(App)

// 注册所有Element Plus图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

// 注册所有Element Plus组件
for (const [key, component] of Object.entries(ElementPlusComponents)) {
  app.component(key, component)
}

// 注册Element Plus全局服务
const { ElMessage, ElMessageBox, ElLoading } = ElementPlusComponents;
app.config.globalProperties.$message = ElMessage
app.config.globalProperties.$msgbox = ElMessageBox
app.config.globalProperties.$loading = ElLoading.service

// 注册loading指令
app.directive('loading', ElementPlusComponents.ElLoadingDirective)

// 配置Pinia并添加持久化插件
const pinia = createPinia()
pinia.use(piniaPluginPersistedstate)

app.use(pinia)
app.use(router)

app.mount('#app')
