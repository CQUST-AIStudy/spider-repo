import { defineStore } from 'pinia'
import { chatSend } from '../api/tap'

const MAX_MESSAGES = 50

function trimMessages(messages) {
  if (!Array.isArray(messages)) return []
  return messages.slice(-MAX_MESSAGES)
}

export const useTeacherAiChatStore = defineStore('teacherAiChat', {
  state: () => ({
    messages: [],
    draft: '',
    loading: false,
  }),
  persist: {
    key: 'teacher_ai_chat',
    storage: sessionStorage,
    paths: ['messages', 'draft']
  },
  actions: {
    setDraft(value) {
      this.draft = value
    },

    clearMessages() {
      this.messages = []
      this.loading = false
    },

    async sendMessage(rawMessage) {
      const message = (rawMessage || '').trim()
      if (!message || this.loading) return

      const history = this.messages
        .slice(-10)
        .map(item => ({ role: item.role, content: item.content }))

      this.loading = true
      this.draft = ''
      this.messages = trimMessages([
        ...this.messages,
        { role: 'user', content: message, createdAt: Date.now() }
      ])

      try {
        const res = await chatSend(message, history)
        const data = res?.data ?? res
        const assistantMessage = {
          role: 'assistant',
          content: data?.reply || '暂无回复',
          papers: Array.isArray(data?.papers) && data.papers.length ? data.papers : undefined,
          createdAt: Date.now()
        }
        this.messages = trimMessages([...this.messages, assistantMessage])
      } catch (error) {
        this.messages = trimMessages([
          ...this.messages,
          {
            role: 'assistant',
            content: `请求失败：${error?.message || '请稍后重试'}`,
            createdAt: Date.now()
          }
        ])
      } finally {
        this.loading = false
      }
    }
  }
})
