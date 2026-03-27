<template>
  <div class="ai-assistant-page">
    <div class="sidebar">
      <h3>AI 学习助手</h3>

      <el-select
        v-model="selectedCourseSpaceId"
        placeholder="选择课程空间（可选）"
        clearable
        style="width: 100%"
      >
        <el-option
          v-for="item in courseSpaces"
          :key="item.id"
          :label="buildCourseSpaceLabel(item)"
          :value="item.id"
        />
      </el-select>

      <div v-if="selectedCourseSpaceId" class="mode-row">
        <el-switch
          v-model="isOpenMode"
          active-text="开放模式"
          inactive-text="严格模式"
          size="small"
        />
        <p class="mode-hint">
          严格模式只依据课程资料回答。开放模式在课程覆盖不足时允许补充联网检索。
        </p>
        <div class="space-summary">
          当前空间：{{ buildCourseSpaceLabel(selectedCourseSpace) }}
        </div>
      </div>

      <div v-else class="empty-space-tip">
        <div class="empty-space-title">还没有可用的课程空间</div>
        <p>你可以先加入教学班，解锁班级授权的课程知识库；也可以先使用普通聊天模式。</p>
        <el-button size="small" @click="goClassJoin">去加入教学班</el-button>
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
      <el-alert
        v-if="assistantNotice"
        class="assistant-alert"
        type="warning"
        :closable="false"
        :title="assistantNotice"
        show-icon
      />

      <div ref="chatContainer" class="messages">
        <div v-for="(message, index) in messages" :key="index" :class="['msg', message.role]">
          <div class="meta">
            <span>{{ message.role === 'user' ? '我' : 'AI 助手' }}</span>
            <span>{{ message.time }}</span>
          </div>
          <div class="body" v-html="formatMessage(message.content)" />

          <div v-if="message.citations && message.citations.length" class="citations">
            <div v-for="cite in message.citations" :key="`${index}-${cite.index}`" class="citation-item">
              [{{ cite.index }}] {{ cite.docName || cite.title || '引用资料' }}
              <span v-if="cite.chapterPath"> | {{ cite.chapterPath }}</span>
              <span v-if="cite.pageRange"> | {{ cite.pageRange }}</span>
            </div>
          </div>
        </div>

        <div v-if="isTyping" class="msg ai">
          <div class="meta"><span>AI 助手</span><span>正在生成...</span></div>
          <div class="body">...</div>
        </div>
      </div>

      <div class="input-area">
        <el-input
          v-model="userInput"
          type="textarea"
          :rows="3"
          resize="none"
          placeholder="输入你的问题，按 Ctrl + Enter 发送"
          @keyup.enter.ctrl="sendMessage"
        />
        <div class="actions">
          <span>{{ selectedCourseSpaceId ? '当前为 RAG 问答模式' : '当前为普通聊天模式' }}</span>
          <el-button
            type="primary"
            :disabled="!userInput.trim() || isTyping"
            :loading="isTyping"
            @click="sendMessage"
          >
            发送
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import DOMPurify from 'dompurify'
import { marked } from 'marked'
import { buildApiUrl } from '../../config/runtime'

const router = useRouter()
const userInput = ref('')
const messages = ref([])
const isTyping = ref(false)
const chatContainer = ref(null)
const courseSpaces = ref([])
const selectedCourseSpaceId = ref(null)
const isOpenMode = ref(false)
const assistantNotice = ref('')

const quickPrompts = [
  { label: '顺序表和链表', prompt: '请解释顺序表和链表的区别，并给出适用场景。' },
  { label: '树的遍历', prompt: '前序、中序、后序遍历分别是什么？如何记忆？' },
  { label: '复杂度分析', prompt: '如何分析一个算法的时间复杂度和空间复杂度？' },
  { label: '代码优化', prompt: '我该如何优化一个查找算法？请给我常见思路。' }
]

const selectedCourseSpace = computed(() => {
  return courseSpaces.value.find((item) => item.id === selectedCourseSpaceId.value) || null
})

function formatMessage(content) {
  const rawHtml = marked.parse(content || '')
  return DOMPurify.sanitize(rawHtml)
}

function buildCourseSpaceLabel(courseSpace) {
  if (!courseSpace) return ''
  const parts = [courseSpace.courseName, courseSpace.name, courseSpace.term].filter(Boolean)
  const scope = courseSpace.docVisibility === 'class' ? '班级授权' : courseSpace.docVisibility === 'public' ? '公开' : null
  if (scope) parts.push(scope)
  return parts.join(' / ') || `课程空间 ${courseSpace.id}`
}

async function scrollToBottom() {
  await nextTick()
  if (chatContainer.value) {
    chatContainer.value.scrollTop = chatContainer.value.scrollHeight
  }
}

