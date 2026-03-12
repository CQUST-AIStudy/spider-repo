<template>
  <div class="ai-assistant-page">
    <div class="sidebar">
      <h3>AI Assistant</h3>

      <el-select
        v-model="selectedCourseSpaceId"
        placeholder="Select course space"
        clearable
        style="width: 100%"
      >
        <el-option
          v-for="item in courseSpaces"
          :key="item.id"
          :label="item.name"
          :value="item.id"
        />
      </el-select>

      <div class="mode-row" v-if="selectedCourseSpaceId">
        <el-switch v-model="isOpenMode" active-text="Open" inactive-text="Strict" size="small" />
      </div>

      <div class="quick-list">
        <el-button
          v-for="q in quickPrompts"
          :key="q.label"
          text
          @click="useQuickPrompt(q.prompt)"
        >
          {{ q.label }}
        </el-button>
      </div>
    </div>

    <div class="chat-panel">
      <div class="messages" ref="chatContainer">
        <div v-for="(message, index) in messages" :key="index" :class="['msg', message.role]">
          <div class="meta">
            <span>{{ message.role === 'user' ? 'You' : 'Assistant' }}</span>
            <span>{{ message.time }}</span>
          </div>
          <div class="body" v-html="formatMessage(message.content)" />

          <div v-if="message.citations && message.citations.length" class="citations">
            <div v-for="cite in message.citations" :key="`${index}-${cite.index}`">
              [{{ cite.index }}] {{ cite.docName || cite.title || 'source' }}
            </div>
          </div>

          <div v-if="message.role === 'ai' && message.qaLogId" class="feedback">
            <el-button size="small" @click="submitFeedback(message, 1)">Helpful</el-button>
            <el-button size="small" @click="submitFeedback(message, -1)">Not helpful</el-button>
          </div>
        </div>

        <div v-if="isTyping" class="msg ai">
          <div class="meta"><span>Assistant</span><span>typing...</span></div>
          <div class="body">...</div>
        </div>
      </div>

      <div class="input-area">
        <el-input
          v-model="userInput"
          type="textarea"
          :rows="3"
          resize="none"
          placeholder="Type your question..."
          @keyup.enter.ctrl="sendMessage"
        />
        <div class="actions">
          <span>Ctrl + Enter to send</span>
          <el-button type="primary" :disabled="!userInput.trim() || isTyping" :loading="isTyping" @click="sendMessage">
            Send
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { nextTick, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import DOMPurify from 'dompurify'
import { marked } from 'marked'

const userInput = ref('')
const messages = ref([])
const isTyping = ref(false)
const chatContainer = ref(null)
const courseSpaces = ref([])
const selectedCourseSpaceId = ref(null)
const isOpenMode = ref(false)

const quickPrompts = [
  { label: 'Array vs LinkedList', prompt: 'Explain differences between array and linked list.' },
  { label: 'Tree traversal', prompt: 'What are preorder, inorder, and postorder traversals?' },
  { label: 'Complexity', prompt: 'How to analyze algorithm time complexity?' },
  { label: 'Optimize code', prompt: 'How can I optimize this search algorithm?' }
]

const formatMessage = (content) => {
  const rawHtml = marked.parse(content || '')
  return DOMPurify.sanitize(rawHtml)
}

const scrollToBottom = async () => {
  await nextTick()
  if (chatContainer.value) {
    chatContainer.value.scrollTop = chatContainer.value.scrollHeight
  }
}

const fetchCourseSpaces = async () => {
  try {
    const token = localStorage.getItem('tap_token')
    if (!token) return
    const response = await fetch('http://localhost:8081/api/course-spaces', {
      headers: { Authorization: `Bearer ${token}` }
    })
    if (!response.ok) return
    const data = await response.json()
    courseSpaces.value = Array.isArray(data) ? data : data?.data || []
  } catch (error) {
    console.warn('Failed to load course spaces:', error)
  }
}

const extractCitations = (content) => {
  const match = content.match(/<!--CITATIONS:(.*?)-->/)
  if (!match) return { text: content, citations: [] }

  let citations = []
  try {
    citations = JSON.parse(match[1])
  } catch (error) {
    console.warn('Failed to parse citations:', error)
  }

  return {
    text: content.replace(/\n?\n?<!--CITATIONS:.*?-->/, ''),
    citations
  }
}

const sendMessage = async () => {
  const text = userInput.value.trim()
  if (!text || isTyping.value) return

  messages.value.push({ role: 'user', content: text, time: new Date().toLocaleTimeString() })
  userInput.value = ''
  await scrollToBottom()

  isTyping.value = true
  const aiIndex = messages.value.length
  messages.value.push({ role: 'ai', content: '', time: new Date().toLocaleTimeString(), citations: [] })

  try {
    const isRagMode = !!selectedCourseSpaceId.value
    const url = isRagMode ? 'http://localhost:8081/api/rag/chat' : 'http://localhost:8081/api/chat'
    const body = isRagMode
      ? {
          courseSpaceId: selectedCourseSpaceId.value,
          query: text,
          mode: isOpenMode.value ? 'open' : 'strict'
        }
      : { userInput: text }

    const headers = { 'Content-Type': 'application/json' }
    const token = localStorage.getItem('tap_token')
    if (token && isRagMode) {
      headers.Authorization = `Bearer ${token}`
    }

    const response = await fetch(url, {
      method: 'POST',
      headers,
      body: JSON.stringify(body)
    })

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`)
    }

    const reader = response.body?.getReader()
    if (!reader) {
      throw new Error('No response body reader')
    }

    const decoder = new TextDecoder()
    let done = false
    while (!done) {
      const chunk = await reader.read()
      done = chunk.done
      if (done) break
      messages.value[aiIndex].content += decoder.decode(chunk.value, { stream: true })
      await scrollToBottom()
    }

    if (isRagMode) {
      const parsed = extractCitations(messages.value[aiIndex].content)
      messages.value[aiIndex].content = parsed.text
      messages.value[aiIndex].citations = parsed.citations
      const qaLogId = parsed.citations?.[0]?.qaLogId
      if (qaLogId) messages.value[aiIndex].qaLogId = qaLogId
    }
  } catch (error) {
    console.error('Failed to get AI response:', error)
    ElMessage.error('Failed to get AI response, please retry.')
    const current = messages.value[aiIndex]
    if (current && !current.content) {
      current.content = 'Sorry, the response failed. Please try again later.'
    }
  } finally {
    isTyping.value = false
    await scrollToBottom()
  }
}

const submitFeedback = async (message, value) => {
  if (!message.qaLogId) return
  try {
    const token = localStorage.getItem('tap_token')
    await fetch('http://localhost:8081/api/rag/feedback', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      },
      body: JSON.stringify({ qaLogId: message.qaLogId, feedback: value })
    })
    message.feedback = value
    ElMessage.success('Feedback submitted.')
  } catch (error) {
    ElMessage.error('Feedback submission failed.')
  }
}

const useQuickPrompt = (prompt) => {
  userInput.value = prompt
  nextTick(() => {
    const textarea = document.querySelector('.input-area textarea')
    if (textarea) textarea.focus()
  })
}

onMounted(() => {
  fetchCourseSpaces()
  messages.value.push({
    role: 'ai',
    content: 'Hello, I am your data-structure assistant. Ask me anything about algorithms and data structures.',
    time: new Date().toLocaleTimeString()
  })
})
</script>

<style scoped>
.ai-assistant-page {
  display: flex;
  gap: 16px;
  height: 100%;
  padding: 16px;
  background: #f5f7fa;
}

.sidebar {
  width: 260px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 12px;
  border-radius: 10px;
  background: #fff;
  border: 1px solid #e6eaf0;
}

.mode-row {
  margin-top: 8px;
}

.quick-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.chat-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  border-radius: 10px;
  background: #fff;
  border: 1px solid #e6eaf0;
  overflow: hidden;
}

.messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

.msg {
  margin-bottom: 12px;
}

.msg .meta {
  display: flex;
  justify-content: space-between;
  color: #909399;
  font-size: 12px;
  margin-bottom: 4px;
}

.msg .body {
  padding: 10px;
  border-radius: 8px;
}

.msg.user .body {
  background: #e8f3ff;
}

.msg.ai .body {
  background: #f7f7f7;
}

.citations {
  margin-top: 6px;
  font-size: 12px;
  color: #606266;
}

.feedback {
  margin-top: 8px;
  display: flex;
  gap: 8px;
}

.input-area {
  border-top: 1px solid #e6eaf0;
  padding: 12px;
}

.actions {
  margin-top: 8px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: #909399;
  font-size: 12px;
}

@media (max-width: 900px) {
  .ai-assistant-page {
    flex-direction: column;
  }

  .sidebar {
    width: 100%;
  }
}
</style>
