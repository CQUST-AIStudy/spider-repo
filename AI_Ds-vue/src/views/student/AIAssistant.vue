<template>
  <div class="ai-assistant-container">
    <page-header
        class="my-page-header"
        title="AI 学习助手"
        description="数据结构精准辅导，解决您的学习难题"
    />

    <div class="ai-container">
      <div class="ai-sidebar">
        <div class="ai-intro">
          <el-avatar :size="80" class="ai-avatar">AI</el-avatar>
          <h3>数据结构 AI 助手</h3>
          <p>专为数据结构课程定制的人工智能，帮助您解决各种数据结构学习问题</p>
        </div>

        <div class="ai-capabilities">
          <h4>AI 助手可以：</h4>
          <ul class="capability-list">
            <li>
              <el-icon><QuestionFilled /></el-icon>
              <span>解答数据结构概念问题</span>
            </li>
            <li>
              <el-icon><DocumentChecked /></el-icon>
              <span>帮助分析算法复杂度</span>
            </li>
            <li>
              <el-icon><Histogram /></el-icon>
              <span>提供最佳实践建议</span>
            </li>
            <li>
              <el-icon><Document /></el-icon>
              <span>解释代码实现思路</span>
            </li>
            <li>
              <el-icon><Cpu /></el-icon>
              <span>设计数据结构解决方案</span>
            </li>
          </ul>
        </div>

        <div class="quick-prompts">
          <h4>快速提问：</h4>
          <div class="prompt-buttons">
            <el-button @click="useQuickPrompt('请解释链表和数组的主要区别？')">链表和数组的区别</el-button>
            <el-button @click="useQuickPrompt('二叉树的前序、中序和后序遍历有什么区别？')">树的遍历方式</el-button>
            <el-button @click="useQuickPrompt('如何判断一个算法的时间复杂度？')">算法复杂度分析</el-button>
            <el-button @click="useQuickPrompt('请帮我优化这段查找代码...')">代码优化建议</el-button>
          </div>
        </div>
      </div>

      <div class="chat-container">
        <div class="course-space-selector">
          <el-select v-model="selectedCourseSpaceId" placeholder="选择课程空间（可选）" clearable style="width: 280px">
            <el-option v-for="cs in courseSpaces" :key="cs.id" :label="cs.name" :value="cs.id" />
          </el-select>
          <el-switch v-if="selectedCourseSpaceId" v-model="isOpenMode" active-text="开放" inactive-text="严格"
                     style="margin-left: 12px" />
          <span class="selector-hint" v-if="selectedCourseSpaceId">📚 RAG模式：基于课程资料回答</span>
          <span class="selector-hint" v-else>💬 纯对话模式</span>
        </div>
        <div class="chat-messages" ref="chatContainer">
          <div v-if="messages.length === 0" class="welcome-message">
            <el-empty description="开始和AI助手对话吧！">
              <template #image>
                <el-icon class="welcome-icon"><ChatDotRound /></el-icon>
              </template>
            </el-empty>
          </div>

          <div v-for="(message, index) in messages" :key="index" :class="['message', message.role === 'user' ? 'user-message' : 'ai-message']">
            <div class="message-avatar">
              <el-avatar :size="36" :src="message.role === 'user' ? userAvatar : aiAvatar"></el-avatar>
            </div>
            <div class="message-content">
              <div class="message-header">
                <span class="message-sender">{{ message.role === 'user' ? '我' : 'AI助手' }}</span>
                <span class="message-time">{{ message.time }}</span>
              </div>
              <div class="message-text" v-html="formatMessage(message.content)"></div>
              <div v-if="message.citations && message.citations.length > 0" class="citations-area">
                <el-collapse>
                  <el-collapse-item title="📖 引用来源">
                    <div v-for="cite in message.citations" :key="cite.index" class="citation-item">
                      <template v-if="cite.source === 'web'">
                        <el-tag size="small" type="warning" style="margin-right: 6px">联网依据</el-tag>
                        [{{ cite.index }}] {{ cite.docName || cite.title || '网络来源' }}
                        <a v-if="cite.url" :href="cite.url" target="_blank" rel="noopener" class="web-link">{{ cite.url }}</a>
                        <el-tag size="small" type="info" style="margin-left: 8px">相似度: {{ (cite.score * 100).toFixed(0) }}%</el-tag>
                      </template>
                      <template v-else>
                        <el-tag size="small" type="success" style="margin-right: 6px">课程依据</el-tag>
                        [{{ cite.index }}] 《{{ cite.docName }}》{{ cite.chapterPath }}
                        <span v-if="cite.pageRange">(第{{ cite.pageRange }}页)</span>
                        <el-tag size="small" type="info" style="margin-left: 8px">相似度: {{ (cite.score * 100).toFixed(0) }}%</el-tag>
                      </template>
                    </div>
                  </el-collapse-item>
                </el-collapse>
              </div>
              <div v-if="message.role === 'ai' && message.qaLogId" class="feedback-area">
                <span style="font-size: 12px; color: #909399; margin-right: 8px">这个回答有帮助吗？</span>
                <el-button :type="message.feedback === 1 ? 'success' : ''" size="small" circle
                           @click="submitFeedback(message, 1)">👍</el-button>
                <el-button :type="message.feedback === -1 ? 'danger' : ''" size="small" circle
                           @click="submitFeedback(message, -1)">👎</el-button>
              </div>
            </div>
          </div>

          <div v-if="isTyping" class="message ai-message typing-indicator">
            <div class="message-avatar">
              <el-avatar :size="36" :src="aiAvatar"></el-avatar>
            </div>
            <div class="message-content">
              <div class="typing-dots">
                <span></span>
                <span></span>
                <span></span>
              </div>
            </div>
          </div>
        </div>

        <div class="chat-input">
          <el-input
              v-model="userInput"
              type="textarea"
              :rows="3"
              placeholder="输入您的问题，例如：如何实现一个平衡二叉树？"
              resize="none"
              @keyup.enter.ctrl="sendMessage"
          />

          <div class="input-actions">
            <span class="input-tip">按Ctrl+Enter发送</span>
            <el-button
                type="primary"
                :disabled="!userInput.trim() || isTyping"
                @click="sendMessage"
                :loading="isTyping"
            >
              发送
            </el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, computed } from 'vue'
