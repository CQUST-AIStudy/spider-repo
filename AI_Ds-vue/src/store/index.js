import { defineStore } from 'pinia'
import api from '../api'
import { clearAuthStorage, setSessionToken, setUserInfo } from '../constants/auth'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: null,
    userInfo: null,
    loading: false,
    selectedClass: null,
  }),
  persist: {
    key: 'user',
    storage: localStorage,
    paths: ['token', 'userInfo', 'selectedClass']
  },
  actions: {
    async login(username, password, teacherLevel) {
      this.loading = true
      try {
        const res = await api.login(username, password, teacherLevel)
        if (!(res && res.success)) {
          return { success: false, message: res?.message || '用户名或密码错误', details: res }
        }

        const userInfo = res.user || res.userInfo
        if (!userInfo) {
          return { success: false, message: '登录成功但未获取到用户信息' }
        }

        this.userInfo = userInfo
        this.token = res.token || 'legacy_session'

        setSessionToken(this.token)
        setUserInfo(this.userInfo)

        try {
          const { restoreTapSession } = await import('../api/tap')
          await restoreTapSession()
        } catch (error) {
          console.warn('TAP session 换票失败:', error.message)
        }

        return { success: true, message: res.message || '登录成功', user: userInfo }
      } catch (error) {
        console.error('登录过程中发生错误:', error)
        return { success: false, message: '登录过程中发生错误: ' + (error.message || '未知错误') }
      } finally {
        this.loading = false
      }
    },

    logout() {
      this.token = null
      this.userInfo = null
      this.selectedClass = null
      clearAuthStorage()
    },

    updateUserInfo(patch = {}) {
      this.userInfo = {
        ...(this.userInfo || {}),
        ...patch
      }
      setUserInfo(this.userInfo)
    },

    setSelectedClass(cls) {
      this.selectedClass = cls
    }
  },
  getters: {
    isLoggedIn: (state) => !!state.token,
    username: (state) => state.userInfo?.name || state.userInfo?.username || '未登录',
    currentClassName: (state) => state.selectedClass?.name || ''
  }
})

export const useExperimentStore = defineStore('experiment', {
  state: () => ({
    experimentList: [],
    currentExperiment: null,
    loading: false,
    generatingReport: false,
    experimentCache: new Map()
  }),
  actions: {
    async fetchExperimentList() {
      this.loading = true
      try {
        const response = await api.getExperimentList()
        if (response?.data && Array.isArray(response.data)) {
          this.experimentList = response.data
        } else if (Array.isArray(response)) {
          this.experimentList = response
        } else {
          this.experimentList = []
        }
      } catch (error) {
        console.error('获取实验列表失败:', error)
        this.experimentList = []
      } finally {
        this.loading = false
      }
    },

    async fetchExperimentDetail(id) {
      if (this.experimentCache.has(id)) {
        this.currentExperiment = this.experimentCache.get(id)
        return this.currentExperiment
      }

      this.loading = true
      try {
        const response = await api.getExperimentDetails(id)
        this.currentExperiment = response?.data || response || null
        if (this.currentExperiment) {
          this.experimentCache.set(id, this.currentExperiment)
        }
        return this.currentExperiment
      } catch (error) {
        console.error(`获取实验 ${id} 详情失败:`, error)
        return null
      } finally {
        this.loading = false
      }
    },

    getExperimentDetailsById(id) {
      return this.experimentCache.get(id)
    },

    async generateAIReport(_experimentId, userData) {
      this.generatingReport = true
      try {
        await new Promise(resolve => setTimeout(resolve, 1200))
        const report = `# ${userData.experimentName || '实验报告'}\n\n`
          + `## 学生信息\n- 姓名：${userData.studentName || ''}\n- 学号：${userData.studentId || ''}\n- 班级：${userData.className || ''}\n\n`
          + `## 实验内容\n${userData.content || '待补充'}\n\n`
          + `## AI点评\n${userData.aiComment || '暂无'}\n\n`
          + `## 实验心得体会\n${userData.experience || '待补充'}\n`
        return { success: true, report }
      } catch {
        return { success: false, message: 'AI 报告生成失败' }
      } finally {
        this.generatingReport = false
      }
    },

    generateLinearListReport(experimentName, userData) {
      return `# ${experimentName || '线性表实验'} - 实验报告\n\n`
        + `## 1. 学生信息\n- 姓名：${userData.studentName || ''}\n- 学号：${userData.studentId || ''}\n- 班级：${userData.className || ''}\n\n`
        + `## 2. 实验代码\n${userData.code ? `\`\`\`c\n${userData.code}\n\`\`\`` : '实验代码见实验平台'}\n\n`
        + `## 3. AI点评\n${userData.aiComment || '暂无'}\n\n`
        + `## 4. 实验心得体会\n${userData.experience || '待补充'}\n`
    },
  },
  getters: {
    completedExperiments: (state) => {
      const expList = Array.isArray(state.experimentList) ? state.experimentList : []
      return expList.filter(exp => exp.status === 'completed')
    },
    inProgressExperiments: (state) => {
      const expList = Array.isArray(state.experimentList) ? state.experimentList : []
      return expList.filter(exp => exp.status === 'in_progress')
    },
    notStartedExperiments: (state) => {
      const expList = Array.isArray(state.experimentList) ? state.experimentList : []
      return expList.filter(exp => exp.status === 'not_started')
    }
  }
})

export const useLearningStore = defineStore('learning', {
  state: () => ({
    analysisData: null,
    recommendedPractices: [],
    loading: false
  }),
  actions: {
    async fetchLearningAnalysis() {
      this.loading = true
      try {
        this.analysisData = await api.getLearningAnalysis()
      } finally {
        this.loading = false
      }
    },

    async fetchRecommendedPractices() {
      this.loading = true
      try {
        this.recommendedPractices = await api.getRecommendedPractices()
      } finally {
        this.loading = false
      }
    },

    async submitSelfAssessment(data) {
      return api.submitSelfAssessment(data)
    }
  }
})
