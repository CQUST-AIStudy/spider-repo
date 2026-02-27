<template>
  <div class="ai-assistant-container">
    <div class="ai-hero-banner">
      <div class="hero-content">
        <div class="hero-icon-wrap">
          <svg viewBox="0 0 48 48" width="40" height="40" fill="none">
            <circle cx="24" cy="24" r="22" fill="url(#g1)" />
            <path d="M16 20a2 2 0 114 0 2 2 0 01-4 0zm12 0a2 2 0 114 0 2 2 0 01-4 0zM18 30s2 3 6 3 6-3 6-3" stroke="#fff" stroke-width="2" stroke-linecap="round"/>
            <defs><linearGradient id="g1" x1="0" y1="0" x2="48" y2="48"><stop stop-color="#667eea"/><stop offset="1" stop-color="#764ba2"/></linearGradient></defs>
          </svg>
        </div>
        <div>
          <h2 class="hero-title">AI 学习助手</h2>
          <p class="hero-desc">数据结构精准辅导，解决您的学习难题</p>
        </div>
      </div>
    </div>
    <div class="ai-body">
      <div class="ai-sidebar">
        <div class="sidebar-section">
          <div class="section-title"><span class="section-icon">🤖</span> AI 能力</div>
          <div class="cap-grid">
            <div class="cap-item" v-for="cap in capabilities" :key="cap.text">
              <span class="cap-emoji">{{ cap.emoji }}</span>
              <span class="cap-text">{{ cap.text }}</span>
            </div>
          </div>
        </div>
        <div class="sidebar-section">
          <div class="section-title"><span class="section-icon">⚡</span> 快速提问</div>
          <div class="quick-grid">
            <button class="quick-btn" v-for="q in quickPrompts" :key="q.label" @click="useQuickPrompt(q.prompt)">
              <span class="quick-emoji">{{ q.emoji }}</span>{{ q.label }}
            </button>
          </div>
        </div>
        <div class="sidebar-section">
          <div class="section-title"><span class="section-icon">📚</span> 课程空间</div>
          <el-select v-model="selectedCourseSpaceId" placeholder="选择课程空间" clearable size="small" style="width:100%">
            <el-option v-for="cs in courseSpaces" :key="cs.id" :label="cs.name" :value="cs.id" />
          </el-select>
          <div class="mode-row" v-if="selectedCourseSpaceId">
            <el-switch v-model="isOpenMode" active-text="开放" inactive-text="严格" size="small" />
            <span class="mode-hint">📖 RAG模式</span>
          </div>
          <div class="mode-hint" v-else style="margin-top:6px">💬 纯对话模式</div>
        </div>
      </div>
      <div class="chat-panel">
        <div class="chat-messages" ref="chatContainer">
          <div v-if="messages.length === 0" class="empty-chat">
            <div class="empty-icon">💬</div>
            <p>开始和AI助手对话吧</p>
          </div>
          <div v-for="(message, index) in messages" :key="index"
               :class="['msg-row', message.role === 'user' ? 'msg-user' : 'msg-ai']">
            <div class="msg-avatar" v-if="message.role !== 'user'">
              <div class="avatar-ai">AI</div>
            </div>
            <div class="msg-bubble">
              <div class="msg-meta">
                <span class="msg-sender">{{ message.role === 'user' ? '我' : 'AI助手' }}</span>
                <span class="msg-time">{{ message.time }}</span>
              </div>
              <div class="msg-body" v-html="formatMessage(message.content)"></div>
              <div v-if="message.citations && message.citations.length > 0" class="citations-area">
                <el-collapse>
                  <el-collapse-item title="📖 引用来源">
                    <div v-for="cite in message.citations" :key="cite.index" class="citation-item">
                      <template v-if="cite.source === 'web'">
                        <el-tag size="small" type="warning" style="margin-right:6px">联网</el-tag>
                        [{{ cite.index }}] {{ cite.docName || cite.title || '网络来源' }}
                        <a v-if="cite.url" :href="cite.url" target="_blank" rel="noopener" class="web-link">{{ cite.url }}</a>
                      </template>
                      <template v-else>
                        <el-tag size="small" type="success" style="margin-right:6px">课程</el-tag>
                        [{{ cite.index }}] 《{{ cite.docName }}》{{ cite.chapterPath }}
                        <span v-if="cite.pageRange">(第{{ cite.pageRange }}页)</span>
                      </template>
                      <el-tag size="small" type="info" style="margin-left:8px">{{ (cite.score * 100).toFixed(0) }}%</el-tag>
                    </div>
                  </el-collapse-item>
                </el-collapse>
              </div>
              <div v-if="message.role === 'ai' && message.qaLogId" class="feedback-row">
                <span class="feedback-hint">有帮助吗？</span>
                <button :class="['fb-btn', message.feedback === 1 && 'fb-active-good']" @click="submitFeedback(message, 1)">👍</button>
                <button :class="['fb-btn', message.feedback === -1 && 'fb-active-bad']" @click="submitFeedback(message, -1)">👎</button>
              </div>
            </div>
            <div class="msg-avatar" v-if="message.role === 'user'">
              <div class="avatar-user">{{ userInitial }}</div>
            </div>
          </div>
          <div v-if="isTyping" class="msg-row msg-ai">
            <div class="msg-avatar"><div class="avatar-ai">AI</div></div>
            <div class="msg-bubble">
              <div class="typing-dots"><span></span><span></span><span></span></div>
            </div>
          </div>
        </div>
        <div class="chat-input-area">
          <el-input v-model="userInput" type="textarea" :rows="3" resize="none"
                    placeholder="输入您的问题，例如：如何实现一个平衡二叉树？"
                    @keyup.enter.ctrl="sendMessage" />
          <div class="input-footer">
            <span class="input-tip">Ctrl + Enter 发送</span>
            <el-button type="primary" :disabled="!userInput.trim() || isTyping" @click="sendMessage" :loading="isTyping" class="send-btn">发送</el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
