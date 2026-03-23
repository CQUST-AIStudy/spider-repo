<template>
  <div class="chat-page">
    <section class="hero-panel">
      <div class="hero-copy">
        <span class="hero-badge">Teacher Copilot</span>
        <h1>AI 教学对话助手</h1>
        <p>
          支持教学问答、论文检索、课程设计讨论。切换到系统其他页面后，请求会继续执行，返回本页时会自动显示结果。
        </p>
      </div>
      <div class="hero-meta">
        <div class="meta-card">
          <span class="meta-label">会话消息</span>
          <strong>{{ messages.length }}</strong>
        </div>
        <div class="meta-card">
          <span class="meta-label">当前状态</span>
          <strong>{{ loading ? 'AI 生成中' : '可继续提问' }}</strong>
        </div>
        <el-button
          class="clear-btn"
          plain
          :disabled="loading || !messages.length"
          @click="clearConversation"
        >
          清空会话
        </el-button>
      </div>
    </section>

    <section class="chat-shell">
      <div class="chat-topbar">
        <div class="topbar-title">
          <span class="topbar-dot" :class="{ active: loading }"></span>
          <div>
            <h2>教师端 AI 对话</h2>
            <p>{{ loading ? '正在生成回答，离开页面后也会继续处理。' : '可直接追问、续问或粘贴教学材料。' }}</p>
          </div>
        </div>
        <div class="topbar-actions">
          <span v-if="messages.length" class="session-tag">已保存本次会话</span>
          <span v-if="lastAssistantPreview" class="session-preview">{{ lastAssistantPreview }}</span>
        </div>
      </div>

      <div ref="messagesRef" class="messages-panel">
        <template v-if="messages.length">
          <div
            v-for="(msg, index) in messages"
            :key="`${msg.role}-${msg.createdAt || index}`"
            :class="['message-row', msg.role]"
          >
            <div class="message-avatar">
              <span>{{ msg.role === 'assistant' ? 'AI' : '师' }}</span>
            </div>
            <div class="message-main">
              <div class="message-meta">
                <strong>{{ msg.role === 'assistant' ? '教学助手' : '教师' }}</strong>
                <span>{{ msg.role === 'assistant' ? '回答' : '提问' }}</span>
              </div>
              <div class="message-bubble">
                <p v-if="msg.role === 'user'" class="plain-text">{{ msg.content }}</p>
                <div v-else class="markdown-body" v-html="renderMarkdown(msg.content)"></div>

                <div v-if="msg.papers?.length" class="papers-grid">
                  <a
                    v-for="(paper, paperIndex) in msg.papers"
                    :key="`${paper.link || paper.title}-${paperIndex}`"
                    :href="paper.link"
                    target="_blank"
                    rel="noopener noreferrer"
                    class="paper-card"
                  >
                    <div class="paper-head">
                      <span class="paper-index">0{{ paperIndex + 1 }}</span>
                      <span class="paper-link">查看论文</span>
                    </div>
                    <strong>{{ paper.title }}</strong>
                    <p>{{ paper.authors || '作者信息暂缺' }}</p>
                  </a>
                </div>
              </div>
            </div>
          </div>

          <div v-if="loading" class="message-row assistant pending-row">
            <div class="message-avatar">
              <span>AI</span>
            </div>
            <div class="message-main">
              <div class="message-meta">
                <strong>教学助手</strong>
                <span>处理中</span>
              </div>
              <div class="message-bubble pending-bubble">
                <div class="typing-line">
                  <span class="typing-dot"></span>
                  <span class="typing-dot"></span>
                  <span class="typing-dot"></span>
                </div>
                <p>正在整理答案与参考资料…</p>
              </div>
            </div>
          </div>
        </template>

        <div v-else class="empty-panel">
          <div class="empty-orb"></div>
          <div class="empty-copy">
            <span class="empty-label">Suggested prompts</span>
            <h3>从一个具体教学问题开始</h3>
            <p>例如课程讲解、实验设计、知识点串讲、论文资料检索或答疑措辞优化。</p>
          </div>
          <div class="suggestion-grid">
            <button
              v-for="item in suggestions"
              :key="item"
              type="button"
              class="suggestion-card"
              @click="applySuggestion(item)"
            >
              {{ item }}
            </button>
          </div>
        </div>
      </div>

      <div class="composer-panel">
        <div class="quick-strip">
          <button
            v-for="item in suggestions"
            :key="`quick-${item}`"
            type="button"
            class="quick-chip"
            @click="applySuggestion(item)"
          >
            {{ item }}
          </button>
        </div>

        <div class="composer-box">
          <el-input
            v-model="draft"
            type="textarea"
            :rows="1"
            :autosize="{ minRows: 1, maxRows: 5 }"
            resize="none"
            placeholder="输入教学问题，例如：帮我给“图的遍历”设计一个 20 分钟课堂讲解结构。"
            @keydown="onKeyDown"
          />
          <div class="composer-actions">
            <span class="composer-hint">Enter 发送，Shift+Enter 换行</span>
            <el-button
              type="primary"
              round
              :loading="loading"
              :disabled="!draft.trim()"
              @click="send"
            >
              发送问题
            </el-button>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
