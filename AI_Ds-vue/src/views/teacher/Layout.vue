<template>
  <div class="teacher-layout">
    <el-container class="layout-container">
      <el-aside :width="collapsed ? '64px' : '220px'" class="layout-aside">
        <div class="logo-container">
          <img src="../../assets/logo.png" alt="Logo" class="logo" />
          <transition name="fade-text">
            <span v-if="!collapsed" class="logo-title">智能教辅平台</span>
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
              <!-- 1. 首页总览 -->
              <el-menu-item index="/teacher/dashboard">
                <el-icon><HomeFilled /></el-icon>
                <template #title>首页总览</template>
              </el-menu-item>

              <!-- 2. 班级管理 (班级列表 + 班级分析 + 能力画像) -->
              <el-sub-menu index="class-group">
                <template #title>
                  <el-icon><UserFilled /></el-icon>
                  <span>班级管理</span>
                </template>
                <el-menu-item index="/teacher/class-list">班级列表</el-menu-item>
                <el-menu-item index="/teacher/class-analysis">班级分析</el-menu-item>
                <el-menu-item index="/teacher/class-profile">能力画像</el-menu-item>
              </el-sub-menu>

              <!-- 3. 实验教学 (实验列表 + 创建实验 + 学生提交) -->
              <el-sub-menu index="teaching-group">
                <template #title>
                  <el-icon><Notebook /></el-icon>
                  <span>实验教学</span>
                </template>
                <el-menu-item index="/teacher/experiments">实验列表</el-menu-item>
                <el-menu-item index="/teacher/experiment-create">创建实验</el-menu-item>
                <el-menu-item index="/teacher/submissions">学生提交</el-menu-item>
                <el-menu-item index="/teacher/experiment-analytics">数据分析</el-menu-item>
                <el-menu-item index="/teacher/data-sync">数据同步</el-menu-item>
              </el-sub-menu>

              <!-- 4. AI 批改 (批改中心 + 评分标准) -->
              <el-sub-menu index="grading-group">
                <template #title>
                  <el-icon><DocumentChecked /></el-icon>
                  <span>AI 批改</span>
                </template>
                <el-menu-item index="/teacher/grading">批改中心</el-menu-item>
                <el-menu-item index="/teacher/grading/rubrics">评分标准</el-menu-item>
              </el-sub-menu>

              <!-- 5. 课程知识库 (知识库管理 + RAG分析) -->
              <el-sub-menu index="rag-group">
                <template #title>
                  <el-icon><Collection /></el-icon>
                  <span>课程知识库</span>
                </template>
                <el-menu-item index="/teacher/knowledge-base">知识库管理</el-menu-item>
                <el-menu-item index="/teacher/rag-analytics">RAG 分析</el-menu-item>
              </el-sub-menu>

              <!-- 6. AI 助手 (AI对话 + AI建议) -->
              <el-sub-menu index="ai-group">
                <template #title>
                  <el-icon><ChatDotRound /></el-icon>
                  <span>AI 助手</span>
                </template>
                <el-menu-item index="/teacher/ai-chat">AI 对话</el-menu-item>
                <el-menu-item index="/teacher/ai-recommendation">教学建议</el-menu-item>
              </el-sub-menu>

              <!-- 7. 教辅工具 (文档中心 + 双语阅读 + AI精读 + 智能整理) -->
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

              <!-- 8. 课程分析 (课程负责人可见) -->
              <el-menu-item
                v-if="hasPermission(['view_course_classes', 'analyze_course_classes'])"
                index="/teacher/course-analysis"
              >
                <el-icon><DataAnalysis /></el-icon>
                <template #title>课程分析</template>
              </el-menu-item>

              <!-- 9. 系部管理 (系主任可见) -->
              <el-sub-menu v-if="hasPermission(['view_all_teachers'])" index="dept-group">
                <template #title>
                  <el-icon><OfficeBuilding /></el-icon>
                  <span>系部管理</span>
                </template>
                <el-menu-item index="/teacher/department-teachers">教师管理</el-menu-item>
                <el-menu-item v-if="hasPermission(['analyze_all_classes'])" index="/teacher/department-analytics">系级分析</el-menu-item>
                <el-menu-item v-if="hasPermission(['manage_teacher_ai'])" index="/teacher/teacher-ai-management">AI 管理</el-menu-item>
              </el-sub-menu>

              <!-- 分隔线 -->
              <div class="menu-divider"></div>

              <!-- 10. 个人设置 -->
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
            <el-breadcrumb separator="/">
              <el-breadcrumb-item :to="{ path: '/teacher/dashboard' }">首页</el-breadcrumb-item>
              <el-breadcrumb-item v-for="(item, index) in breadcrumbs" :key="index">
                {{ item }}
              </el-breadcrumb-item>
            </el-breadcrumb>
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
                <el-avatar :size="32" :src="userInfo.avatar">
                  <span>{{ (userInfo.name || '教')[0] }}</span>
                </el-avatar>
                <span v-if="!collapsed" class="username">{{ userInfo.name }}</span>
                <el-icon class="arrow-icon"><ArrowDown /></el-icon>
              </div>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="switchClass">
                    <el-icon><School /></el-icon> 切换班级
                  </el-dropdown-item>
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
          智能学情分析与个性化实验能力提升平台 v2.1 © 2025
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
import { clearAuthStorage } from '../../constants/auth'
import {
  HomeFilled, DocumentChecked, UserFilled, Setting,
  Fold, Expand, FullScreen, DataAnalysis, Notebook, Collection,
  ChatDotRound, Briefcase, OfficeBuilding,
  ArrowDown, SwitchButton, School
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const collapsed = ref(false)
const userStore = useUserStore()

const userInfo = computed(() => userStore.userInfo || { name: '教师用户', avatar: '' })
const selectedClassName = computed(() => userStore.selectedClass?.name || '')

const teacherLevel = computed(() => userInfo.value?.level || 'normal')
const teacherLevelText = computed(() => {
  const map = { department_head: '系主任', course_leader: '课程负责人' }
  return map[teacherLevel.value] || '普通教师'
})
const teacherLevelClass = computed(() => `level-${teacherLevel.value}`)

const hasPermission = (permissions) => {
  const userPermissions = userInfo.value?.permissions || []
  return permissions.some(p => userPermissions.includes(p))
}

const activeMenu = computed(() => route.path)

const breadcrumbs = computed(() => {
  const pathMap = {
    dashboard: '首页', experiments: '实验列表', 'experiment-detail': '实验详情',
    'experiment-create': '创建实验', submissions: '学生提交', 'submission-detail': '提交详情',
    'class-list': '班级列表', 'class-analysis': '班级分析', 'class-profile': '能力画像',
    profile: '个人设置', 'document-center': '文档中心', 'bilingual-read': '双语阅读',
    'summary-card': 'AI 精读', 'ai-chat': 'AI 对话', 'ai-organize': '智能整理',
    grading: 'AI 批改', 'knowledge-base': '知识库', 'rag-analytics': 'RAG 分析',
    'course-analysis': '课程分析', 'department-teachers': '教师管理',
    'department-analytics': '系级分析', 'teacher-ai-management': 'AI 管理',
    'ai-recommendation': '教学建议',
    'experiment-analytics': '数据分析',
    'data-sync': '数据同步'
  }
  const paths = route.path.split('/').filter(Boolean)
  return paths[0] === 'teacher' ? paths.slice(1).map(p => pathMap[p.split('/')[0]] || p) : []
})

const toggleSidebar = () => { collapsed.value = !collapsed.value }

const toggleFullScreen = () => {
  if (!document.fullscreenElement) document.documentElement.requestFullscreen()
  else document.exitFullscreen?.()
}

const switchClass = () => {
  userStore.setSelectedClass(null)
  router.push('/teacher/select-class')
}

const handleCommand = (cmd) => {
  if (cmd === 'profile') router.push('/teacher/profile')
  else if (cmd === 'switchClass') switchClass()
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

onMounted(() => {
  if (!userStore.selectedClass) {
    router.replace('/teacher/select-class')
    return
  }
  if (userInfo.value.role && userInfo.value.role !== 'teacher') {
    ElMessageBox.alert('您没有教师权限，请重新登录', '权限错误', {
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
.teacher-layout { height: 100vh; width: 100%; }
.layout-container { height: 100%; }

/* ===== Sidebar ===== */
.layout-aside {
  background: linear-gradient(180deg, #1a1a2e 0%, #202134 100%);
  transition: width 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
  border-right: 1px solid rgba(255,255,255,0.06);
  display: flex;
  flex-direction: column;
}

.logo-container {
  height: 56px;
  display: flex;
  align-items: center;
  padding: 0 14px;
  gap: 10px;
  border-bottom: 1px solid rgba(255,255,255,0.08);
  flex-shrink: 0;
}

.logo {
  border-radius: 10px;
  border: 2px solid rgba(26,115,232,0.5);
  height: 36px;
  width: 36px;
  flex-shrink: 0;
}

.logo-title {
  font-size: 14px;
  font-weight: 700;
  color: rgba(255,255,255,0.9);
  white-space: nowrap;
  letter-spacing: 0.3px;
}

/* 滚动区域 — 关键：让菜单可滚动 */
.menu-scroll-area {
  flex: 1;
  overflow: hidden;
  min-height: 0; /* flex子项需要这个才能正确收缩 */
}

.menu-scroll-area :deep(.el-scrollbar) {
  height: 100%;
}

.menu-scroll-area :deep(.el-scrollbar__wrap) {
  overflow-x: hidden;
}

.menu-scroll-area :deep(.el-scrollbar__bar.is-vertical) {
  width: 4px;
  right: 2px;
}

.menu-scroll-area :deep(.el-scrollbar__thumb) {
  background: rgba(255,255,255,0.15);
  border-radius: 4px;
}

.menu-scroll-area :deep(.el-scrollbar__thumb:hover) {
  background: rgba(255,255,255,0.3);
}

.layout-menu {
  border-right: none;
  background: transparent !important;
  --el-menu-bg-color: transparent;
  --el-menu-text-color: #9aa0a6;
  --el-menu-active-color: #8ab4f8;
  --el-menu-hover-bg-color: rgba(26,115,232,0.1);
  --el-menu-hover-text-color: #d2e3fc;
  padding: 6px 8px;
}

.layout-menu :deep(.el-menu-item),
.layout-menu :deep(.el-sub-menu__title) {
  border-radius: 8px;
  margin: 2px 0;
  height: 42px;
  line-height: 42px;
  font-size: 13px;
  transition: all 0.2s;
}

.layout-menu :deep(.el-menu-item.is-active) {
  background: linear-gradient(135deg, rgba(26,115,232,0.2), rgba(66,133,244,0.15)) !important;
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
  height: 18px;
  background: #8ab4f8;
  border-radius: 0 3px 3px 0;
}

.layout-menu :deep(.el-sub-menu .el-menu) {
  background: transparent !important;
}

.layout-menu :deep(.el-sub-menu .el-menu .el-menu-item) {
  padding-left: 50px !important;
  height: 38px;
  line-height: 38px;
  font-size: 12.5px;
}

.layout-menu :deep(.el-menu-item:hover),
.layout-menu :deep(.el-sub-menu__title:hover) {
  background: rgba(26,115,232,0.1) !important;
}

.layout-menu :deep(.el-icon) {
  font-size: 17px;
}

.menu-divider {
  height: 1px;
  background: rgba(255,255,255,0.06);
  margin: 6px 10px;
}

/* ===== Header ===== */
.layout-main { background: #f8f9fa; }

.layout-header {
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-shadow: 0 1px 2px rgba(60,64,67,0.1);
  padding: 0 20px;
  height: 52px;
  border-bottom: 1px solid #dadce0;
}

.header-left { display: flex; align-items: center; gap: 12px; }

.fold-icon {
  cursor: pointer;
  font-size: 18px;
  color: #5f6368;
  padding: 6px;
  border-radius: 8px;
  transition: all 0.2s;
}
.fold-icon:hover { background: #f1f3f4; color: #202124; }

.header-right { display: flex; align-items: center; gap: 10px; }

.class-indicator {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 5px 12px;
  border-radius: 100px;
  background: #e8f0fe;
  color: #1a73e8;
  font-size: 12.5px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}
.class-indicator:hover { background: #d2e3fc; }

.header-icon {
  cursor: pointer;
  font-size: 17px;
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
  padding: 4px 6px;
  border-radius: 8px;
  transition: background 0.2s;
  gap: 6px;
}
.user-info:hover { background: #f1f3f4; }

.username { font-size: 13px; color: #202124; font-weight: 500; }
.arrow-icon { font-size: 12px; color: #9aa0a6; }

.teacher-level-badge {
  padding: 3px 8px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 500;
}
.level-normal { background: #f1f3f4; color: #5f6368; }
.level-course_leader { background: #dbeafe; color: #2563eb; }
.level-department_head { background: #dcfce7; color: #16a34a; }

/* ===== Content ===== */
.layout-content {
  background: #f8f9fa;
  padding: 20px;
  min-height: calc(100vh - 116px);
  overflow-y: auto;
}

.layout-footer {
  text-align: center;
  color: #9aa0a6;
  padding: 10px;
  font-size: 12px;
  background: #f8f9fa;
  border-top: 1px solid #dadce0;
}

/* ===== Transitions ===== */
.page-slide-enter-active,
.page-slide-leave-active {
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}
.page-slide-enter-from { opacity: 0; transform: translateY(6px); }
.page-slide-leave-to { opacity: 0; transform: translateY(-4px); }

.fade-text-enter-active,
.fade-text-leave-active { transition: opacity 0.2s; }
.fade-text-enter-from,
.fade-text-leave-to { opacity: 0; }
/* layout-v2 */
</style>