<script setup>
import { ref, onMounted, nextTick, computed } from 'vue'
import { useUserStore } from '../../store'
import { ElMessage } from 'element-plus'
import DOMPurify from 'dompurify'
import { marked } from 'marked'

const userStore = useUserStore()
const userInput = ref('')
const messages = ref([])
const isTyping = ref(false)
const chatContainer = ref(null)
const courseSpaces = ref([])
const selectedCourseSpaceId = ref(null)
const isOpenMode = ref(false)

const userInitial = computed(() => {
  const info = userStore.userInfo
  return (info?.name || info?.username || '我').charAt(0)
})

const capabilities = [
  { emoji: '❓', text: '解答概念问题' },
  { emoji: '⏱️', text: '分析算法复杂度' },
  { emoji: '💡', text: '最佳实践建议' },
  { emoji: '🔍', text: '解释代码思路' },
  { emoji: '🧩', text: '设计解决方案' }
]

const quickPrompts = [
  { emoji: '🔗', label: '链表 vs 数组', prompt: '请解释链表和数组的主要区别？' },
  { emoji: '🌲', label: '树的遍历', prompt: '二叉树的前序、中序和后序遍历有什么区别？' },
  { emoji: '📊', label: '复杂度分析', prompt: '如何判断一个算法的时间复杂度？' },
  { emoji: '⚡', label: '代码优化', prompt: '请帮我优化这段查找代码...' }
]

const formatMessage = (content) => {
  const rawHtml = marked(content)
  return DOMPurify.sanitize(rawHtml)
}

const fetchCourseSpaces = async () => {
  try {
    const token = localStorage.getItem('tap_token')
    if (!token) return
    const res = await fetch('http://localhost:8081/api/course-spaces', {
      headers: { 'Authorization': `Bearer ${token}` }
    })
    if (res.ok) {
      const data = await res.json()
      courseSpaces.value = Array.isArray(data) ? data : (data.data || [])
    }
  } catch (e) { console.warn('获取课程空间列表失败:', e) }
}