import PageHeader from '../../components/PageHeader.vue'
import { useUserStore } from '../../store'
import { ElMessage } from 'element-plus'
import {
  QuestionFilled,
  DocumentChecked,
  Histogram,
  Document, // 使用 Document 代替 CodeSquare
  Cpu,
  ChatDotRound
} from '@element-plus/icons-vue'
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

// 用户头像
const userAvatar = computed(() => userStore.userInfo?.avatar || 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png')

// AI头像
const aiAvatar = 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png'

// 格式化消息（支持Markdown和代码高亮）
const formatMessage = (content) => {
  // 转义HTML并渲染Markdown
  const rawHtml = marked(content)
  return DOMPurify.sanitize(rawHtml)
}

// 获取课程空间列表
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
  } catch (e) {
    console.warn('获取课程空间列表失败:', e)
  }
}

// 发送消息
const sendMessage = async () => {
  const trimmedInput = userInput.value.trim()
  if (!trimmedInput || isTyping.value) return

  // 添加用户消息
  messages.value.push({
    role: 'user',
    content: trimmedInput,
    time: new Date().toLocaleTimeString()
  })

  // 清空输入框
  userInput.value = ''

  // 滚动到底部
  await scrollToBottom()

  // 显示AI正在输入
  isTyping.value = true

  try {
    console.log('发送流式请求到后端：', trimmedInput)

    // 先添加一个空的AI回复消息
    const aiMessageIndex = messages.value.length
    messages.value.push({
      role: 'ai',
      content: '',
      time: new Date().toLocaleTimeString()
    })

    // 使用fetch API发送POST请求并处理流式响应
    const isRagMode = !!selectedCourseSpaceId.value
    const url = isRagMode ? 'http://localhost:8081/api/rag/chat' : 'http://localhost:8081/api/chat'
    const body = isRagMode
        ? { courseSpaceId: selectedCourseSpaceId.value, query: trimmedInput, mode: isOpenMode.value ? 'open' : 'strict' }
        : { userInput: trimmedInput }
    const headers = { 'Content-Type': 'application/json' }
    const token = localStorage.getItem('tap_token')
    if (token && isRagMode) {
      headers['Authorization'] = `Bearer ${token}`
    }

    const response = await fetch(url, {
      method: 'POST',
      headers,
      body: JSON.stringify(body)
    })

    if (!response.ok) {
      throw new Error(`服务器响应错误: ${response.status}`)
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder()

    // 处理流式响应
    let streamDone = false
    while (!streamDone) {
      try {
        const {done, value} = await reader.read()
        streamDone = done
        if (done) {
          console.log('流式响应完成')
          break
        }
        const chunk = decoder.decode(value, {stream: true})
        console.log('收到数据块:', chunk)
        messages.value[aiMessageIndex].content += chunk
        await scrollToBottom()
      } catch (error) {
        console.error('读取流式响应时发生错误:', error)
        ElMessage.error('读取 AI 回复时发生错误，请稍后再试')
        break
      }
    }

    // 流结束后解析引用来源
    if (isRagMode) {
      const fullContent = messages.value[aiMessageIndex].content
      const citationMatch = fullContent.match(/<!--CITATIONS:(.*?)-->/)
      if (citationMatch) {
        try {
          const citations = JSON.parse(citationMatch[1])
          messages.value[aiMessageIndex].citations = citations
        } catch (e) {
          console.warn('引用来源解析失败:', e)
        }
        messages.value[aiMessageIndex].content = fullContent.replace(/\n?\n?<!--CITATIONS:.*?-->/, '')
      }
    }
  } catch (error) {
    console.error('获取AI回复失败:', error)
    ElMessage.error('获取AI回复失败，请稍后再试')

    // 如果已经添加了空的AI回复消息
    const lastAiMessage = messages.value[messages.value.length - 1]
    if (lastAiMessage && lastAiMessage.role === 'ai' && !lastAiMessage.content) {
      lastAiMessage.content = '抱歉，服务器响应出现问题，请稍后再试。'
    } else {
      // 添加错误消息
      messages.value.push({
        role: 'ai',
        content: '抱歉，服务器响应出现问题，请稍后再试。',
        time: new Date().toLocaleTimeString()
      })
    }
  } finally {
    isTyping.value = false
    // 滚动到底部
    await scrollToBottom()
  }
}

// 快速提问
const useQuickPrompt = (prompt) => {
  userInput.value = prompt
  // 聚焦到输入框，让用户可以修改
  nextTick(() => {
    document.querySelector('.chat-input textarea').focus()
  })
}

// 滚动到底部
const scrollToBottom = async () => {
  await nextTick()
  if (chatContainer.value) {
    chatContainer.value.scrollTop = chatContainer.value.scrollHeight
  }
}

// 提交反馈
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
  } catch (e) {
    ElMessage.error('反馈提交失败')
  }
}