import { ElMessageBox } from 'element-plus'
import { useTeacherAiChatStore } from '../../store/teacherAiChat'

const md = new MarkdownIt({
  html: false,
  linkify: true,
  typographer: true,
  breaks: true
})

const store = useTeacherAiChatStore()
const { messages, draft, loading } = storeToRefs(store)

const messagesRef = ref(null)

const suggestions = [
  '帮我设计一个“二叉树遍历”实验课的提问链路',
  '帮我生成“栈和队列”课堂讲解提纲',
  '帮我检索最近的 Transformer 教学应用论文',
  '帮我润色一段给学生的实验反馈'
]

const renderMarkdown = (text) => DOMPurify.sanitize(md.render(text || ''))

const lastAssistantPreview = computed(() => {
  const lastAssistant = [...messages.value].reverse().find(item => item.role === 'assistant' && item.content)
  if (!lastAssistant) return ''
  return lastAssistant.content.replace(/\s+/g, ' ').slice(0, 48)
})

function scrollToBottom() {
  nextTick(() => {
    if (!messagesRef.value) return
    messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  })
}

function applySuggestion(text) {
  store.setDraft(text)
}

async function clearConversation() {
  try {
    await ElMessageBox.confirm('清空当前 AI 对话记录？', '提示', {
      confirmButtonText: '清空',
      cancelButtonText: '取消',
      type: 'warning'
    })
    store.clearMessages()
  } catch (error) {
    // User cancelled the confirmation dialog.
  }
}

async function send() {
  await store.sendMessage(draft.value)
}

function onKeyDown(event) {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    send()
  }
}

watch(() => [messages.value.length, loading.value], scrollToBottom, { immediate: true })
onMounted(scrollToBottom)
</script>

<style scoped>
.chat-page {
  min-height: calc(100vh - 180px);
  display: flex;
  flex-direction: column;
  gap: 18px;
  color: #1f2937;
}

.hero-panel {
  position: relative;
  overflow: hidden;
  display: grid;
  grid-template-columns: minmax(0, 1.8fr) minmax(280px, 1fr);
  gap: 18px;
  padding: 28px 32px;
  border-radius: 28px;
  background:
    radial-gradient(circle at top left, rgba(255, 255, 255, 0.72), rgba(255, 255, 255, 0) 36%),
    linear-gradient(135deg, #f7f3ea 0%, #ecf7f4 48%, #eef4ff 100%);
  border: 1px solid rgba(112, 136, 173, 0.22);
  box-shadow: 0 16px 40px rgba(68, 95, 130, 0.12);
}

.hero-panel::after {
  content: '';
  position: absolute;
  right: -56px;
  top: -48px;
  width: 180px;
  height: 180px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(60, 125, 255, 0.18), rgba(60, 125, 255, 0));
}

.hero-copy {
  position: relative;
  z-index: 1;
  max-width: 720px;
}

.hero-badge {
  display: inline-flex;
  align-items: center;
  padding: 6px 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.72);
  color: #3056a1;
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.hero-copy h1 {
  margin: 14px 0 10px;
  font-size: 34px;
  line-height: 1.15;
  font-family: 'ZiYouLangManTi', 'Microsoft YaHei', sans-serif;
  letter-spacing: 1px;
  color: #173153;
}

.hero-copy p {
  margin: 0;
  max-width: 640px;
  font-size: 14px;
  line-height: 1.75;
  color: #4b5b74;
}

.hero-meta {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  align-content: start;
  gap: 14px;
}

.meta-card,
.clear-btn {
  min-height: 88px;
  border-radius: 20px;
  border: 1px solid rgba(104, 124, 155, 0.18);
  background: rgba(255, 255, 255, 0.74);
  backdrop-filter: blur(12px);
}

.meta-card {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 16px 18px;
}

.meta-label {
  font-size: 12px;
  color: #6a7792;
}

.meta-card strong {
  margin-top: 8px;
  font-size: 22px;
  color: #183153;
}

.clear-btn {
  grid-column: span 2;
  color: #38517c;
}