const sendMessage = async () => {
  const trimmedInput = userInput.value.trim()
  if (!trimmedInput || isTyping.value) return
  messages.value.push({ role: 'user', content: trimmedInput, time: new Date().toLocaleTimeString() })
  userInput.value = ''
  await scrollToBottom()
  isTyping.value = true
  try {
    const aiMessageIndex = messages.value.length
    messages.value.push({ role: 'ai', content: '', time: new Date().toLocaleTimeString() })
    const isRagMode = !!selectedCourseSpaceId.value
    const url = isRagMode ? 'http://localhost:8081/api/rag/chat' : 'http://localhost:8081/api/chat'
    const body = isRagMode
        ? { courseSpaceId: selectedCourseSpaceId.value, query: trimmedInput, mode: isOpenMode.value ? 'open' : 'strict' }
        : { userInput: trimmedInput }
    const headers = { 'Content-Type': 'application/json' }
    const token = localStorage.getItem('tap_token')
    if (token && isRagMode) headers['Authorization'] = `Bearer ${token}`
    const response = await fetch(url, { method: 'POST', headers, body: JSON.stringify(body) })
    if (!response.ok) throw new Error(`服务器响应错误: ${response.status}`)
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let streamDone = false
    while (!streamDone) {
      try {
        const { done, value } = await reader.read()
        streamDone = done
        if (done) break
        messages.value[aiMessageIndex].content += decoder.decode(value, { stream: true })
        await scrollToBottom()
      } catch (error) {
        console.error('读取流式响应时发生错误:', error)
        ElMessage.error('读取 AI 回复时发生错误')
        break
      }
    }
    if (isRagMode) {
      const fullContent = messages.value[aiMessageIndex].content
      const citationMatch = fullContent.match(/<!--CITATIONS:(.*?)-->/)
      if (citationMatch) {
        try { messages.value[aiMessageIndex].citations = JSON.parse(citationMatch[1]) } catch (e) {}
        messages.value[aiMessageIndex].content = fullContent.replace(/\n?\n?<!--CITATIONS:.*?-->/, '')
      }
    }
  } catch (error) {
    console.error('获取AI回复失败:', error)
    ElMessage.error('获取AI回复失败，请稍后再试')
    const last = messages.value[messages.value.length - 1]
    if (last?.role === 'ai' && !last.content) last.content = '抱歉，服务器响应出现问题，请稍后再试。'
    else messages.value.push({ role: 'ai', content: '抱歉，服务器响应出现问题，请稍后再试。', time: new Date().toLocaleTimeString() })
  } finally {
    isTyping.value = false
    await scrollToBottom()
  }
}

const useQuickPrompt = (prompt) => {
  userInput.value = prompt
  nextTick(() => { document.querySelector('.chat-input-area textarea')?.focus() })
}

const scrollToBottom = async () => {
  await nextTick()
  if (chatContainer.value) chatContainer.value.scrollTop = chatContainer.value.scrollHeight
}

const submitFeedback = async (message, value) => {
  if (!message.qaLogId) return
  try {
    const token = localStorage.getItem('tap_token')
    await fetch('http://localhost:8081/api/rag/feedback', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
      body: JSON.stringify({ qaLogId: message.qaLogId, feedback: value })
    })
    message.feedback = value
    ElMessage.success(value === 1 ? '感谢您的肯定！' : '感谢反馈，我们会改进！')
  } catch (e) { ElMessage.error('反馈提交失败') }
}

onMounted(() => {
  fetchCourseSpaces()
  messages.value.push({
    role: 'ai',
    content: "# 你好，我是数据结构AI助手！👋\n\n我将帮助你解决数据结构学习中的各种问题。你可以向我咨询：\n\n- 数据结构基本概念\n- 算法时间复杂度和空间复杂度分析\n- 数据结构实现方法和技巧\n- 代码问题的解决方案\n\n快来向我提问吧！你也可以使用左侧的快速提问按钮。",
    time: new Date().toLocaleTimeString()
  })
})
</script>

<script setup>
import { ref, onMounted, nextTick, computed } from 'vue'
import { useUserStore } from '../../store'
import { ElMessage } from 'element-plus'
import DOMPurify from 'dompurify'
import { marked } from 'marked'

const userStore = useUserStore()
const userInput = ref('')
const messages = ref([])
const isTyping = ref(false)
const chatContainer = ref(null)
const courseSpaces = ref([])
const selectedCourseSpaceId = ref(null)
const isOpenMode = ref(false)

const userInitial = computed(() => {
  const info = userStore.userInfo
  return (info?.name || info?.username || '我').charAt(0)
})

const capabilities = [
  { emoji: '❓', text: '解答概念问题' },
  { emoji: '⏱️', text: '分析算法复杂度' },
  { emoji: '💡', text: '最佳实践建议' },
  { emoji: '🔍', text: '解释代码思路' },
  { emoji: '🧩', text: '设计解决方案' }
]

const quickPrompts = [
  { emoji: '🔗', label: '链表 vs 数组', prompt: '请解释链表和数组的主要区别？' },
  { emoji: '🌲', label: '树的遍历', prompt: '二叉树的前序、中序和后序遍历有什么区别？' },
  { emoji: '📊', label: '复杂度分析', prompt: '如何判断一个算法的时间复杂度？' },
  { emoji: '⚡', label: '代码优化', prompt: '请帮我优化这段查找代码...' }
]

const formatMessage = (content) => {
  const rawHtml = marked(content)
  return DOMPurify.sanitize(rawHtml)
}

const fetchCourseSpaces = async () => {
  try {
    const token = localStorage.getItem('tap_token')
    if (!token) return
    const res = await fetch('http://localhost:8081/api/course-spaces', {
      headers: { 'Authorization': `Bearer ${token}` }
    })
    if (res.ok) {
      const data = await res.json()
      courseSpaces.value = Array.isArray(data) ? data : (data.data || [])
    }
  } catch (e) { console.warn('获取课程空间列表失败:', e) }
}