onMounted(() => {
  // 获取课程空间列表
  fetchCourseSpaces()

  // 添加欢迎消息
  messages.value.push({
    role: 'ai',
    content: `# 你好，我是数据结构AI助手！👋

我将帮助你解决数据结构学习中的各种问题。你可以向我咨询：

- 数据结构基本概念
- 算法时间复杂度和空间复杂度分析
- 数据结构实现方法和技巧
- 代码问题的解决方案

快来向我提问吧！你也可以使用左侧的快速提问按钮。`,
    time: new Date().toLocaleTimeString()
  })
})
</script>

<style scoped>
.my-page-header {
  padding: 20px;
}

.ai-assistant-container {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.ai-container {
  display: flex;
  gap: 20px;
  height: calc(100% - 80px);
}

.ai-sidebar {
  width: 300px;
  background-color: #fff;
  border-radius: 16px;
  border: 1px solid #dadce0;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 20px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.04);
}

.ai-intro {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.ai-avatar {
  background: #1a73e8;
  color: white;
  font-weight: bold;
  font-size: 28px;
  margin-bottom: 15px;
}

.ai-intro h3 {
  margin: 10px 0;
  color: #202124;
}

.ai-intro p {
  color: #5f6368;
  font-size: 14px;
  line-height: 1.5;
}

.ai-capabilities h4 {
  color: #202124;
  margin-bottom: 10px;
}

.capability-list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.capability-list li {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 0;
  color: #5f6368;
}

.capability-list li .el-icon {
  color: #1a73e8;
}

.quick-prompts h4 {
  color: #202124;
  margin-bottom: 10px;
}

.prompt-buttons {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.chat-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  border: 1px solid #dadce0;
  border-radius: 16px;
  overflow: hidden;
  background: #fff;
  box-shadow: 0 1px 3px rgba(0,0,0,0.04);
}

.course-space-selector {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 15px;
  background-color: #f8f9fa;
  border-bottom: 1px solid #dadce0;
}

.selector-hint {
  font-size: 13px;
  color: #9aa0a6;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background-color: #f8f9fa;
}

.welcome-message {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100%;
}

.welcome-image {
  max-width: 200px;
}

.message {
  display: flex;
  margin-bottom: 20px;
  gap: 12px;
}

.user-message {
  flex-direction: row-reverse;
}

.message-avatar {
  flex-shrink: 0;
}

.message-content {
  padding: 10px 15px;
  border-radius: 10px;
  max-width: 80%;
}

.user-message .message-content {
  background: #e8f0fe;
  border-top-right-radius: 0;
}

.ai-message .message-content {
  background-color: white;
  border-top-left-radius: 0;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
}

.message-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 5px;
}

.message-sender {
  font-weight: 500;
  font-size: 14px;
  color: #202124;
}

.user-message .message-sender {
  color: #1a73e8;
}

.ai-message .message-sender {
  color: #1a73e8;
}

.message-time {
  color: #9aa0a6;
  font-size: 12px;
}

.message-text {
  padding: 10px;
  color: #202124;
  line-height: 1.5;
  word-break: break-word;
}

.message-text :deep(pre) {
  background-color: #f6f8fa;
  padding: 10px;
  border-radius: 5px;
  overflow-x: auto;
  margin: 10px 0;
}

.message-text :deep(code) {
  background-color: #f6f8fa;
  padding: 2px 4px;
  border-radius: 3px;
  font-family: monospace;
}

.typing-indicator {
  opacity: 0.7;
}

.typing-dots {
  display: flex;
  gap: 5px;
}

.typing-dots span {
  width: 10px;
  height: 10px;
  background-color: #1a73e8;
  border-radius: 50%;
  display: inline-block;
  animation: typingAnimation 1.4s infinite ease-in-out both;
}

.typing-dots span:nth-child(1) {
  animation-delay: 0s;
}

.typing-dots span:nth-child(2) {
  animation-delay: 0.2s;
}

.typing-dots span:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes typingAnimation {
  0%, 80%, 100% {
    transform: scale(0.6);
    opacity: 0.4;
  }
  40% {
    transform: scale(1);
    opacity: 1;
  }
}

.chat-input {
  padding: 15px;
  border-top: 1px solid #dadce0;
  background-color: white;
}

.input-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 10px;
}

