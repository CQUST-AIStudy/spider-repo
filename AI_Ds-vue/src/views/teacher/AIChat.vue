<template>
  <div class="chat-page">
    <!-- 空状态 -->
    <div v-if="messages.length === 0" class="empty-state">
      <div class="empty-glow">✨</div>
      <h2>AI 学术助手</h2>
      <p class="empty-desc">搜索论文、解读文献、学术问答，支持 arXiv 检索</p>
      <div class="quick-tags">
        <span v-for="s in suggestions" :key="s" class="quick-tag" @click="input = s">{{ s }}</span>
      </div>
    </div>

    <!-- 消息列表 -->
    <div v-else class="msg-list" ref="messagesRef">
      <div v-for="(msg, i) in messages" :key="i" :class="['msg-row', msg.role]">
        <div class="msg-avatar">{{ msg.role === 'assistant' ? '🤖' : '👤' }}</div>
        <div class="msg-bubble">
          <p v-if="msg.role === 'user'" class="user-text">{{ msg.content }}</p>
          <div v-else class="md-body" v-html="renderMd(msg.content)"></div>
          <!-- 论文卡片 -->
          <div v-if="msg.papers && msg.papers.length" class="papers-area">
            <p class="papers-label">📄 相关论文</p>
            <a v-for="(p, j) in msg.papers" :key="j" :href="p.link" target="_blank" class="paper-chip">
              <div class="paper-main">
                <span class="paper-t">{{ p.title }}</span>
                <span class="paper-a">{{ p.authors }}</span>
              </div>
              <span class="paper-arrow">↗</span>
            </a>
          </div>
        </div>
      </div>
      <div v-if="loading" class="msg-row assistant">
        <div class="msg-avatar">🤖</div>
        <div class="msg-bubble typing">
          <span class="dot"></span><span class="dot"></span><span class="dot"></span>
        </div>
      </div>
    </div>

    <!-- 输入 -->
    <div class="input-bar">
      <div class="input-inner">
        <el-input v-model="input" type="textarea" :rows="1"
          :autosize="{ minRows: 1, maxRows: 4 }"
          placeholder="输入问题，如「帮我找 transformer 相关论文」..."
          @keydown="onKeyDown" />
        <el-button type="primary" :icon="Promotion" :loading="loading"
          :disabled="!input.trim()" circle @click="send" />
      </div>
      <p class="input-hint">AI 回答仅供参考 · Enter 发送，Shift+Enter 换行</p>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted, watch } from 'vue'
import { Promotion } from '@element-plus/icons-vue'
import { chatSend } from '../../api/tap'
import MarkdownIt from 'markdown-it'

const md = new MarkdownIt({ html: true, linkify: true, typographer: true })
const renderMd = (text) => md.render(text || '')

const CHAT_STORAGE_KEY = 'ai_chat_messages'
const loadMessages = () => {
  try { return JSON.parse(sessionStorage.getItem(CHAT_STORAGE_KEY) || '[]') } catch { return [] }
}
const messages = ref(loadMessages())
watch(messages, (v) => {
  try { sessionStorage.setItem(CHAT_STORAGE_KEY, JSON.stringify(v.slice(-50))) } catch (e) { /* ignore */ }
}, { deep: true })

const input = ref('')
const loading = ref(false)
const messagesRef = ref(null)

const suggestions = [
  '帮我找关于 transformer 的最新论文',
  '搜索 large language model 相关研究',
  '什么是 attention mechanism？',
  '帮我找知识蒸馏相关的论文'
]

const scrollToBottom = () => {
  nextTick(() => { if (messagesRef.value) messagesRef.value.scrollTop = messagesRef.value.scrollHeight })
}

onMounted(() => { if (messages.value.length) scrollToBottom() })

const send = async () => {
  const msg = input.value.trim()
  if (!msg || loading.value) return
  input.value = ''
  messages.value.push({ role: 'user', content: msg })
  scrollToBottom(); loading.value = true
  try {
    const history = messages.value.slice(-10).map(m => ({ role: m.role, content: m.content }))
    const res = await chatSend(msg, history)
    const data = res?.data ?? res
    messages.value.push({ role: 'assistant', content: data.reply ?? '无响应', papers: data.papers?.length ? data.papers : undefined })
  } catch (e) { messages.value.push({ role: 'assistant', content: '⚠️ ' + e.message }) }
  loading.value = false; scrollToBottom()
}

const onKeyDown = (e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); send() } }
</script>

<style scoped>
.chat-page { display: flex; flex-direction: column; height: calc(100vh - 180px); font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; }