const sendMessage = async () => {
  const trimmedInput = userInput.value.trim()
  if (!trimmedInput || isTyping.value) return
  messages.value.push({ role: 'user', content: trimmedInput, time: new Date().toLocaleTimeString() })
  userInput.value = ''
  await scrollToBottom()
  isTyping.value = true
  try {
    const aiMessageIndex = messages.value.length
    messages.value.push({ role: 'ai', content: '', time: new Date().toLocaleTimeString() })
    const isRagMode = !!selectedCourseSpaceId.value
    const url = isRagMode ? 'http://localhost:8081/api/rag/chat' : 'http://localhost:8081/api/chat'
    const body = isRagMode
        ? { courseSpaceId: selectedCourseSpaceId.value, query: trimmedInput, mode: isOpenMode.value ? 'open' : 'strict' }
        : { userInput: trimmedInput }
    const headers = { 'Content-Type': 'application/json' }
    const token = localStorage.getItem('tap_token')
    if (token && isRagMode) headers['Authorization'] = `Bearer ${token}`
    const response = await fetch(url, { method: 'POST', headers, body: JSON.stringify(body) })
    if (!response.ok) throw new Error('服务器响应错误: ' + response.status)
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let streamDone = false
    while (!streamDone) {
      try {
        const { done, value } = await reader.read()
        streamDone = done
        if (done) break
        messages.value[aiMessageIndex].content += decoder.decode(value, { stream: true })
        await scrollToBottom()
      } catch (error) {
        console.error('读取流式响应时发生错误:', error)
        ElMessage.error('读取 AI 回复时发生错误')
        break
      }
    }
    if (isRagMode) {
      const fullContent = messages.value[aiMessageIndex].content
      const citationMatch = fullContent.match(/<!--CITATIONS:(.*?)-->/)
      if (citationMatch) {
        try { messages.value[aiMessageIndex].citations = JSON.parse(citationMatch[1]) } catch (e) {}
        messages.value[aiMessageIndex].content = fullContent.replace(/\n?\n?<!--CITATIONS:.*?-->/, '')
      }
    }
  } catch (error) {
    console.error('获取AI回复失败:', error)
    ElMessage.error('获取AI回复失败，请稍后再试')
    const last = messages.value[messages.value.length - 1]
    if (last?.role === 'ai' && !last.content) last.content = '抱歉，服务器响应出现问题，请稍后再试。'
    else messages.value.push({ role: 'ai', content: '抱歉，服务器响应出现问题，请稍后再试。', time: new Date().toLocaleTimeString() })
  } finally {
    isTyping.value = false
    await scrollToBottom()
  }
}

const useQuickPrompt = (prompt) => {
  userInput.value = prompt
  nextTick(() => { document.querySelector('.chat-input-area textarea')?.focus() })
}

const scrollToBottom = async () => {
  await nextTick()
  if (chatContainer.value) chatContainer.value.scrollTop = chatContainer.value.scrollHeight
}

const submitFeedback = async (message, value) => {
  if (!message.qaLogId) return
  try {
    const token = localStorage.getItem('tap_token')
    await fetch('http://localhost:8081/api/rag/feedback', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
      body: JSON.stringify({ qaLogId: message.qaLogId, feedback: value })
    })
    message.feedback = value
    ElMessage.success(value === 1 ? '感谢您的肯定！' : '感谢反馈，我们会改进！')
  } catch (e) { ElMessage.error('反馈提交失败') }
}

onMounted(() => {
  fetchCourseSpaces()
  messages.value.push({
    role: 'ai',
    content: '# 你好，我是数据结构AI助手！👋\n\n我将帮助你解决数据结构学习中的各种问题。你可以向我咨询：\n\n- 数据结构基本概念\n- 算法时间复杂度和空间复杂度分析\n- 数据结构实现方法和技巧\n- 代码问题的解决方案\n\n快来向我提问吧！你也可以使用左侧的快速提问按钮。',
    time: new Date().toLocaleTimeString()
  })
})
</script>

<script setup>
import { ref, onMounted, nextTick, computed } from 'vue'
import { useUserStore } from '../../store'
import { ElMessage } from 'element-plus'
import DOMPurify from 'dompurify'
import { marked } from 'marked'

const userStore = useUserStore()
const userInput = ref('')
const messages = ref([])
const isTyping = ref(false)
const chatContainer = ref(null)
const courseSpaces = ref([])
const selectedCourseSpaceId = ref(null)
const isOpenMode = ref(false)

const userInitial = computed(() => {
  const info = userStore.userInfo
  return (info?.name || info?.username || '我').charAt(0)
})