async function fetchCourseSpaces() {
  try {
    const response = await fetch(buildApiUrl('/api/student-rag/course-spaces'), {
      credentials: 'include'
    })
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`)
    }
    const data = await response.json()
    const spaces = Array.isArray(data) ? data : data?.data || []
    courseSpaces.value = spaces
    if (!selectedCourseSpaceId.value && spaces.length > 0) {
      selectedCourseSpaceId.value = spaces[0].id
    }
  } catch (error) {
    const isRagMode = !!selectedCourseSpaceId.value
    const friendlyMessage = formatAssistantError(error?.message, isRagMode)
    assistantNotice.value = friendlyMessage
    ElMessage.warning(friendlyMessage)
    courseSpaces.value = []
  }
}

function extractCitations(content) {
  const match = (content || '').match(/<!--CITATIONS:(.*?)-->/)
  if (!match) {
    return { text: content, citations: [] }
  }

  let citations = []
  try {
    citations = JSON.parse(match[1])
  } catch (error) {
    citations = []
  }

  return {
    text: content.replace(/\n?\n?<!--CITATIONS:.*?-->/, ''),
    citations
  }
}

async function readErrorMessage(response) {
  const contentType = response.headers.get('content-type') || ''
  try {
    if (contentType.includes('application/json')) {
      const payload = await response.json()
      return payload?.message || payload?.detail || payload?.error || `HTTP ${response.status}`
    }
    const text = await response.text()
    return text || `HTTP ${response.status}`
  } catch {
    return `HTTP ${response.status}`
  }
}

function formatAssistantError(message, isRagMode) {
  const raw = String(message || '')
  if (raw.includes('OPENAI_API_KEY') || raw.includes('AI service is not configured')) {
    return 'AI chat is not configured on the backend yet. Set OPENAI_API_KEY before using this entry.'
  }
  if (raw.includes('course space')) {
    return 'No accessible course space is available for RAG chat right now.'
  }
  if (raw.includes('401')) {
    return isRagMode
      ? 'RAG chat is unavailable because the current login session is invalid.'
      : 'AI chat is unavailable because the current login session is invalid.'
  }
  if (raw.includes('403')) {
    return isRagMode
      ? 'RAG chat is unavailable because the current account has no permission to use this course space.'
      : 'AI chat is unavailable because the current account has no permission.'
  }
  return isRagMode ? 'RAG chat is temporarily unavailable.' : 'AI chat is temporarily unavailable.'
}

async function sendMessage() {
  const text = userInput.value.trim()
  if (!text || isTyping.value) return
  assistantNotice.value = ''

  messages.value.push({ role: 'user', content: text, time: new Date().toLocaleTimeString() })
  userInput.value = ''
  await scrollToBottom()

  isTyping.value = true
  const aiIndex = messages.value.length
  messages.value.push({ role: 'ai', content: '', time: new Date().toLocaleTimeString(), citations: [] })

  try {
    const isRagMode = !!selectedCourseSpaceId.value
    const url = isRagMode ? buildApiUrl('/api/student-rag/chat') : buildApiUrl('/api/chat')
    const response = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify(
        isRagMode
          ? {
              courseSpaceId: selectedCourseSpaceId.value,
              query: text,
              mode: isOpenMode.value ? 'open' : 'strict'
            }
          : { userInput: text }
      )
    })

    if (!response.ok) {
      throw new Error(await readErrorMessage(response))
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
    }
  } catch (error) {
    const isRagMode = !!selectedCourseSpaceId.value
    const friendlyMessage = formatAssistantError(error?.message, isRagMode)
    assistantNotice.value = friendlyMessage
    ElMessage.warning(friendlyMessage)
    const current = messages.value[aiIndex]
    if (current && !current.content) {
      current.content = friendlyMessage
    }
  } finally {
    isTyping.value = false
    await scrollToBottom()
  }
}

function useQuickPrompt(prompt) {
  userInput.value = prompt
  nextTick(() => {
    const textarea = document.querySelector('.input-area textarea')
    if (textarea) textarea.focus()
  })
}

function goClassJoin() {
  router.push('/student/class-join')
}

onMounted(() => {
  fetchCourseSpaces()
  messages.value.push({
    role: 'ai',
    content: '你好，我是你的数据结构 AI 学习助手。已授权的课程空间会自动用于带引用的 RAG 问答；如果暂时没有课程空间，你仍然可以先进行普通聊天。',
    time: new Date().toLocaleTimeString(),
    citations: []
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
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.mode-hint {
  margin: 0;
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
}

.space-summary {
  padding: 8px 10px;
  background: #f5f7fa;
  border-radius: 8px;
  font-size: 12px;
  color: #606266;
  line-height: 1.5;
}

.empty-space-tip {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  background: #fff8e8;
  border: 1px solid #f5d28b;
  border-radius: 10px;
}

.empty-space-title {
  font-size: 14px;
  font-weight: 600;
  color: #7a4f01;
}

.empty-space-tip p {
  margin: 0;
  color: #8c6d1f;
  font-size: 12px;
  line-height: 1.6;
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

.assistant-alert {
  margin: 12px 12px 0;
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
  line-height: 1.7;
}

.msg.user .body {
  background: #e8f3ff;
}

.msg.ai .body {
  background: #f7f7f7;
}

.citations {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: #606266;
}

.citation-item {
  line-height: 1.5;
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
