import { createRouter, createWebHistory } from 'vue-router'
import { getSessionToken, getUserInfo } from '../constants/auth'

const routes = [
  {
    path: '/',
    redirect: '/login'
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue')
  },
  {
    path: '/student',
    name: 'StudentLayout',
    component: () => import('../views/student/Layout.vue'),
    redirect: '/student/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'StudentDashboard',
        component: () => import('../views/student/Dashboard.vue')
      },
      {
        path: 'experiments',
        name: 'ExperimentList',
        component: () => import('../views/student/ExperimentList.vue')
      },
      {
        path: 'experiment-detail/:id',
        name: 'ExperimentDetail',
        component: () => import('../views/student/ExperimentDetail.vue')
      },
      {
        path: 'learning-analysis',
        name: 'LearningAnalysis',
        component: () => import('../views/student/LearningAnalysis.vue')
      },
      {
        path: 'ai-assistant',
        name: 'AIAssistant',
        component: () => import('../views/student/AIAssistant.vue')
      },
      {
        path: 'ai-report',
        name: 'AIReport',
        component: () => import('../views/student/AIReport.vue')
      },
      {
        path: 'practice',
        name: 'Practice',
        component: () => import('../views/student/Practice.vue')
      },
      {
        path: 'leetcode-practice/:id',
        name: 'LeetCodePractice',
        component: () => import('../views/student/LeetCodePractice.vue')
      },
      {
        path: 'leetcode-demo',
        name: 'LeetCodeDemo',
        component: () => import('../views/student/LeetCodeDemo.vue')
      },
      {
        path: 'ability-profile',
        name: 'AbilityProfile',
        component: () => import('../views/student/AbilityProfile.vue')
      },
      {
        path: 'profile',
        name: 'StudentProfile',
        component: () => import('../views/student/Profile.vue')
      }
    ]
  },
  {
    path: '/teacher/select-class',
    name: 'ClassSelector',
    component: () => import('../views/teacher/ClassSelector.vue')
  },
  {
    path: '/teacher',
    name: 'TeacherLayout',
    component: () => import('../views/teacher/Layout.vue'),
    redirect: '/teacher/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'TeacherDashboard',
        component: () => import('../views/teacher/Dashboard.vue')
      },
      {
        path: 'experiments',
        name: 'TeacherExperimentList',
        component: () => import('../views/teacher/ExperimentList.vue')
      },
      {
        path: 'experiment-detail/:id',
        name: 'TeacherExperimentDetail',
        component: () => import('../views/teacher/ExperimentDetail.vue')
      },
      {
        path: 'experiment-create',
        name: 'ExperimentCreate',
        component: () => import('../views/teacher/ExperimentCreate.vue')
      },
      {
        path: 'class-list',
        name: 'ClassList',
        component: () => import('../views/teacher/ClassList.vue')
      },
      {
        path: 'class-analysis/:id?',
        name: 'ClassAnalysis',
        component: () => import('../views/teacher/ClassDetailedAnalysis.vue')
      },
      {
        path: 'class-detailed-analysis/:classId?',
        name: 'ClassDetailedAnalysis',
        component: () => import('../views/teacher/ClassDetailedAnalysis.vue')
      },
      {
        path: 'submissions/:experimentId?',
        name: 'SubmissionList',
        component: () => import('../views/teacher/SubmissionList.vue')
      },
      {
        path: 'submission-detail/:id',
        name: 'SubmissionDetail',
        component: () => import('../views/teacher/SubmissionDetail.vue')
      },
      {
        path: 'profile',
        name: 'TeacherProfile',
        component: () => import('../views/teacher/Profile.vue')
      },
      // 基础AI功能 - 所有教师可用
      {
        path: 'ai-recommendation',
        name: 'AIRecommendation',
        component: () => import('../views/teacher/AIRecommendation.vue')
      },
      {
        path: 'generate-ppt',
        name: 'GeneratePPT',
        component: () => import('../views/teacher/GeneratePPT.vue')
      },
      // 课程负责人路由
      {
        path: 'course-analysis',
        name: 'CourseAnalysis',
        component: () => import('../views/teacher/CourseAnalysis.vue'),
        meta: { requiredPermissions: ['view_course_classes', 'analyze_course_classes'] }
      },
      // 系主任路由
      {
        path: 'department-teachers',
        name: 'DepartmentTeachers',
        component: () => import('../views/teacher/DepartmentTeachers.vue'),
        meta: { requiredPermissions: ['view_all_teachers'] }
      },
      {
        path: 'department-analytics',
        name: 'DepartmentAnalytics',
        component: () => import('../views/teacher/DepartmentAnalytics.vue'),
        meta: { requiredPermissions: ['analyze_all_classes'] }
      },
      // 系主任特殊AI管理功能
      {
        path: 'teacher-ai-management',
        name: 'TeacherAIManagement',
        component: () => import('../views/teacher/TeacherAIManagement.vue'),
        meta: { requiredPermissions: ['manage_teacher_ai'] }
      },
      // 班级能力画像
      {
        path: 'class-profile',
        name: 'ClassProfile',
        component: () => import('../views/teacher/ClassProfile.vue')
      },
      // 教辅工具 (tap-backend)
      {
        path: 'document-center',
        name: 'DocumentCenter',
        component: () => import('../views/teacher/DocumentCenter.vue')
      },
      {
        path: 'bilingual-read',
        name: 'BilingualRead',
        component: () => import('../views/teacher/BilingualRead.vue')
      },
      {
        path: 'summary-card',
        name: 'SummaryCard',
        component: () => import('../views/teacher/SummaryCard.vue')
      },
      {
        path: 'ai-chat',
        name: 'TeacherAIChat',
        component: () => import('../views/teacher/AIChat.vue')
      },
      {
        path: 'ai-organize',
        name: 'AIOrganize',
        component: () => import('../views/teacher/AIOrganize.vue')
      },
      // AI批改模块
      {
        path: 'grading',
        name: 'GradingCenter',
        component: () => import('../views/teacher/GradingCenter.vue')
      },
      {
        path: 'grading/detail/:id',
        name: 'GradingDetail',
        component: () => import('../views/teacher/GradingDetail.vue')
      },
      {
        path: 'grading/submission/:id',
        name: 'SubmissionReview',
        component: () => import('../views/teacher/SubmissionReview.vue')
      },
      {
        path: 'grading/rubrics',
        name: 'RubricEditor',
        component: () => import('../views/teacher/RubricEditor.vue')
      },
      // 课程知识库（RAG）
      {
        path: 'knowledge-base',
        name: 'KnowledgeBase',
        component: () => import('../views/teacher/KnowledgeBase.vue')
      },
      {
        path: 'rag-analytics',
        name: 'RagAnalytics',
        component: () => import('../views/teacher/RagAnalytics.vue')
      },
      // 实验数据分析
      {
        path: 'experiment-analytics',
        name: 'ExperimentAnalytics',
        component: () => import('../views/teacher/ExperimentAnalytics.vue')
      },
      // PTA 数据同步
      {
        path: 'data-sync',
        name: 'DataSync',
        component: () => import('../views/teacher/DataSyncPanel.vue')
      }
    ]
  },
  {
    path: '/admin',
    name: 'AdminLayout',
    component: () => import('../views/admin/Layout.vue'),
    redirect: '/admin/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'AdminDashboard',
        component: () => import('../views/admin/Dashboard.vue')
      },
      {
        path: 'user-management',
        name: 'UserManagement',
        component: () => import('../views/admin/UserManagement.vue')
      },
      {
        path: 'class-management',
        name: 'ClassManagement',
        component: () => import('../views/admin/ClassManagement.vue')
      },
      {
        path: 'experiment-management',
        name: 'ExperimentManagement',
        component: () => import('../views/admin/ExperimentManagement.vue')
      },
      {
        path: 'system-log',
        name: 'SystemLog',
        component: () => import('../views/admin/SystemLog.vue')
      },
      {
        path: 'profile',
        name: 'AdminProfile',
        component: () => import('../views/admin/Profile.vue')
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 添加路由变化日志
router.afterEach((to) => {
  console.log('路由变化:', '->', to.path)
})

// 全局前置守卫
router.beforeEach((to, from, next) => {
  console.log('路由守卫检查:', to.path)
  const isLoginPage = to.path === '/login'
  const isClassSelector = to.path === '/teacher/select-class'
  const token = getSessionToken()

  if (!isLoginPage && !isClassSelector && !token) {
    console.log('未登录，重定向到登录页')
    next('/login')
  } else if (to.path.startsWith('/teacher/') && !isClassSelector) {
    // 教师端页面需要先选择班级
    const userStr = localStorage.getItem('user')
    let selectedClass = null
    try {
      const parsed = userStr ? JSON.parse(userStr) : null
      selectedClass = parsed?.selectedClass
    } catch (e) { /* ignore */ }
    if (!selectedClass) {
      console.log('未选择班级，重定向到班级选择页')
      next('/teacher/select-class')
    } else if (to.meta.requiredPermissions) {
      const userInfo = getUserInfo()
      const userPermissions = userInfo?.permissions || []
      const hasPermission = to.meta.requiredPermissions.some(p => userPermissions.includes(p))
      if (hasPermission) next()
      else next('/teacher/dashboard')
    } else {
      next()
    }
  } else if (to.meta.requiredPermissions) {
    const userInfo = getUserInfo()
    const userPermissions = userInfo?.permissions || []
    const hasPermission = to.meta.requiredPermissions.some(p => userPermissions.includes(p))
    if (hasPermission) next()
    else next('/teacher/dashboard')
  } else {
    next()
  }
})

export default router