const capabilities = [
  { emoji: '❓', text: '解答概念问题' },
  { emoji: '⏱️', text: '分析算法复杂度' },
  { emoji: '💡', text: '最佳实践建议' },
  { emoji: '🔍', text: '解释代码思路' },
  { emoji: '🧩', text: '设计解决方案' }
]

const quickPrompts = [
  { emoji: '🔗', label: '链表 vs 数组', prompt: '请解释链表和数组的主要区别？' },
  { emoji: '🌲', label: '树的遍历', prompt: '二叉树的前序、中序和后序遍历有什么区别？' },
  { emoji: '📊', label: '复杂度分析', prompt: '如何判断一个算法的时间复杂度？' },
  { emoji: '⚡', label: '代码优化', prompt: '请帮我优化这段查找代码...' }
]

const formatMessage = (content) => {
  const rawHtml = marked(content)
  return DOMPurify.sanitize(rawHtml)
}

const fetchCourseSpaces = async () => {
  try {
    const token = localStorage.getItem('tap_token')
    if (!token) return
    const res = await fetch('http://localhost:8081/api/course-spaces', {
      headers: { 'Authorization': `Bearer ${token}` }
    })
    if (res.ok) {
      const data = await res.json()
      courseSpaces.value = Array.isArray(data) ? data : (data.data || [])
    }
  } catch (e) { console.warn('获取课程空间列表失败:', e) }
}

const sendMessage = async () => {
  const trimmedInput = userInput.value.trim()
  if (!trimmedInput || isTyping.value) return
  messages.value.push({ role: 'user', content: trimmedInput, time: new Date().toLocaleTimeString() })
  userInput.value = ''
  await scrollToBottom()
  isTyping.value = true
  try {
    const aiMessageIndex = messages.value.length
    messages.value.push({ role: 'ai', content: '', time: new Date().toLocaleTimeString() })
    const isRagMode = !!selectedCourseSpaceId.value
    const url = isRagMode ? 'http://localhost:8081/api/rag/chat' : 'http://localhost:8081/api/chat'
    const body = isRagMode
        ? { courseSpaceId: selectedCourseSpaceId.value, query: trimmedInput, mode: isOpenMode.value ? 'open' : 'strict' }
        : { userInput: trimmedInput }
    const headers = { 'Content-Type': 'application/json' }
    const token = localStorage.getItem('tap_token')
    if (token && isRagMode) headers['Authorization'] = `Bearer ${token}`
    const response = await fetch(url, { method: 'POST', headers, body: JSON.stringify(body) })
    if (!response.ok) throw new Error('服务器响应错误: ' + response.status)
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let streamDone = false
    while (!streamDone) {
      try {
        const { done, value } = await reader.read()
        streamDone = done
        if (done) break
        messages.value[aiMessageIndex].content += decoder.decode(value, { stream: true })
        await scrollToBottom()
      } catch (error) {
        console.error('读取流式响应时发生错误:', error)
        ElMessage.error('读取 AI 回复时发生错误')
        break
      }
    }
    if (isRagMode) {
      const fullContent = messages.value[aiMessageIndex].content
      const citationMatch = fullContent.match(/<!--CITATIONS:(.*?)-->/)
      if (citationMatch) {
        try { messages.value[aiMessageIndex].citations = JSON.parse(citationMatch[1]) } catch (e) {}
        messages.value[aiMessageIndex].content = fullContent.replace(/\n?\n?<!--CITATIONS:.*?-->/, '')
      }
    }
  } catch (error) {
    console.error('获取AI回复失败:', error)
    ElMessage.error('获取AI回复失败，请稍后再试')
    const last = messages.value[messages.value.length - 1]
    if (last && last.role === 'ai' && !last.content) last.content = '抱歉，服务器响应出现问题，请稍后再试。'
    else messages.value.push({ role: 'ai', content: '抱歉，服务器响应出现问题，请稍后再试。', time: new Date().toLocaleTimeString() })
  } finally {
    isTyping.value = false
    await scrollToBottom()
  }
}

const useQuickPrompt = (prompt) => {
  userInput.value = prompt
  nextTick(() => { document.querySelector('.chat-input-area textarea')?.focus() })
}

const scrollToBottom = async () => {
  await nextTick()
  if (chatContainer.value) chatContainer.value.scrollTop = chatContainer.value.scrollHeight
}

const submitFeedback = async (message, value) => {
  if (!message.qaLogId) return
  try {
    const token = localStorage.getItem('tap_token')
    await fetch('http://localhost:8081/api/rag/feedback', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
      body: JSON.stringify({ qaLogId: message.qaLogId, feedback: value })
    })
    message.feedback = value
    ElMessage.success(value === 1 ? '感谢您的肯定！' : '感谢反馈，我们会改进！')
  } catch (e) { ElMessage.error('反馈提交失败') }
}

