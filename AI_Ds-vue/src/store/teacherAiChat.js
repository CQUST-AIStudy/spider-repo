import { defineStore } from 'pinia'
import { chatSend, chatStreamSend } from '../api/tap'

const MAX_MESSAGES = 50
const PAPERS_MARKER = '<!--PAPERS:'
const DEFAULT_EMPTY_REPLY = 'No response from AI.'
const DEFAULT_ERROR_MESSAGE = 'Request failed. Please try again later.'

function trimMessages(messages) {
  if (!Array.isArray(messages)) return []
  return messages.slice(-MAX_MESSAGES)
}

function normalizeAssistantPayload(fullText) {
  if (!fullText) {
    return { content: '', papers: undefined }
  }

  const markerIndex = fullText.indexOf(PAPERS_MARKER)
  if (markerIndex === -1) {
    return { content: fullText, papers: undefined }
  }

  const content = fullText.slice(0, markerIndex).replace(/\s+$/, '')
  const markerEnd = fullText.indexOf('-->', markerIndex)
  if (markerEnd === -1) {
    return { content, papers: undefined }
  }

  const jsonText = fullText.slice(markerIndex + PAPERS_MARKER.length, markerEnd)
  try {
    const papers = JSON.parse(jsonText)
    return {
      content,
      papers: Array.isArray(papers) && papers.length ? papers : undefined,
    }
  } catch {
    return { content, papers: undefined }
  }
}

async function readErrorMessage(response) {
  const contentType = response.headers.get('content-type') || ''
  try {
    if (contentType.includes('application/json')) {
      const payload = await response.json()
      return payload?.message || payload?.error || `Request failed (${response.status})`
    }
    const text = await response.text()
    return text || `Request failed (${response.status})`
  } catch {
    return `Request failed (${response.status})`
  }
}

function appendAssistantMessage(messages, content = '', papers) {
  return trimMessages([
    ...messages,
    {
      role: 'assistant',
      content,
      papers,
      createdAt: Date.now() + 1,
    }
  ])
}

function resolveReplyText(payload) {
  const reply = payload?.reply
  if (typeof reply === 'string' && reply.trim()) return reply
  return DEFAULT_EMPTY_REPLY
}

export const useTeacherAiChatStore = defineStore('teacherAiChat', {
  state: () => ({
    messages: [],
    draft: '',
    loading: false,
    streaming: false,
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
      this.streaming = false
    },

    async sendMessage(rawMessage) {
      const message = (rawMessage || '').trim()
      if (!message || this.loading || this.streaming) return

      const history = this.messages
        .slice(-10)
        .filter(item => item?.content)
        .map(item => ({ role: item.role, content: item.content }))

      this.loading = true
      this.streaming = false
      this.draft = ''
      this.messages = trimMessages([
        ...this.messages,
        { role: 'user', content: message, createdAt: Date.now() }
      ])

      let assistantIndex = -1

      try {
        const response = await chatStreamSend(message, history)
        if (!response.ok) {
          throw new Error(await readErrorMessage(response))
        }

        const reader = response.body?.getReader()
        if (!reader) {
          const fallback = await chatSend(message, history)
          const data = fallback?.data ?? fallback
          this.messages = appendAssistantMessage(
            this.messages,
            resolveReplyText(data),
            Array.isArray(data?.papers) && data.papers.length ? data.papers : undefined,
          )
          return
        }

        const decoder = new TextDecoder()
        let fullText = ''
        let done = false

        while (!done) {
          const chunk = await reader.read()
          done = chunk.done
          if (done) break

          fullText += decoder.decode(chunk.value, { stream: true })
          const parsed = normalizeAssistantPayload(fullText)

          if (assistantIndex === -1) {
            this.messages = appendAssistantMessage(this.messages, parsed.content, parsed.papers)
            assistantIndex = this.messages.length - 1
            this.loading = false
            this.streaming = true
          } else {
            this.messages[assistantIndex] = {
              ...this.messages[assistantIndex],
              content: parsed.content,
              papers: parsed.papers,
            }
          }
        }

        fullText += decoder.decode()
        const parsed = normalizeAssistantPayload(fullText)

        let finalContent = parsed.content?.trim()
        let finalPapers = parsed.papers

        // Stream may complete without visible text if provider chunk format changed.
        if (!finalContent) {
          try {
            const fallback = await chatSend(message, history)
            const data = fallback?.data ?? fallback
            finalContent = resolveReplyText(data)
            if (Array.isArray(data?.papers) && data.papers.length) {
              finalPapers = data.papers
            }
          } catch {
            finalContent = DEFAULT_EMPTY_REPLY
          }
        }

        if (assistantIndex === -1) {
          this.messages = appendAssistantMessage(this.messages, finalContent, finalPapers)
        } else {
          this.messages[assistantIndex] = {
            ...this.messages[assistantIndex],
            content: finalContent,
            papers: finalPapers,
          }
        }
      } catch (error) {
        const errorText = `Request failed: ${error?.message || DEFAULT_ERROR_MESSAGE}`
        if (assistantIndex === -1) {
          this.messages = appendAssistantMessage(this.messages, errorText, undefined)
        } else {
          this.messages[assistantIndex] = {
            ...this.messages[assistantIndex],
            content: errorText,
            papers: undefined,
          }
        }
      } finally {
        this.loading = false
        this.streaming = false
      }
    }
  }
})