.chat-shell {
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  min-height: 680px;
  border-radius: 28px;
  border: 1px solid rgba(129, 148, 174, 0.18);
  background:
    linear-gradient(180deg, rgba(247, 250, 253, 0.95) 0%, rgba(243, 247, 252, 0.98) 100%);
  box-shadow: 0 22px 55px rgba(56, 84, 122, 0.12);
}

.chat-shell::before {
  content: '';
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at left top, rgba(116, 185, 165, 0.12), transparent 28%),
    radial-gradient(circle at right center, rgba(96, 141, 255, 0.12), transparent 32%);
  pointer-events: none;
}

.chat-topbar,
.messages-panel,
.composer-panel {
  position: relative;
  z-index: 1;
}

.chat-topbar {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 24px 28px 18px;
  border-bottom: 1px solid rgba(154, 169, 191, 0.18);
}

.topbar-title {
  display: flex;
  gap: 14px;
  align-items: flex-start;
}

.topbar-dot {
  width: 12px;
  height: 12px;
  margin-top: 7px;
  border-radius: 999px;
  background: #8da0bc;
  box-shadow: 0 0 0 6px rgba(141, 160, 188, 0.15);
  transition: all 0.25s ease;
}

.topbar-dot.active {
  background: #2fb086;
  box-shadow: 0 0 0 8px rgba(47, 176, 134, 0.16);
}

.topbar-title h2 {
  margin: 0;
  font-size: 20px;
  color: #1d3557;
}

.topbar-title p {
  margin: 6px 0 0;
  color: #60708a;
  font-size: 13px;
}

.topbar-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 10px;
  min-width: 220px;
}

.session-tag {
  padding: 6px 12px;
  border-radius: 999px;
  background: rgba(33, 115, 213, 0.1);
  color: #2955a6;
  font-size: 12px;
}

.session-preview {
  max-width: 280px;
  font-size: 12px;
  color: #66768f;
  text-align: right;
}

.messages-panel {
  flex: 1;
  overflow-y: auto;
  padding: 26px 28px 10px;
}

.message-row {
  display: flex;
  gap: 14px;
  margin-bottom: 22px;
}

.message-row.user {
  flex-direction: row-reverse;
}

.message-avatar {
  width: 42px;
  height: 42px;
  border-radius: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: #fff;
  background: linear-gradient(135deg, #5872a5 0%, #3651a2 100%);
  box-shadow: 0 10px 20px rgba(61, 88, 140, 0.18);
}

.message-row.user .message-avatar {
  background: linear-gradient(135deg, #54b6a2 0%, #2a8e7e 100%);
}

.message-main {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-width: min(820px, calc(100% - 70px));
}

.message-row.user .message-main {
  align-items: flex-end;
}

.message-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  color: #6b7b92;
}

.message-meta strong {
  color: #22395d;
}

.message-bubble {
  border-radius: 22px;
  padding: 16px 18px;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(128, 147, 176, 0.16);
  box-shadow: 0 16px 30px rgba(89, 111, 143, 0.08);
}

.message-row.user .message-bubble {
  background: linear-gradient(135deg, #2e8c7b 0%, #4bb6a2 100%);
  color: #fff;
  border: none;
}

.plain-text {
  margin: 0;
  white-space: pre-wrap;
  line-height: 1.75;
}

.markdown-body {
  color: #23354c;
  line-height: 1.8;
  font-size: 14px;
}

.markdown-body :deep(p) {
  margin: 0 0 10px;
}

.markdown-body :deep(p:last-child) {
  margin-bottom: 0;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  padding-left: 20px;
  margin: 10px 0;
}

.markdown-body :deep(code) {
  padding: 2px 6px;
  border-radius: 6px;
  background: rgba(36, 56, 86, 0.08);
  color: #234577;
  font-size: 13px;
}

.markdown-body :deep(pre) {
  margin: 12px 0;
  padding: 14px 16px;
  border-radius: 16px;
  overflow-x: auto;
  background: #162030;
  color: #eaf1ff;
}

.markdown-body :deep(pre code) {
  padding: 0;
  background: transparent;
  color: inherit;
}

.markdown-body :deep(blockquote) {
  margin: 12px 0;
  padding: 10px 14px;
  border-left: 4px solid #80a8ea;
  background: rgba(110, 155, 243, 0.08);
  border-radius: 12px;
}

.markdown-body :deep(a) {
  color: #295dc8;
  word-break: break-all;
}

.papers-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
  margin-top: 16px;
}

.paper-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px;
  border-radius: 18px;
  text-decoration: none;
  background: linear-gradient(180deg, #f8fbff 0%, #eef4ff 100%);
  border: 1px solid rgba(126, 153, 202, 0.24);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.paper-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 24px rgba(73, 101, 149, 0.12);
}

.paper-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 12px;
  color: #6880a7;
}

