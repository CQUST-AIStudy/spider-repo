<template>
  <div class="teacher-layout">
    <el-container class="layout-container">
      <el-aside :width="collapsed ? '76px' : '260px'" class="layout-aside">
        <div class="logo-container">
          <img src="../../assets/logo.png" alt="Logo" class="logo" />
          <transition name="fade-text">
            <div v-if="!collapsed" class="brand-copy">
              <span class="logo-kicker">Teacher Console</span>
              <span class="logo-title">智能教学平台</span>
            </div>
          </transition>
        </div>

        <div class="menu-scroll-area">
          <el-scrollbar>
            <el-menu
              :default-active="activeMenu"
              class="layout-menu"
              :collapse="collapsed"
              router
              :collapse-transition="false"
            >
              <el-menu-item index="/teacher/dashboard">
                <el-icon><HomeFilled /></el-icon>
                <template #title>首页总览</template>
              </el-menu-item>

              <el-sub-menu index="class-group">
                <template #title>
                  <el-icon><UserFilled /></el-icon>
                  <span>教学班管理</span>
                </template>
                <el-menu-item index="/teacher/class-list">教学班列表</el-menu-item>
                <el-menu-item index="/teacher/class-analysis">教学班分析</el-menu-item>
                <el-menu-item index="/teacher/class-profile">能力画像</el-menu-item>
              </el-sub-menu>

              <el-sub-menu index="teaching-group">
                <template #title>
                  <el-icon><Notebook /></el-icon>
                  <span>实验教学</span>
                </template>
                <el-menu-item index="/teacher/experiments">实验列表</el-menu-item>
                <el-menu-item index="/teacher/experiment-create">创建实验</el-menu-item>
                <el-menu-item index="/teacher/submissions">学生提交</el-menu-item>
                <el-menu-item index="/teacher/experiment-analytics">实验数据分析</el-menu-item>
                <el-menu-item index="/teacher/data-sync">PTA 数据同步</el-menu-item>
              </el-sub-menu>

              <el-sub-menu index="grading-group">
                <template #title>
                  <el-icon><DocumentChecked /></el-icon>
                  <span>AI 批改</span>
                </template>
                <el-menu-item index="/teacher/grading">批改中心</el-menu-item>
                <el-menu-item index="/teacher/grading/rubrics">评分标准</el-menu-item>
              </el-sub-menu>

              <el-sub-menu index="rag-group">
                <template #title>
                  <el-icon><Collection /></el-icon>
                  <span>课程知识库</span>
                </template>
                <el-menu-item index="/teacher/knowledge-base">空间与文档</el-menu-item>
                <el-menu-item index="/teacher/rag-analytics">RAG 分析</el-menu-item>
              </el-sub-menu>

              <el-sub-menu index="ai-group">
                <template #title>
                  <el-icon><ChatDotRound /></el-icon>
                  <span>AI 助教</span>
                </template>
                <el-menu-item index="/teacher/ai-chat">AI 对话</el-menu-item>
                <el-menu-item index="/teacher/ai-recommendation">教学建议</el-menu-item>
              </el-sub-menu>

              <el-sub-menu index="tools-group">
                <template #title>
                  <el-icon><Briefcase /></el-icon>
                  <span>教辅工具</span>
                </template>
                <el-menu-item index="/teacher/document-center">文档中心</el-menu-item>
                <el-menu-item index="/teacher/bilingual-read">双语阅读</el-menu-item>
                <el-menu-item index="/teacher/summary-card">AI 精读</el-menu-item>
                <el-menu-item index="/teacher/ai-organize">智能整理</el-menu-item>
              </el-sub-menu>

              <el-menu-item
                v-if="hasPermission(['view_course_classes', 'analyze_course_classes'])"
                index="/teacher/course-analysis"
              >
                <el-icon><DataAnalysis /></el-icon>
                <template #title>课程分析</template>
              </el-menu-item>

              <el-sub-menu v-if="hasPermission(['view_all_teachers'])" index="dept-group">
                <template #title>
                  <el-icon><OfficeBuilding /></el-icon>
                  <span>院系管理</span>
                </template>
                <el-menu-item index="/teacher/department-teachers">教师管理</el-menu-item>
                <el-menu-item v-if="hasPermission(['analyze_all_classes'])" index="/teacher/department-analytics">
                  院系统计
                </el-menu-item>
                <el-menu-item v-if="hasPermission(['manage_teacher_ai'])" index="/teacher/teacher-ai-management">
                  AI 管理
                </el-menu-item>
              </el-sub-menu>

              <div class="menu-divider"></div>

              <el-menu-item index="/teacher/profile">
                <el-icon><Setting /></el-icon>
                <template #title>个人设置</template>
              </el-menu-item>
            </el-menu>
          </el-scrollbar>
        </div>
      </el-aside>

      <el-container class="layout-main">
        <el-header class="layout-header">
          <div class="header-left">
            <el-icon class="fold-icon" @click="toggleSidebar">
              <Fold v-if="!collapsed" />
              <Expand v-else />
            </el-icon>

            <div class="header-path">
              <el-breadcrumb separator="/">
                <el-breadcrumb-item :to="{ path: '/teacher/dashboard' }">首页</el-breadcrumb-item>
                <el-breadcrumb-item v-for="(item, index) in breadcrumbs" :key="index">
                  {{ item }}
                </el-breadcrumb-item>
              </el-breadcrumb>
            </div>
          </div>

          <div class="header-right">
            <div v-if="selectedClassName" class="class-indicator" @click="switchClass">
              <el-icon><School /></el-icon>
              <span>{{ selectedClassName }}</span>
              <el-icon :size="12"><ArrowDown /></el-icon>
            </div>

            <span class="teacher-level-badge" :class="teacherLevelClass">{{ teacherLevelText }}</span>

            <el-tooltip content="全屏" placement="bottom">
              <el-icon class="header-icon" @click="toggleFullScreen"><FullScreen /></el-icon>
            </el-tooltip>

            <el-dropdown trigger="click" @command="handleCommand">
              <div class="user-info">
                <el-avatar :size="34" :src="userInfo.avatar">
                  <span>{{ (userInfo.name || '教').slice(0, 1) }}</span>
                </el-avatar>
                <div v-if="!collapsed" class="user-copy">
                  <span class="username">{{ userInfo.name || '教师用户' }}</span>
                  <span class="user-subtitle">课程教学工作台</span>
                </div>
                <el-icon class="arrow-icon"><ArrowDown /></el-icon>
              </div>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="switchClass">
                    <el-icon><School /></el-icon>
                    切换教学班
                  </el-dropdown-item>
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
          智能学情分析与个性化实验能力提升平台 · Teacher Workspace
        </el-footer>
      </el-container>
    </el-container>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import {
  ArrowDown,
  Briefcase,
  ChatDotRound,
  Collection,
  DataAnalysis,
  DocumentChecked,
  Expand,
  Fold,
  FullScreen,
  HomeFilled,
  Notebook,
  OfficeBuilding,
  School,
  Setting,
  SwitchButton,
  UserFilled
} from '@element-plus/icons-vue'
import { useUserStore } from '../../store'
import { clearAuthStorage } from '../../constants/auth'