.input-tip {
  color: #9aa0a6;
  font-size: 12px;
}

.citations-area {
  margin-top: 10px;
  border-top: 1px dashed #e4e7ed;
  padding-top: 8px;
}

.citations-area :deep(.el-collapse-item__header) {
  font-size: 13px;
  color: #606266;
  height: 32px;
  line-height: 32px;
}

.citation-item {
  font-size: 13px;
  color: #606266;
  padding: 4px 0;
  line-height: 1.6;
}

.web-link {
  display: inline-block;
  margin-left: 6px;
  font-size: 12px;
  color: #409eff;
  word-break: break-all;
  text-decoration: underline;
}

.feedback-area {
  margin-top: 8px;
  display: flex;
  align-items: center;
}

/* 响应式调整 */
@media (max-width: 1200px) {
  .ai-container {
    flex-direction: column;
  }

  .ai-sidebar {
    width: 100%;
    max-height: 180px;
    overflow-y: auto;
  }

  .ai-intro {
    flex-direction: row;
    text-align: left;
    gap: 15px;
  }

  .ai-capabilities, .capability-list {
    display: none;
  }

  .prompt-buttons {
    flex-direction: row;
    flex-wrap: wrap;
  }

  .chat-container {
    height: calc(100% - 180px);
  }
}
</style>