/* 空状态 */
.empty-state {
  flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center;
}
.empty-glow { font-size: 56px; margin-bottom: 12px; animation: pulse 2s infinite; }
@keyframes pulse { 0%,100% { transform: scale(1); } 50% { transform: scale(1.1); } }
.empty-state h2 { margin: 0 0 6px; font-size: 24px; font-weight: 400; color: #202124; }
.empty-desc { color: #5f6368; font-size: 14px; margin: 0 0 28px; }
.quick-tags { display: flex; flex-wrap: wrap; gap: 10px; justify-content: center; max-width: 600px; }
.quick-tag {
  padding: 10px 18px; border-radius: 100px; font-size: 13px;
  background: #f1f3f4; color: #5f6368; cursor: pointer; transition: all .2s;
  border: 1px solid #dadce0;
}
.quick-tag:hover { background: #e8f0fe; color: #1a73e8; border-color: #1a73e8; }

/* 消息列表 */
.msg-list { flex: 1; overflow-y: auto; padding: 20px; }
.msg-row { display: flex; gap: 12px; margin-bottom: 20px; }
.msg-row.user { flex-direction: row-reverse; }
.msg-avatar {
  width: 36px; height: 36px; border-radius: 50%; display: flex;
  align-items: center; justify-content: center; font-size: 18px;
  flex-shrink: 0; background: #f1f3f4;
}
.msg-bubble {
  max-width: 75%; padding: 14px 18px; border-radius: 16px;
  font-size: 14px; line-height: 1.7;
}
.msg-row.user .msg-bubble {
  background: #1a73e8; color: #fff; border-bottom-right-radius: 4px;
}
.msg-row.assistant .msg-bubble {
  background: #fff; border: 1px solid #dadce0; border-bottom-left-radius: 4px;
}
.user-text { margin: 0; white-space: pre-wrap; }

/* Typing dots */
.typing { display: flex; gap: 4px; align-items: center; padding: 14px 20px; }
.dot {
  width: 8px; height: 8px; border-radius: 50%; background: #dadce0;
  animation: bounce .6s infinite alternate;
}
.dot:nth-child(2) { animation-delay: .2s; }
.dot:nth-child(3) { animation-delay: .4s; }
@keyframes bounce { to { transform: translateY(-6px); opacity: .4; } }

/* Markdown */
.md-body { font-size: 14px; line-height: 1.8; }
.md-body :deep(h1), .md-body :deep(h2), .md-body :deep(h3) { margin-top: 12px; margin-bottom: 6px; color: #202124; }
.md-body :deep(p) { margin: 6px 0; }
.md-body :deep(code) { background: #e8eaed; padding: 2px 6px; border-radius: 4px; font-size: 13px; }
.md-body :deep(pre) { background: #1e1e2e; color: #cdd6f4; padding: 14px; border-radius: 12px; overflow-x: auto; }
.md-body :deep(pre code) { background: none; padding: 0; color: inherit; }
.md-body :deep(a) { color: #1a73e8; }
.md-body :deep(table) { border-collapse: collapse; width: 100%; }
.md-body :deep(th), .md-body :deep(td) { border: 1px solid #dadce0; padding: 6px 10px; }

/* 论文卡片 */
.papers-area { margin-top: 14px; padding-top: 14px; border-top: 1px solid #e8eaed; }
.papers-label { font-size: 12px; color: #5f6368; margin: 0 0 8px; }
.paper-chip {
  display: flex; align-items: center; gap: 8px;
  padding: 10px 14px; border-radius: 12px; background: #f8f9fa;
  margin-bottom: 6px; text-decoration: none; color: inherit; transition: background .2s;
  border: 1px solid #e8eaed;
}
.paper-chip:hover { background: #e8f0fe; border-color: #1a73e8; }
.paper-main { flex: 1; min-width: 0; }
.paper-t { display: block; font-size: 13px; font-weight: 500; color: #202124; }
.paper-chip:hover .paper-t { color: #1a73e8; }
.paper-a { display: block; font-size: 12px; color: #5f6368; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.paper-arrow { color: #9aa0a6; font-size: 14px; flex-shrink: 0; }

/* 输入 */
.input-bar { border-top: 1px solid #dadce0; padding: 14px 20px; background: #fff; }
.input-inner {
  display: flex; gap: 10px; align-items: flex-end;
  max-width: 800px; margin: 0 auto;
}
.input-inner :deep(.el-textarea) { flex: 1; }
.input-inner :deep(.el-textarea__inner) { border-radius: 100px; padding: 10px 18px; border-color: #dadce0; }
.input-inner :deep(.el-textarea__inner:focus) { border-color: #1a73e8; box-shadow: 0 0 0 1px #1a73e8; }
.input-hint { text-align: center; font-size: 11px; color: #9aa0a6; margin: 8px 0 0; }
</style>
