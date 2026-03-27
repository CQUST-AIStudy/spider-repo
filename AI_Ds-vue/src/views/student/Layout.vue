<template>
  <div class="student-layout">
    <el-container class="layout-container">
      <el-aside :width="collapsed ? '64px' : '240px'" class="layout-aside">
        <div class="logo-container">
          <img src="../../assets/logo.png" alt="Logo" class="logo" />
          <transition name="fade-text">
            <span v-if="!collapsed" class="logo-title">智能学习平台</span>
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
            <el-menu-item index="/student/dashboard">
              <el-icon><HomeFilled /></el-icon>
              <template #title>首页</template>
            </el-menu-item>

            <el-menu-item index="/student/experiments">
              <el-icon><Notebook /></el-icon>
              <template #title>实验列表</template>
            </el-menu-item>

            <el-menu-item index="/student/learning-analysis">
              <el-icon><DataAnalysis /></el-icon>
              <template #title>学情分析</template>
            </el-menu-item>

            <el-menu-item index="/student/ai-report">
              <el-icon><Document /></el-icon>
              <template #title>AI 报告生成</template>
            </el-menu-item>

            <el-menu-item index="/student/ai-assistant">
              <el-icon><ChatDotRound /></el-icon>
              <template #title>AI 学习助手</template>
            </el-menu-item>

            <el-menu-item index="/student/class-join">
              <el-icon><UserFilled /></el-icon>
              <template #title>教学班</template>
            </el-menu-item>

            <el-menu-item index="/student/practice">
              <el-icon><Collection /></el-icon>
              <template #title>推荐练习</template>
            </el-menu-item>

            <el-menu-item index="/student/weakness-training">
              <el-icon><Finished /></el-icon>
              <template #title>错题本/专项训练</template>
            </el-menu-item>

            <el-menu-item index="/student/ability-profile">
              <el-icon><TrendCharts /></el-icon>
              <template #title>能力画像</template>
            </el-menu-item>

            <div class="menu-divider"></div>

            <el-menu-item index="/student/profile">
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
              <el-breadcrumb-item :to="{ path: '/student/dashboard' }">首页</el-breadcrumb-item>
              <el-breadcrumb-item v-for="(item, index) in breadcrumbs" :key="index">
                {{ item }}
              </el-breadcrumb-item>
            </el-breadcrumb>
          </div>

          <div class="header-right">
            <el-tooltip content="全屏" placement="bottom">
              <el-icon class="header-icon" @click="toggleFullScreen">
                <FullScreen />
              </el-icon>
            </el-tooltip>

            <el-dropdown trigger="click" @command="handleCommand">
              <div class="user-info">
                <el-avatar :size="34">
                  <img src="../../assets/User/Cat.jpg" alt="avatar" style="width:100%;height:100%;object-fit:cover" />
                </el-avatar>
                <span class="username">{{ userInfo.name || '学生' }}</span>
                <el-icon class="arrow-icon"><ArrowDown /></el-icon>
              </div>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="profile">
                    <el-icon><Setting /></el-icon>
                    个人信息
                  </el-dropdown-item>
                  <el-dropdown-item command="logout" divided>
                    <el-icon><SwitchButton /></el-icon>
                    退出登录
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
          智能学情分析与个性化实验能力提升平台 © 2025
        </el-footer>
      </el-container>
    </el-container>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  HomeFilled,
  Notebook,
  DataAnalysis,
  Document,
  ChatDotRound,
  Collection,
  Finished,
  TrendCharts,
  Setting,
  Fold,
  Expand,
  FullScreen,
  UserFilled,
  ArrowDown,
  SwitchButton
} from '@element-plus/icons-vue'
import { useUserStore } from '../../store'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const collapsed = ref(false)

const userInfo = computed(() => userStore.userInfo || {})

const activeMenu = computed(() => {
  if (route.name === 'ExperimentDetail') return '/student/experiments'
  return route.path
})

const breadcrumbs = computed(() => {
  const pathMap = {
    dashboard: '首页',
    experiments: '实验列表',
    'experiment-detail': '实验详情',
    'learning-analysis': '学情分析',
    'ai-report': 'AI 报告生成',
    'ai-assistant': 'AI 学习助手',
    'class-join': '教学班',
    practice: '推荐练习',
    'weakness-training': '错题本/专项训练',
    'ability-profile': '能力画像',
    profile: '个人设置'
  }
  const paths = route.path.split('/').filter(Boolean)
  return paths[0] === 'student' ? paths.slice(1).map((part) => pathMap[part] || part) : []
})

function toggleSidebar() {
  collapsed.value = !collapsed.value
}

function toggleFullScreen() {
  if (!document.fullscreenElement) {
    document.documentElement.requestFullscreen()
    return
  }
  document.exitFullscreen?.()
}

