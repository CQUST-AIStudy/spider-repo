import { defineStore } from 'pinia'
import api from '../api'
import { clearAuthStorage, setSessionToken, setUserInfo } from '../constants/auth'

// 用户状态
export const useUserStore = defineStore('user', {
  state: () => ({
    token: null,
    userInfo: null,
    loading: false,
    selectedClass: null  // { id, name } — 当前选中的班级
  }),
  persist: {
    key: 'user',
    storage: localStorage,
    paths: ['token', 'userInfo', 'selectedClass']
  },
  actions: {
    async login(username, password, teacherLevel) {
      this.loading = true
      console.log('用户登录请求 - UserStore:', { username, teacherLevel })
      
      try {
        // 调用API登录
        const res = await api.login(username, password, teacherLevel)
        console.log('登录API返回 (Store):', res)
        
        if (res && res.success) {
          const userInfo = res.user || res.userInfo;
          
          if (userInfo) {
            this.userInfo = userInfo;
            this.token = res.token || ('session_token_' + new Date().getTime());
            
            // 同步写 localStorage（路由守卫兼容）
            setSessionToken(this.token)
            setUserInfo(this.userInfo)
            
            // 同步获取 TAP JWT token（教辅模块需要）
            try {
              const { tapLogin } = await import('../api/tap')
              await tapLogin(username, password)
            } catch (e) {
              console.warn('TAP JWT 登录跳过:', e.message)
            }
            
            return { success: true, message: res.message || '登录成功', user: userInfo };
          } else {
            return { success: false, message: '登录成功但未获取到用户信息' };
          }
        } else if (res) {
          return { success: false, message: res.message || '用户名或密码错误', details: res };
        } else {
          return { success: false, message: '登录请求无响应' };
        }
      } catch (error) {
        console.error('登录过程发生错误:', error)
        return { success: false, message: '登录过程发生错误: ' + (error.message || '未知错误') }
      } finally {
        this.loading = false
      }
    },
    
    logout() {
      this.token = null
      this.userInfo = null
      this.selectedClass = null
      
      try {
        clearAuthStorage()
      } catch (e) {
        console.error('清除登录信息失败:', e)
      }
    },
    
    setSelectedClass(cls) {
      this.selectedClass = cls
    }
  },
  getters: {
    isLoggedIn: (state) => !!state.token,
    username: (state) => state.userInfo?.name || '未登录',
    currentClassName: (state) => state.selectedClass?.name || ''
  }
})

// 实验状态
export const useExperimentStore = defineStore('experiment', {
  state: () => ({
    experimentList: [],
    currentExperiment: null,
    loading: false,
    generatingReport: false,

    experimentCache: new Map() // 添加缓存来存储实验详情，避免重复请求
  }),
  actions: {
    async fetchExperimentList() {
      this.loading = true
      try {
        const response = await api.getExperimentList()
        console.log('从后端获取的原始实验列表：', response)
        
        // 统一数据结构处理：确保experimentList始终是数组
        if (response.data && Array.isArray(response.data)) {
          this.experimentList = response.data
        } else if (Array.isArray(response)) {
          this.experimentList = response
        } else {
          console.warn('实验列表响应格式异常:', response)
          this.experimentList = [] // 设置为空数组以避免错误
        }
        
        console.log('处理后的实验列表：', this.experimentList)
      } catch (error) {
        console.error('获取实验列表失败:', error)
        this.experimentList = [] // 错误时设置为空数组
      } finally {
        this.loading = false
      }
    },
    
    async fetchExperimentDetail(id) {
      // 如果缓存中已有该实验详情，则直接返回
      if (this.experimentCache.has(id)) {
        console.log(`从缓存获取实验${id}详情`)
        this.currentExperiment = this.experimentCache.get(id)
        return this.currentExperiment
      }
      
      this.loading = true
      try {
        const response = await api.getExperimentDetails(id)
        console.log(`从后端获取实验${id}详情:`, response)
        
        // 统一响应数据结构
        if (response.data) {
          this.currentExperiment = response.data
        } else {
          this.currentExperiment = response
        }
        
        // 将详情保存到缓存
        this.experimentCache.set(id, this.currentExperiment)
        return this.currentExperiment
      } catch (error) {
        console.error(`获取实验${id}详情失败:`, error)
        return null
      } finally {
        this.loading = false
      }
    },
    
    // 从缓存获取实验详情
    getExperimentDetailsById(id) {
      return this.experimentCache.get(id)
    },
    
    async generateAIReport(experimentId, userData) {
      // 模拟AI报告生成，实际应调用后端API
      // 这里只做简单拼接和模拟
      this.generatingReport = true;
      try {
        // 模拟耗时
        await new Promise(resolve => setTimeout(resolve, 1200));
        // 生成简单的Markdown报告
        const report = `# ${userData.experimentName || '实验报告'}\n\n` +
          `## 学生信息\n- 姓名：${userData.studentName || ''}\n- 学号：${userData.studentId || ''}\n- 班级：${userData.className || ''}\n\n` +
          `## 实验目的\n实现顺序表的基本操作\n实现链表的基本操作\n完成示例应用程序\n撰写实验报告分析性能` +
          `## 实验内容\n12312312\n\n` +
          `## AI点评\n${userData.aiComment || '无'}\n\n` +
          `## 实验心得体会\n${userData.experience || '略'}\n`;
        return { success: true, report };
      } catch (e) {
        return { success: false, message: 'AI报告生成失败' };
      } finally {
        this.generatingReport = false;
      }
    },
    generateLinearListReport(experimentName, userData) {
      // 生成线性表实验的默认报告
      return `# ${experimentName || '线性表的实现与应用'} - 实验报告\n\n` +
        `## 1. 实验目的\n加深对线性表顺序存储和链式存储方式的理解，掌握线性表的基本操作算法及其应用。\n\n` +
        `## 2. 学生信息\n- 姓名：${userData.studentName || ''}\n- 学号：${userData.studentId || ''}\n- 班级：${userData.className || ''}\n\n` +
        `## 3. 实验内容\n实现线性表的基本操作，包括顺序表和链表的插入、删除、查找等。\n\n` +
        `## 4. 实验代码\n${userData.code ? '```c\n' + userData.code + '\n```' : '实验代码见实验平台'}\n\n` +
        `## 5. AI点评\n${userData.aiComment || '无'}\n\n` +
        `## 6. 实验心得体会\n${userData.experience || '略'}\n`;
    },
    
    // ... 其他方法保持不变 ...
  },
  getters: {
    // 修改getter，确保在experimentList不是数组时安全处理
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

// 学习分析状态
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
        console.log("获取推荐题目！！！！：",await api.getRecommendedPractices())
        this.recommendedPractices = await api.getRecommendedPractices()
        console.log("获取推荐题目！！！！：",this.recommendedPractices)
      } finally {
        this.loading = false
      }
    },
    async submitSelfAssessment(data) {
      return await api.submitSelfAssessment(data)
    }
  }
})
