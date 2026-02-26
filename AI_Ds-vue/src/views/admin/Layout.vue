<template>
  <div class="admin-layout">
    <el-container class="layout-container">
      <el-aside :width="collapsed ? '64px' : '240px'" class="layout-aside">
        <div class="logo-container">
          <img src="../../assets/logo.png" alt="Logo" class="logo" />
          <transition name="fade-text">
            <span v-if="!collapsed" class="logo-title">系统管理后台</span>
          </transition>
        </div>

        <el-scrollbar class="menu-scrollbar">
          <el-menu
            :default-active="activeMenu"
            class="layout-menu"
            :collapse="collapsed"
            router
            :collapse-transition="false"
          >
            <el-menu-item index="/admin/dashboard">
              <el-icon><HomeFilled /></el-icon>
              <template #title>首页</template>
            </el-menu-item>

            <el-menu-item index="/admin/user-management">
              <el-icon><User /></el-icon>
              <template #title>用户管理</template>
            </el-menu-item>

            <el-menu-item index="/admin/class-management">
              <el-icon><OfficeBuilding /></el-icon>
              <template #title>班级管理</template>
            </el-menu-item>

            <el-menu-item index="/admin/experiment-management">
              <el-icon><DocumentCopy /></el-icon>
              <template #title>实验管理</template>
            </el-menu-item>

            <el-menu-item index="/admin/system-log">
              <el-icon><DataAnalysis /></el-icon>
              <template #title>系统日志</template>
            </el-menu-item>

            <div class="menu-divider"></div>

            <el-menu-item index="/admin/profile">
              <el-icon><Setting /></el-icon>
              <template #title>个人设置</template>
            </el-menu-item>
          </el-menu>
        </el-scrollbar>
      </el-aside>

      <el-container class="layout-main">
        <el-header class="layout-header">
          <div class="header-left">
            <el-icon class="fold-icon" @click="toggleSidebar">
              <Fold v-if="!collapsed" />
              <Expand v-else />
            </el-icon>
            <el-breadcrumb separator="/">
              <el-breadcrumb-item :to="{ path: '/admin/dashboard' }">首页</el-breadcrumb-item>
              <el-breadcrumb-item v-for="(item, index) in breadcrumbs" :key="index">
                {{ item }}
              </el-breadcrumb-item>
            </el-breadcrumb>
          </div>

          <div class="header-right">
            <!-- PTA Cookie 过期告警 -->
            <el-tooltip v-if="ptaCookieExpired" content="PTA Cookie 已过期，数据同步暂停" placement="bottom">
              <el-badge is-dot type="danger">
                <el-icon class="header-icon" style="color: #ea4335" @click="goToCookieAlert">
                  <WarningFilled />
                </el-icon>
              </el-badge>
            </el-tooltip>

            <el-tooltip content="全屏" placement="bottom">
              <el-icon class="header-icon" @click="toggleFullScreen">
                <FullScreen />
              </el-icon>
            </el-tooltip>

            <el-dropdown trigger="click" @command="handleCommand">
              <div class="user-info">
                <el-avatar :size="34">
                  <span>{{ (userInfo.name || '管')[0] }}</span>
                </el-avatar>
                <span class="username">{{ userInfo.name || '管理员' }}</span>
                <el-icon class="arrow-icon"><ArrowDown /></el-icon>
              </div>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="profile">
                    <el-icon><Setting /></el-icon> 个人信息
                  </el-dropdown-item>
                  <el-dropdown-item command="logout" divided>
                    <el-icon><SwitchButton /></el-icon> 退出登录
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </el-header>

        <el-main class="layout-content">
          <router-view v-slot="{ Component }">
            <transition name="page-slide" mode="out-in">
              <component :is="Component" />
            </transition>
          </router-view>
        </el-main>

        <el-footer class="layout-footer">
          智能学情分析与个性化实验能力提升平台 - 管理后台 © 2025
        </el-footer>
      </el-container>
    </el-container>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useUserStore } from '../../store'
import {
  HomeFilled, User, OfficeBuilding, DocumentCopy, DataAnalysis,
  Setting, Fold, Expand, FullScreen, ArrowDown, SwitchButton, WarningFilled
} from '@element-plus/icons-vue'
import { getPtaCookieStatus } from '../../api/tap'

const route = useRoute()
const router = useRouter()
const collapsed = ref(false)
const userStore = useUserStore()

const userInfo = computed(() => userStore.userInfo || { name: '管理员' })

const activeMenu = computed(() => route.path)

const breadcrumbs = computed(() => {
  const pathMap = {
    dashboard: '首页', 'user-management': '用户管理', 'class-management': '班级管理',
    'experiment-management': '实验管理', 'system-log': '系统日志', profile: '个人设置'
  }
  const paths = route.path.split('/').filter(Boolean)
  return paths[0] === 'admin' ? paths.slice(1).map(p => pathMap[p.split('/')[0]] || p) : []
})

const toggleSidebar = () => { collapsed.value = !collapsed.value }

const toggleFullScreen = () => {
  if (!document.fullscreenElement) document.documentElement.requestFullscreen()
  else document.exitFullscreen?.()
}

const handleCommand = (cmd) => {
  if (cmd === 'profile') router.push('/admin/profile')
  else if (cmd === 'logout') {
    ElMessageBox.confirm('确定要退出登录吗?', '提示', {
      confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning'
    }).then(() => {
      userStore.logout()
      sessionStorage.clear()
      router.push('/login')
    }).catch(() => {})
  }
}