onMounted(() => {
  fetchCourseSpaces()
  messages.value.push({
    role: 'ai',
    content: '# 你好，我是数据结构AI助手！👋\n\n我将帮助你解决数据结构学习中的各种问题。你可以向我咨询：\n\n- 数据结构基本概念\n- 算法时间复杂度和空间复杂度分析\n- 数据结构实现方法和技巧\n- 代码问题的解决方案\n\n快来向我提问吧！你也可以使用左侧的快速提问按钮。',
    time: new Date().toLocaleTimeString()
  })
})
</script>
<script setup>
import { ref, onMounted, nextTick, computed } from 'vue'
import { useUserStore } from '../../store'
import { ElMessage } from 'element-plus'
import DOMPurify from 'dompurify'
import { marked } from 'marked'

const userStore = useUserStore()
const userInput = ref('')
const messages = ref([])
const isTyping = ref(false)
const chatContainer = ref(null)
const courseSpaces = ref([])
const selectedCourseSpaceId = ref(null)
const isOpenMode = ref(false)

const userInitial = computed(() => {
  const info = userStore.userInfo
  return (info?.name || info?.username || '我').charAt(0)
})

const capabilities = [
  { emoji: '❓', text: '解答概念问题' },
  { emoji: '⏱️', text: '分析算法复杂度' },
  { emoji: '💡', text: '最佳实践建议' },
  { emoji: '🔍', text: '解释代码思路' },
  { emoji: '🧩', text: '设计解决方案' }
]

const quickPrompts = [
  { emoji: '🔗', label: '链表 vs 数组', prompt: '请解释链表和数组的主要区别？' },
  { emoji: '🌲', label: '树的遍历', prompt: '二叉树的前序、中序和后序遍历有什么区别？' },
  { emoji: '📊', label: '复杂度分析', prompt: '如何判断一个算法的时间复杂度？' },
  { emoji: '⚡', label: '代码优化', prompt: '请帮我优化这段查找代码...' }
]

const formatMessage = (content) => {
  const rawHtml = marked(content)
  return DOMPurify.sanitize(rawHtml)
}

const fetchCourseSpaces = async () => {
  try {
    const token = localStorage.getItem('tap_token')
    if (!token) return
    const res = await fetch('http://localhost:8081/api/course-spaces', {
      headers: { 'Authorization': `Bearer ${token}` }
    })
    if (res.ok) {
      const data = await res.json()
      courseSpaces.value = Array.isArray(data) ? data : (data.data || [])
    }
  } catch (e) { console.warn('获取课程空间列表失败:', e) }
}

const sendMessage = async () => {
  const trimmedInput = userInput.value.trim()
  if (!trimmedInput || isTyping.value) return
  messages.value.push({ role: 'user', content: trimmedInput, time: new Date().toLocaleTimeString() })
  userInput.value = ''
  await scrollToBottom()
  isTyping.value = true
  try {
    const aiMessageIndex = messages.value.length
    messages.value.push({ role: 'ai', content: '', time: new Date().toLocaleTimeString() })
    const isRagMode = !!selectedCourseSpaceId.value
    const url = isRagMode ? 'http://localhost:8081/api/rag/chat' : 'http://localhost:8081/api/chat'
    const body = isRagMode
        ? { courseSpaceId: selectedCourseSpaceId.value, query: trimmedInput, mode: isOpenMode.value ? 'open' : 'strict' }
        : { userInput: trimmedInput }
    const headers = { 'Content-Type': 'application/json' }
    const token = localStorage.getItem('tap_token')
    if (token && isRagMode) headers['Authorization'] = `Bearer ${token}`
    const response = await fetch(url, { method: 'POST', headers, body: JSON.stringify(body) })
    if (!response.ok) throw new Error('服务器响应错误: ' + response.status)
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let streamDone = false
    while (!streamDone) {
      try {
        const { done, value } = await reader.read()
        streamDone = done
        if (done) break
        messages.value[aiMessageIndex].content += decoder.decode(value, { stream: true })
        await scrollToBottom()
      } catch (error) {
        console.error('读取流式响应时发生错误:', error)
        ElMessage.error('读取 AI 回复时发生错误')
        break
      }
    }
    if (isRagMode) {
      const fullContent = messages.value[aiMessageIndex].content
      const citationMatch = fullContent.match(/<!--CITATIONS:(.*?)-->/)
      if (citationMatch) {
        try { messages.value[aiMessageIndex].citations = JSON.parse(citationMatch[1]) } catch (e) {}
        messages.value[aiMessageIndex].content = fullContent.replace(/\n?\n?<!--CITATIONS:.*?-->/, '')
      }
    }
  } catch (error) {
    console.error('获取AI回复失败:', error)
    ElMessage.error('获取AI回复失败，请稍后再试')
    const last = messages.value[messages.value.length - 1]
    if (last && last.role === 'ai' && !last.content) last.content = '抱歉，服务器响应出现问题，请稍后再试。'
    else messages.value.push({ role: 'ai', content: '抱歉，服务器响应出现问题，请稍后再试。', time: new Date().toLocaleTimeString() })
  } finally {
    isTyping.value = false
    await scrollToBottom()
  }
}