.paper-index {
  font-weight: 700;
  color: #3057a7;
}

.paper-card strong {
  color: #20365a;
  line-height: 1.5;
}

.paper-card p {
  margin: 0;
  font-size: 12px;
  color: #60708a;
}

.pending-bubble {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.pending-bubble p {
  margin: 0;
  color: #60708a;
}

.typing-line {
  display: flex;
  gap: 8px;
}

.typing-dot {
  width: 9px;
  height: 9px;
  border-radius: 999px;
  background: #7e98c8;
  animation: typing-bounce 0.8s infinite alternate;
}

.typing-dot:nth-child(2) {
  animation-delay: 0.2s;
}

.typing-dot:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes typing-bounce {
  from {
    transform: translateY(0);
    opacity: 0.45;
  }

  to {
    transform: translateY(-6px);
    opacity: 1;
  }
}

.empty-panel {
  min-height: 420px;
  display: grid;
  align-content: center;
  gap: 20px;
  justify-items: center;
  padding: 36px 20px 48px;
  text-align: center;
}

.empty-orb {
  width: 112px;
  height: 112px;
  border-radius: 50%;
  background:
    radial-gradient(circle at 30% 30%, rgba(255, 255, 255, 0.95), rgba(255, 255, 255, 0.2) 30%),
    linear-gradient(135deg, #8ec5b8 0%, #7aa4ef 100%);
  box-shadow:
    inset 0 1px 12px rgba(255, 255, 255, 0.55),
    0 20px 45px rgba(67, 106, 157, 0.18);
}

.empty-copy {
  max-width: 560px;
}

.empty-label {
  display: inline-block;
  margin-bottom: 10px;
  padding: 6px 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.78);
  color: #47649b;
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.empty-copy h3 {
  margin: 0 0 10px;
  font-size: 28px;
  color: #1d3557;
}

.empty-copy p {
  margin: 0;
  color: #64758d;
  line-height: 1.75;
}

.suggestion-grid {
  width: min(920px, 100%);
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.suggestion-card {
  padding: 18px 18px;
  border: 1px solid rgba(123, 147, 178, 0.18);
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.86);
  text-align: left;
  color: #234066;
  font-size: 14px;
  line-height: 1.65;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}

.suggestion-card:hover,
.quick-chip:hover {
  transform: translateY(-2px);
  border-color: rgba(63, 110, 193, 0.34);
  box-shadow: 0 12px 24px rgba(83, 108, 145, 0.12);
}

.composer-panel {
  padding: 0 24px 24px;
}

.quick-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding: 0 4px 14px;
}

.quick-chip {
  padding: 9px 14px;
  border-radius: 999px;
  border: 1px solid rgba(123, 147, 178, 0.2);
  background: rgba(255, 255, 255, 0.8);
  color: #38547f;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}

.composer-box {
  padding: 16px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(128, 147, 176, 0.18);
  box-shadow: 0 18px 34px rgba(84, 106, 138, 0.1);
}

.composer-box :deep(.el-textarea__inner) {
  border: none;
  box-shadow: none;
  background: transparent;
  min-height: 64px !important;
  padding: 6px 2px;
  font-size: 15px;
  line-height: 1.8;
  color: #203557;
}

.composer-box :deep(.el-textarea__inner::placeholder) {
  color: #93a0b2;
}

.composer-box :deep(.el-textarea__inner:focus) {
  box-shadow: none;
}

.composer-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 14px;
  padding-top: 12px;
  border-top: 1px solid rgba(128, 147, 176, 0.14);
}

.composer-hint {
  font-size: 12px;
  color: #71829a;
}

@media (max-width: 1100px) {
  .hero-panel {
    grid-template-columns: 1fr;
  }

  .hero-meta {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .topbar-actions {
    min-width: 0;
  }
}

@media (max-width: 768px) {
  .chat-page {
    gap: 14px;
  }

  .hero-panel,
  .chat-topbar,
  .messages-panel,
  .composer-panel {
    padding-left: 16px;
    padding-right: 16px;
  }

  .hero-panel {
    padding-top: 20px;
    padding-bottom: 20px;
    border-radius: 22px;
  }

  .hero-copy h1 {
    font-size: 28px;
  }

  .chat-shell {
    min-height: 620px;
    border-radius: 22px;
  }

  .chat-topbar {
    flex-direction: column;
    align-items: flex-start;
  }

  .topbar-actions {
    align-items: flex-start;
  }

  .message-main {
    max-width: calc(100% - 56px);
  }

  .suggestion-grid {
    grid-template-columns: 1fr;
  }

  .composer-panel {
    padding-bottom: 16px;
  }

  .composer-actions {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