// --- PTA Cookie 告警 ---
const ptaCookieExpired = ref(false)

const checkPtaCookie = async () => {
  try {
    const res = await getPtaCookieStatus()
    const data = res?.data ?? res
    ptaCookieExpired.value = data?.status === 'EXPIRED'
  } catch { /* 爬虫服务未启动时忽略 */ }
}

const goToCookieAlert = () => {
  ElMessageBox.alert(
    'PTA 登录凭证已过期，自动登录重试失败。请通知相关教师在「班级管理 → PTA同步设置」中手动更新 Cookie，或检查爬虫服务器网络连接。',
    'PTA Cookie 过期告警',
    { confirmButtonText: '知道了', type: 'warning' }
  )
}

onMounted(() => {
  checkPtaCookie()
  // 每5分钟检查一次
  setInterval(checkPtaCookie, 5 * 60 * 1000)
  if (userInfo.value.role && userInfo.value.role !== 'admin') {
    ElMessageBox.alert('您没有管理员权限，请重新登录', '权限错误', {
      confirmButtonText: '确定',
      callback: () => {
        userStore.logout()
        router.push('/login')
      }
    })
  }
})
</script>

<style scoped>
.admin-layout { height: 100vh; width: 100%; }
.layout-container { height: 100%; }

.layout-aside {
  background: linear-gradient(180deg, #1a1a2e 0%, #202134 100%);
  transition: width 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
  border-right: 1px solid rgba(255,255,255,0.06);
}

.logo-container {
  height: 64px; display: flex; align-items: center;
  padding: 0 16px; gap: 12px;
  border-bottom: 1px solid rgba(255,255,255,0.08);
}

.logo {
  border-radius: 12px; border: 2px solid rgba(26,115,232,0.5);
  height: 40px; width: 40px; flex-shrink: 0; transition: all 0.3s;
}

.logo-title {
  font-size: 15px; font-weight: 700; color: rgba(255,255,255,0.9);
  white-space: nowrap; letter-spacing: 0.5px;
}

.menu-scrollbar { height: calc(100vh - 64px); }
.menu-scrollbar :deep(.el-scrollbar__bar.is-vertical) { width: 4px; right: 2px; }
.menu-scrollbar :deep(.el-scrollbar__thumb) { background: rgba(255,255,255,0.15); border-radius: 4px; }

.layout-menu {
  border-right: none; background: transparent !important;
  --el-menu-bg-color: transparent; --el-menu-text-color: #9aa0a6;
  --el-menu-active-color: #8ab4f8; --el-menu-hover-bg-color: rgba(26,115,232,0.1);
  --el-menu-hover-text-color: #d2e3fc; padding: 8px;
}

.layout-menu :deep(.el-menu-item) {
  border-radius: 8px; margin: 2px 0; height: 44px; line-height: 44px;
  font-size: 13.5px; transition: all 0.2s;
}

.layout-menu :deep(.el-menu-item.is-active) {
  background: linear-gradient(135deg, rgba(26,115,232,0.2), rgba(66,133,244,0.15)) !important;
  color: #aecbfa !important; font-weight: 600;
}

.layout-menu :deep(.el-menu-item.is-active)::before {
  content: ''; position: absolute; left: 0; top: 50%; transform: translateY(-50%);
  width: 3px; height: 20px; background: #8ab4f8; border-radius: 0 3px 3px 0;
}

.layout-menu :deep(.el-menu-item:hover) { background: rgba(26,115,232,0.1) !important; }
.layout-menu :deep(.el-icon) { font-size: 18px; }

.menu-divider { height: 1px; background: rgba(255,255,255,0.06); margin: 8px 12px; }

.layout-main { background: #f8f9fa; }

.layout-header {
  background: #fff; display: flex; align-items: center; justify-content: space-between;
  box-shadow: 0 1px 2px rgba(60,64,67,0.1); padding: 0 24px; height: 56px;
  border-bottom: 1px solid #dadce0;
}

.header-left { display: flex; align-items: center; gap: 12px; }

.fold-icon {
  cursor: pointer; font-size: 20px; color: #5f6368; padding: 6px;
  border-radius: 8px; transition: all 0.2s;
}
.fold-icon:hover { background: #f1f3f4; color: #202124; }

.header-right { display: flex; align-items: center; gap: 12px; }

.header-icon {
  cursor: pointer; font-size: 18px; color: #5f6368; padding: 6px;
  border-radius: 8px; transition: all 0.2s;
}
.header-icon:hover { background: #f1f3f4; color: #202124; }

.user-info {
  display: flex; align-items: center; cursor: pointer; padding: 4px 8px;
  border-radius: 8px; transition: background 0.2s; gap: 8px;
}
.user-info:hover { background: #f1f3f4; }

.username { font-size: 14px; color: #202124; font-weight: 500; }
.arrow-icon { font-size: 12px; color: #9aa0a6; }

.layout-content {
  background: #f8f9fa; padding: 24px; min-height: calc(100vh - 120px); overflow-y: auto;
}

.layout-footer {
  text-align: center; color: #9aa0a6; padding: 12px; font-size: 13px;
  background: #f8f9fa; border-top: 1px solid #dadce0;
}

.page-slide-enter-active, .page-slide-leave-active {
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}
.page-slide-enter-from { opacity: 0; transform: translateY(8px); }
.page-slide-leave-to { opacity: 0; transform: translateY(-4px); }

.fade-text-enter-active, .fade-text-leave-active { transition: opacity 0.2s; }
.fade-text-enter-from, .fade-text-leave-to { opacity: 0; }
</style>