const useQuickPrompt = (prompt) => {
  userInput.value = prompt
  nextTick(() => { document.querySelector('.chat-input-area textarea')?.focus() })
}

const scrollToBottom = async () => {
  await nextTick()
  if (chatContainer.value) chatContainer.value.scrollTop = chatContainer.value.scrollHeight
}

const submitFeedback = async (message, value) => {
  if (!message.qaLogId) return
  try {
    const token = localStorage.getItem('tap_token')
    await fetch('http://localhost:8081/api/rag/feedback', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
      body: JSON.stringify({ qaLogId: message.qaLogId, feedback: value })
    })
    message.feedback = value
    ElMessage.success(value === 1 ? '感谢您的肯定！' : '感谢反馈，我们会改进！')
  } catch (e) { ElMessage.error('反馈提交失败') }
}

onMounted(() => {
  fetchCourseSpaces()
  messages.value.push({
    role: 'ai',
    content: '# 你好，我是数据结构AI助手！👋\n\n我将帮助你解决数据结构学习中的各种问题。你可以向我咨询：\n\n- 数据结构基本概念\n- 算法时间复杂度和空间复杂度分析\n- 数据结构实现方法和技巧\n- 代码问题的解决方案\n\n快来向我提问吧！你也可以使用左侧的快速提问按钮。',
    time: new Date().toLocaleTimeString()
  })
})
</script>