const route = useRoute()
const router = useRouter()
const collapsed = ref(false)
const userStore = useUserStore()

const userInfo = computed(() => userStore.userInfo || { name: '教师用户', avatar: '' })
const selectedClassName = computed(() => userStore.selectedClass?.name || '')

const teacherLevel = computed(() => userInfo.value?.level || 'normal')
const teacherLevelText = computed(() => {
  const map = {
    department_head: '系主任',
    course_leader: '课程负责人'
  }
  return map[teacherLevel.value] || '教师'
})
const teacherLevelClass = computed(() => `level-${teacherLevel.value}`)

function hasPermission(permissions) {
  const userPermissions = userInfo.value?.permissions || []
  return permissions.some((permission) => userPermissions.includes(permission))
}

const activeMenu = computed(() => route.path)

const breadcrumbs = computed(() => {
  const pathMap = {
    dashboard: '首页总览',
    experiments: '实验列表',
    'experiment-detail': '实验详情',
    'experiment-create': '创建实验',
    submissions: '学生提交',
    'submission-detail': '提交详情',
    'class-list': '教学班列表',
    'class-analysis': '教学班分析',
    'class-profile': '能力画像',
    profile: '个人设置',
    'document-center': '文档中心',
    'bilingual-read': '双语阅读',
    'summary-card': 'AI 精读',
    'ai-chat': 'AI 对话',
    'ai-organize': '智能整理',
    grading: 'AI 批改',
    'knowledge-base': '课程知识库',
    'rag-analytics': 'RAG 分析',
    'course-analysis': '课程分析',
    'department-teachers': '教师管理',
    'department-analytics': '院系统计',
    'teacher-ai-management': 'AI 管理',
    'ai-recommendation': '教学建议',
    'experiment-analytics': '实验数据分析',
    'data-sync': 'PTA 数据同步'
  }
  const paths = route.path.split('/').filter(Boolean)
  return paths[0] === 'teacher' ? paths.slice(1).map((part) => pathMap[part] || part) : []
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

function switchClass() {
  userStore.setSelectedClass(null)
  router.push('/teacher/select-class')
}

function handleCommand(command) {
  if (command === 'profile') {
    router.push('/teacher/profile')
    return
  }
  if (command === 'switchClass') {
    switchClass()
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
    clearAuthStorage()
    router.push('/login')
  }).catch(() => {})
}

onMounted(() => {
  const canOpenWithoutClass = route.path === '/teacher/class-list' || route.path === '/teacher/profile'
  if (!userStore.selectedClass && !canOpenWithoutClass) {
    router.replace('/teacher/select-class')
    return
  }

  if (userInfo.value.role && userInfo.value.role !== 'teacher') {
    ElMessageBox.alert('当前账号没有教师权限，请重新登录。', '权限错误', {
      confirmButtonText: '确定',
      callback: () => {
        clearAuthStorage()
        router.push('/login')
      }
    })
  }
})
</script>

<style scoped>
.teacher-layout,
.layout-container {
  height: 100vh;
}

.layout-aside {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-right: 1px solid rgba(126, 157, 183, 0.14);
  background:
    linear-gradient(180deg, rgba(12, 31, 50, 0.94), rgba(16, 49, 77, 0.92)),
    radial-gradient(circle at top left, rgba(44, 181, 160, 0.16), transparent 32%);
  box-shadow: inset -1px 0 0 rgba(255, 255, 255, 0.04);
  transition: width 0.28s ease;
}

.logo-container {
  display: flex;
  align-items: center;
  gap: 14px;
  height: 74px;
  padding: 0 18px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.logo {
  width: 42px;
  height: 42px;
  border-radius: 14px;
  border: 1px solid rgba(130, 220, 255, 0.18);
  box-shadow: 0 10px 24px rgba(10, 30, 50, 0.28);
}

.brand-copy {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.logo-kicker {
  color: rgba(173, 222, 255, 0.7);
  font-size: 11px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.logo-title {
  color: #f3f8fc;
  font-size: 16px;
  font-weight: 700;
}

.menu-scroll-area {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  padding: 10px 8px 16px;
}

.menu-scroll-area :deep(.el-scrollbar) {
  height: 100%;
}

.menu-scroll-area :deep(.el-scrollbar__wrap) {
  overflow-x: hidden;
}

.layout-menu {
  border-right: none;
  background: transparent !important;
  --el-menu-bg-color: transparent;
  --el-menu-text-color: rgba(222, 236, 248, 0.7);
  --el-menu-active-color: #ffffff;
  --el-menu-hover-bg-color: rgba(255, 255, 255, 0.08);
  --el-menu-hover-text-color: #ffffff;
  padding: 0;
}

.layout-menu :deep(.el-menu-item),
.layout-menu :deep(.el-sub-menu__title) {
  height: 46px;
  line-height: 46px;
  margin: 4px 0;
  border-radius: 16px;
  font-size: 13px;
  transition: all 0.22s ease;
}

.layout-menu :deep(.el-menu-item.is-active) {
  background: linear-gradient(135deg, rgba(20, 114, 219, 0.92), rgba(43, 181, 160, 0.88)) !important;
  box-shadow: 0 14px 26px rgba(18, 112, 216, 0.2);
}

.layout-menu :deep(.el-menu-item:hover),
.layout-menu :deep(.el-sub-menu__title:hover) {
  background: rgba(255, 255, 255, 0.08) !important;
}

.layout-menu :deep(.el-sub-menu .el-menu) {
  background: transparent !important;
}

.layout-menu :deep(.el-sub-menu .el-menu .el-menu-item) {
  margin-left: 12px;
  padding-left: 42px !important;
  height: 40px;
  line-height: 40px;
  border-radius: 14px;
  font-size: 12px;
}

.layout-menu :deep(.el-icon) {
  font-size: 18px;
}

.menu-divider {
  height: 1px;
  margin: 10px 12px;
  background: rgba(255, 255, 255, 0.08);
}

.layout-main {
  background: transparent;
}

.layout-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  height: 78px;
  padding: 0 28px;
  border-bottom: 1px solid rgba(126, 157, 183, 0.14);
  background: rgba(248, 251, 253, 0.72);
  backdrop-filter: blur(14px);
}

.header-left,
.header-right {
  display: flex;
  align-items: center;
  gap: 14px;
}

.fold-icon,
.header-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 14px;
  color: #5d7288;
  cursor: pointer;
  transition: all 0.2s ease;
}

.fold-icon:hover,
.header-icon:hover {
  background: rgba(18, 112, 216, 0.08);
  color: #1270d8;
}

.header-path {
  padding: 10px 14px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.64);
  border: 1px solid rgba(126, 157, 183, 0.14);
}

.class-indicator {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 40px;
  padding: 0 14px;
  border-radius: 999px;
  background: linear-gradient(135deg, rgba(18, 112, 216, 0.12), rgba(44, 181, 160, 0.1));
  color: #1270d8;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}

.teacher-level-badge {
  min-height: 34px;
  padding: 0 12px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  font-size: 12px;
  font-weight: 700;
}

.level-normal {
  background: rgba(111, 134, 156, 0.12);
  color: #5d7288;
}

.level-course_leader {
  background: rgba(18, 112, 216, 0.12);
  color: #1270d8;
}

.level-department_head {
  background: rgba(29, 143, 106, 0.12);
  color: #1d8f6a;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 10px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.64);
  border: 1px solid rgba(126, 157, 183, 0.14);
  cursor: pointer;
}

.user-copy {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.username {
  color: #16324a;
  font-size: 13px;
  font-weight: 700;
}

.user-subtitle {
  color: #7f92a6;
  font-size: 11px;
}

.arrow-icon {
  color: #8ca0b3;
  font-size: 12px;
}

.layout-content {
  padding: 24px 28px 28px;
  min-height: calc(100vh - 138px);
  overflow-y: auto;
}

.layout-footer {
  padding: 12px 16px 18px;
  text-align: center;
  color: #8ca0b3;
  font-size: 12px;
  background: transparent;
}

.page-slide-enter-active,
.page-slide-leave-active {
  transition: all 0.24s ease;
}

.page-slide-enter-from {
  opacity: 0;
  transform: translateY(8px);
}

.page-slide-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

.fade-text-enter-active,
.fade-text-leave-active {
  transition: opacity 0.2s ease;
}

.fade-text-enter-from,
.fade-text-leave-to {
  opacity: 0;
}

@media (max-width: 960px) {
  .layout-header {
    height: auto;
    padding: 16px;
    flex-direction: column;
    align-items: flex-start;
  }

  .header-right {
    width: 100%;
    flex-wrap: wrap;
  }

  .layout-content {
    padding: 16px;
  }
}
</style>