function handleCommand(command) {
  if (command === 'profile') {
    router.push('/student/profile')
    return
  }
  if (command !== 'logout') return

  ElMessageBox.confirm('确定要退出登录吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    userStore.logout()
    sessionStorage.clear()
    router.push('/login')
    ElMessage.success('已退出登录')
  }).catch(() => {})
}

onMounted(() => {
  if (userStore.isLoggedIn) return
  router.push('/login')
  ElMessage.warning('请先登录')
})
</script>

<style scoped>
.student-layout { height: 100vh; width: 100%; }
.layout-container { height: 100%; }

.layout-aside {
  background: linear-gradient(180deg, #1a1a2e 0%, #202134 100%);
  transition: width 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
  border-right: 1px solid rgba(255, 255, 255, 0.06);
}

.logo-container {
  height: 64px;
  display: flex;
  align-items: center;
  padding: 0 16px;
  gap: 12px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.logo {
  border-radius: 12px;
  border: 2px solid rgba(26, 115, 232, 0.5);
  height: 40px;
  width: 40px;
  flex-shrink: 0;
  transition: all 0.3s;
}

.logo-title {
  font-size: 15px;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.9);
  white-space: nowrap;
  letter-spacing: 0.5px;
}

.menu-scrollbar { height: calc(100vh - 64px); }

.menu-scrollbar :deep(.el-scrollbar__bar.is-vertical) { width: 4px; right: 2px; }
.menu-scrollbar :deep(.el-scrollbar__thumb) { background: rgba(255, 255, 255, 0.15); border-radius: 4px; }

.layout-menu {
  border-right: none;
  background: transparent !important;
  --el-menu-bg-color: transparent;
  --el-menu-text-color: #9aa0a6;
  --el-menu-active-color: #8ab4f8;
  --el-menu-hover-bg-color: rgba(26, 115, 232, 0.1);
  --el-menu-hover-text-color: #d2e3fc;
  padding: 8px;
}

.layout-menu :deep(.el-menu-item) {
  border-radius: 8px;
  margin: 2px 0;
  height: 44px;
  line-height: 44px;
  font-size: 13.5px;
  transition: all 0.2s;
}

.layout-menu :deep(.el-menu-item.is-active) {
  background: linear-gradient(135deg, rgba(26, 115, 232, 0.2), rgba(66, 133, 244, 0.15)) !important;
  color: #aecbfa !important;
  font-weight: 600;
}

.layout-menu :deep(.el-menu-item.is-active)::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 20px;
  background: #8ab4f8;
  border-radius: 0 3px 3px 0;
}

.layout-menu :deep(.el-menu-item:hover) {
  background: rgba(26, 115, 232, 0.1) !important;
}

.layout-menu :deep(.el-icon) { font-size: 18px; }

.menu-divider {
  height: 1px;
  background: rgba(255, 255, 255, 0.06);
  margin: 8px 12px;
}

.layout-main { background: #f8f9fa; }

.layout-header {
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-shadow: 0 1px 2px rgba(60, 64, 67, 0.1);
  padding: 0 24px;
  height: 56px;
  border-bottom: 1px solid #dadce0;
}

.header-left { display: flex; align-items: center; gap: 12px; }

.fold-icon {
  cursor: pointer;
  font-size: 20px;
  color: #5f6368;
  padding: 6px;
  border-radius: 8px;
  transition: all 0.2s;
}

.fold-icon:hover { background: #f1f3f4; color: #202124; }

.header-right { display: flex; align-items: center; gap: 12px; }

.header-icon {
  cursor: pointer;
  font-size: 18px;
  color: #5f6368;
  padding: 6px;
  border-radius: 8px;
  transition: all 0.2s;
}

.header-icon:hover { background: #f1f3f4; color: #202124; }

.user-info {
  display: flex;
  align-items: center;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 8px;
  transition: background 0.2s;
  gap: 8px;
}

.user-info:hover { background: #f1f3f4; }

.username { font-size: 14px; color: #202124; font-weight: 500; }
.arrow-icon { font-size: 12px; color: #9aa0a6; }

.layout-content {
  background: #f8f9fa;
  padding: 24px;
  min-height: calc(100vh - 120px);
  overflow-y: auto;
}

.layout-footer {
  text-align: center;
  color: #9aa0a6;
  padding: 12px;
  font-size: 13px;
  background: #f8f9fa;
  border-top: 1px solid #dadce0;
}

.page-slide-enter-active,
.page-slide-leave-active {
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}

.page-slide-enter-from { opacity: 0; transform: translateY(8px); }
.page-slide-leave-to { opacity: 0; transform: translateY(-4px); }

.fade-text-enter-active,
.fade-text-leave-active { transition: opacity 0.2s; }

.fade-text-enter-from,
.fade-text-leave-to { opacity: 0; }
</style>