<style scoped>
.ai-assistant-container { height: 100%; display: flex; flex-direction: column; background: #f0f2f5; }
.ai-hero-banner {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 18px 28px; border-radius: 0 0 20px 20px;
  box-shadow: 0 4px 20px rgba(102, 126, 234, 0.3);
}
.hero-content { display: flex; align-items: center; gap: 14px; }
.hero-icon-wrap { flex-shrink: 0; filter: drop-shadow(0 2px 8px rgba(0,0,0,0.2)); }
.hero-title { margin: 0; font-size: 20px; font-weight: 700; color: #fff; letter-spacing: 0.5px; }
.hero-desc { margin: 2px 0 0; font-size: 13px; color: rgba(255,255,255,0.85); }
.ai-body { display: flex; gap: 16px; flex: 1; padding: 16px; overflow: hidden; }
.ai-sidebar {
  width: 260px; flex-shrink: 0; display: flex; flex-direction: column; gap: 12px; overflow-y: auto;
}
.sidebar-section {
  background: #fff; border-radius: 14px; padding: 14px;
  border: 1px solid #e8eaed; box-shadow: 0 1px 3px rgba(0,0,0,0.04);
}
.section-title { font-size: 13px; font-weight: 600; color: #202124; margin-bottom: 10px; display: flex; align-items: center; gap: 6px; }
.section-icon { font-size: 16px; }
.cap-grid { display: flex; flex-direction: column; gap: 6px; }
.cap-item { display: flex; align-items: center; gap: 8px; font-size: 12px; color: #5f6368; padding: 4px 0; }
.cap-emoji { font-size: 16px; }
.quick-grid { display: flex; flex-direction: column; gap: 6px; }
.quick-btn {
  display: flex; align-items: center; gap: 8px; padding: 8px 10px;
  border: 1px solid #e8eaed; border-radius: 10px; background: #f8f9fa;
  font-size: 12px; color: #202124; cursor: pointer; text-align: left; transition: all 0.2s;
}
.quick-btn:hover { background: #e8f0fe; border-color: #c2d7f7; transform: translateX(2px); }
.quick-emoji { font-size: 15px; }
.mode-row { display: flex; align-items: center; gap: 8px; margin-top: 8px; }
.mode-hint { font-size: 11px; color: #9aa0a6; }
.chat-panel {
  flex: 1; display: flex; flex-direction: column;
  background: #fff; border-radius: 16px; border: 1px solid #e8eaed;
  box-shadow: 0 1px 3px rgba(0,0,0,0.04); overflow: hidden;
}
.chat-messages { flex: 1; overflow-y: auto; padding: 20px; background: #fafbfc; }
.empty-chat { display: flex; flex-direction: column; align-items: center; justify-content: center; height: 100%; color: #9aa0a6; }
.empty-icon { font-size: 48px; margin-bottom: 8px; opacity: 0.5; }
.msg-row { display: flex; gap: 10px; margin-bottom: 16px; align-items: flex-start; }
.msg-user { flex-direction: row-reverse; }
.msg-avatar { flex-shrink: 0; }
.avatar-ai {
  width: 36px; height: 36px; border-radius: 12px; display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #667eea, #764ba2); color: #fff; font-size: 13px; font-weight: 700;
  box-shadow: 0 2px 8px rgba(102, 126, 234, 0.3);
}
.avatar-user {
  width: 36px; height: 36px; border-radius: 12px; display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #1a73e8, #4285f4); color: #fff; font-size: 14px; font-weight: 600;
}
.msg-bubble { max-width: 75%; }
.msg-user .msg-bubble {
  background: linear-gradient(135deg, #e8f0fe, #d2e3fc); border-radius: 16px 4px 16px 16px; padding: 10px 14px;
}
.msg-ai .msg-bubble {
  background: #fff; border: 1px solid #e8eaed; border-radius: 4px 16px 16px 16px; padding: 10px 14px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.04);
}
.msg-meta { display: flex; justify-content: space-between; margin-bottom: 4px; }
.msg-sender { font-size: 12px; font-weight: 600; color: #202124; }
.msg-user .msg-sender { color: #1a73e8; }
.msg-time { font-size: 11px; color: #9aa0a6; }
.msg-body { font-size: 14px; color: #202124; line-height: 1.6; word-break: break-word; }
.msg-body :deep(pre) { background: #f6f8fa; padding: 10px; border-radius: 8px; overflow-x: auto; margin: 8px 0; font-size: 13px; }
.msg-body :deep(code) { background: #f0f2f5; padding: 2px 5px; border-radius: 4px; font-family: 'Fira Code', monospace; font-size: 13px; }
.msg-body :deep(h1), .msg-body :deep(h2), .msg-body :deep(h3) { margin: 8px 0 4px; }
.msg-body :deep(ul), .msg-body :deep(ol) { padding-left: 20px; margin: 4px 0; }
.citations-area { margin-top: 8px; border-top: 1px dashed #e4e7ed; padding-top: 6px; }
.citations-area :deep(.el-collapse-item__header) { font-size: 12px; color: #606266; height: 28px; line-height: 28px; }
.citation-item { font-size: 12px; color: #606266; padding: 3px 0; line-height: 1.5; }
.web-link { margin-left: 4px; font-size: 11px; color: #409eff; word-break: break-all; text-decoration: underline; }
.feedback-row { margin-top: 6px; display: flex; align-items: center; gap: 6px; }
.feedback-hint { font-size: 11px; color: #9aa0a6; }
.fb-btn {
  border: none; background: #f1f3f4; border-radius: 50%; width: 28px; height: 28px;
  cursor: pointer; font-size: 14px; display: flex; align-items: center; justify-content: center; transition: all 0.2s;
}
.fb-btn:hover { background: #e8eaed; transform: scale(1.1); }
.fb-active-good { background: #e6f4ea; }
.fb-active-bad { background: #fce8e6; }
.typing-dots { display: flex; gap: 5px; padding: 4px 0; }
.typing-dots span {
  width: 8px; height: 8px; border-radius: 50%; display: inline-block;
  background: linear-gradient(135deg, #667eea, #764ba2);
  animation: typingAnim 1.4s infinite ease-in-out both;
}
.typing-dots span:nth-child(2) { animation-delay: 0.2s; }
.typing-dots span:nth-child(3) { animation-delay: 0.4s; }
@keyframes typingAnim {
  0%, 80%, 100% { transform: scale(0.5); opacity: 0.3; }
  40% { transform: scale(1); opacity: 1; }
}
.chat-input-area { padding: 12px 16px; border-top: 1px solid #e8eaed; background: #fff; }
.input-footer { display: flex; justify-content: space-between; align-items: center; margin-top: 8px; }
.input-tip { font-size: 11px; color: #9aa0a6; }
.send-btn { border-radius: 10px; padding: 8px 24px; }
@media (max-width: 1100px) {
  .ai-body { flex-direction: column; }
  .ai-sidebar { width: 100%; flex-direction: row; overflow-x: auto; gap: 8px; }
  .sidebar-section { min-width: 200px; }
}
</style>