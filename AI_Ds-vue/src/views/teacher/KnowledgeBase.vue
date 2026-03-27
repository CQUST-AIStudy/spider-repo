<template>
  <div class="knowledge-base-container">
    <page-header title="课程知识库" description="管理课程资料，上传书籍，AI知识问答" />

    <!-- Course Space List View -->
    <div v-if="!selectedSpace" class="space-list-view">
      <div class="space-actions">
        <el-button type="primary" @click="showCreateDialog">
          <el-icon><Plus /></el-icon> 创建课程空间
        </el-button>
      </div>
      <el-empty v-if="spaces.length === 0 && !loading" description="暂无课程空间，点击上方按钮创建" />
      <el-row :gutter="20" v-loading="loading">
        <el-col :span="8" v-for="space in spaces" :key="space.id" style="margin-bottom: 20px">
          <el-card class="space-card" shadow="hover" @click="selectSpace(space)">
            <template #header>
              <div class="card-header">
                <span class="card-title">{{ space.name }}</span>
                <el-tag size="small" :type="visibilityTagType(space.docVisibility)">
                  {{ visibilityLabel(space.docVisibility) }}
                </el-tag>
                <el-dropdown @click.stop trigger="click">
                  <el-icon class="card-more"><MoreFilled /></el-icon>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item @click="editSpace(space)">编辑</el-dropdown-item>
                      <el-dropdown-item @click="confirmDeleteSpace(space)">删除</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </template>
            <div class="card-body">
              <p v-if="space.term"><el-icon><Calendar /></el-icon> {{ space.term }}</p>
              <p v-if="space.courseName"><el-icon><Reading /></el-icon> {{ space.courseName }}</p>
              <p class="card-stats">Mode: {{ modeLabel(space.defaultMode) }}</p>
              <p v-if="space.docVisibility === 'class'" class="card-stats">{{ (space.boundClassIds || []).length }} linked classes</p>
              <p class="card-stats">{{ space.docCount || 0 }} 个文档</p>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <!-- Course Space Detail View -->
    <div v-else class="space-detail-view">
      <div class="detail-header">
        <el-button @click="selectedSpace = null"><el-icon><ArrowLeft /></el-icon> 返回</el-button>
        <h2>{{ selectedSpace.name }}</h2>
        <span v-if="selectedSpace.term" class="detail-meta">{{ selectedSpace.term }}</span>
        <el-tag size="small" :type="visibilityTagType(selectedSpace.docVisibility)">
          {{ visibilityLabel(selectedSpace.docVisibility) }}
        </el-tag>
      </div>

      <el-tabs v-model="activeTab" style="margin-top: 16px">
        <!-- Tab 1: 文档管理 -->
        <el-tab-pane label="文档管理" name="docs">
          <!-- Upload -->
          <el-card class="section-card">
            <template #header><span>上传书籍/文档</span></template>
            <el-upload drag multiple :auto-upload="false" :on-change="onFileChange" :file-list="pendingFiles"
              accept=".pdf,.docx,.txt,.md">
              <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
              <div class="el-upload__text">拖拽文件到此处，或<em>点击选择</em></div>
              <template #tip>
                <div class="el-upload__tip">支持 PDF、DOCX、TXT、Markdown 格式的教材、讲义、参考书</div>
              </template>
            </el-upload>
            <div style="margin-top: 12px; text-align: right" v-if="pendingFiles.length > 0">
              <el-select v-model="uploadDocType" style="width: 140px; margin-right: 8px" size="default">
                <el-option label="教材" value="textbook" />
                <el-option label="讲义" value="lecture" />
                <el-option label="参考书" value="reference" />
                <el-option label="习题集" value="exercise" />
                <el-option label="其他" value="other" />
              </el-select>
              <el-button type="primary" @click="uploadFiles" :loading="uploading">
                上传 {{ pendingFiles.length }} 个文件
              </el-button>
            </div>
          </el-card>

          <!-- Document List -->
          <el-card class="section-card">
            <template #header>
              <div style="display:flex;justify-content:space-between;align-items:center">
                <span>文档列表</span>
                <el-button @click="loadDocuments" :loading="docsLoading" link>刷新</el-button>
              </div>
            </template>
            <el-empty v-if="documents.length === 0 && !docsLoading" description="暂无文档，请上传课程资料" />
            <el-table v-else :data="documents" v-loading="docsLoading" stripe>
              <el-table-column prop="documentId" label="ID" width="80" />
              <el-table-column prop="docType" label="类型" width="100">
                <template #default="{ row }">{{ docTypeLabel(row.docType) }}</template>
              </el-table-column>
              <el-table-column label="状态" width="140">
                <template #default="{ row }">
                  <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="chunkCount" label="分块数" width="100" />
              <el-table-column prop="createdAt" label="上传时间" />
              <el-table-column label="错误" min-width="150">
                <template #default="{ row }">
                  <span v-if="row.errorMessage" style="color:#d93025;font-size:12px">{{ row.errorMessage }}</span>
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </el-tab-pane>

        <!-- Tab 2: 知识问答 -->
        <el-tab-pane label="知识问答" name="chat">
          <div class="chat-container">
            <div class="chat-messages" ref="chatMessagesRef">
              <div v-if="chatMessages.length === 0" class="chat-empty">
                <el-icon style="font-size: 48px; color: #dadce0"><ChatDotRound /></el-icon>
                <p>向知识库提问，AI将基于上传的课程资料回答</p>
                <div class="chat-suggestions">
                  <el-button v-for="s in suggestions" :key="s" size="small" round @click="askQuestion(s)">{{ s }}</el-button>
                </div>
              </div>
              <div v-for="(msg, idx) in chatMessages" :key="idx" :class="['chat-msg', msg.role]">
                <div class="msg-bubble">
                  <div v-if="msg.role === 'user'" class="msg-text">{{ msg.content }}</div>
                  <div v-else class="msg-text" v-html="renderMarkdown(msg.content)"></div>
                  <div v-if="msg.citations && msg.citations.length" class="msg-citations">
                    <span class="citation-label">引用来源:</span>
                    <el-tag v-for="c in msg.citations" :key="c.index" size="small" type="info" style="margin: 2px">
                      [{{ c.index }}] {{ c.docName }} {{ c.chapterPath }}
                    </el-tag>
                  </div>
                </div>
              </div>
              <div v-if="chatLoading" class="chat-msg assistant">
                <div class="msg-bubble"><span class="typing-indicator">AI 正在思考...</span></div>
              </div>
            </div>
            <div class="chat-input-area">
              <el-input v-model="chatInput" placeholder="输入问题，如：什么是二叉搜索树？" :rows="2" type="textarea"
                @keydown.enter.ctrl="sendChat" :disabled="chatLoading" />
              <el-button type="primary" @click="sendChat" :loading="chatLoading" :disabled="!chatInput.trim()">
                发送
              </el-button>
            </div>
          </div>
        </el-tab-pane>

        <!-- Tab 3: 段落标注 -->
        <el-tab-pane label="段落标注" name="annotations">
          <el-card class="section-card">
            <template #header>
              <div style="display:flex;justify-content:space-between;align-items:center">
                <span>知识分块 ({{ chunks.length }})</span>
                <el-button @click="loadChunksAndAnnotations" :loading="chunksLoading" link>刷新</el-button>
              </div>
            </template>
            <el-empty v-if="chunks.length === 0 && !chunksLoading" description="暂无分块数据，请先上传文档" />
            <div v-else class="chunk-list">
              <div v-for="chunk in chunks" :key="chunk.id" class="chunk-item">
                <div class="chunk-content">
                  <span class="chunk-meta">[{{ chunk.id }}] {{ chunk.chapterPath || '未分类' }}</span>
                  <p>{{ chunk.contentPreview || chunk.content?.substring(0, 200) }}</p>
                </div>
                <div class="chunk-actions">
                  <el-button size="small" type="warning" @click="addAnnotation(chunk.id, 'important')">📌 重点</el-button>
                  <el-button size="small" type="danger" @click="addAnnotation(chunk.id, 'error_prone')">⚠️ 易错</el-button>
                </div>
              </div>
            </div>
          </el-card>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- Create/Edit Dialog -->
    <el-dialog v-model="dialogVisible" :title="editingSpace ? '编辑课程空间' : '创建课程空间'" width="500px">
      <el-form :model="spaceForm" label-width="80px">
        <el-form-item label="名称" required>
          <el-input v-model="spaceForm.name" placeholder="如: 数据结构2024秋" />
        </el-form-item>
        <el-form-item label="学期">
          <el-input v-model="spaceForm.term" placeholder="如: 2024-2025-2" />
        </el-form-item>
        <el-form-item label="课程">
          <el-input v-model="spaceForm.courseName" placeholder="如: 数据结构" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="spaceForm.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="Visibility">
          <el-select v-model="spaceForm.docVisibility" style="width: 100%">
            <el-option label="Public (students can use this RAG space)" value="public" />
            <el-option label="Class scoped (only bound classes can use it)" value="class" />
            <el-option label="Private (teacher only)" value="private" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="spaceForm.docVisibility === 'class'" label="Classes">
          <el-select v-model="spaceForm.classIds" multiple collapse-tags collapse-tags-tooltip style="width: 100%">
            <el-option
              v-for="cls in teacherClasses"
              :key="cls.id"
              :label="`${cls.name}${cls.courseName ? ' / ' + cls.courseName : ''}`"
              :value="cls.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="Mode">
          <el-select v-model="spaceForm.defaultMode" style="width: 100%">
            <el-option label="Strict" value="strict" />
            <el-option label="Open" value="open" />
          </el-select>
        </el-form-item>
        <el-form-item label="Web Search">
          <el-switch v-model="spaceForm.allowWebSearch" />
        </el-form-item>
        <el-form-item label="Citation">
          <el-switch v-model="spaceForm.requireCitation" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveSpace" :loading="saving" :disabled="!spaceForm.name">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, MoreFilled, Calendar, Reading, ArrowLeft, UploadFilled, ChatDotRound } from '@element-plus/icons-vue'
import PageHeader from '../../components/PageHeader.vue'
import {
  getCourseSpaces, createCourseSpace, updateCourseSpace, deleteCourseSpace,
  getCourseSpaceDocuments, uploadCourseSpaceDocument,
  getTeachingClasses,
  getCourseSpaceChunks, getAnnotations, createAnnotation, deleteAnnotation,
  ragChatStream
} from '@/api/tap'

const spaces = ref([])
const loading = ref(false)
const teacherClasses = ref([])
const selectedSpace = ref(null)
const activeTab = ref('docs')

// Documents
const documents = ref([])
const docsLoading = ref(false)
const pendingFiles = ref([])
const uploading = ref(false)
const uploadDocType = ref('textbook')

// Chunks
const chunks = ref([])
const chunksLoading = ref(false)

// Chat
const chatMessages = ref([])
const chatInput = ref('')
const chatLoading = ref(false)
const chatMessagesRef = ref(null)
const suggestions = ['什么是二叉搜索树？', '链表和数组的区别？', 'Dijkstra算法的时间复杂度？']

// Dialog
const dialogVisible = ref(false)
const editingSpace = ref(null)
const saving = ref(false)
const spaceForm = ref({
  name: '',
  term: '',
  courseName: '',
  description: '',
  defaultMode: 'strict',
  allowWebSearch: false,
  requireCitation: true,
  docVisibility: 'public',
  classIds: []
})

let refreshTimer = null

const modeLabel = (mode) => mode === 'open' ? 'Open' : 'Strict'
const visibilityLabel = (visibility) => {
  if (visibility === 'public') return 'Public'
  if (visibility === 'class') return 'Class Scoped'
  return 'Private'
}
const visibilityTagType = (visibility) => {
  if (visibility === 'public') return 'success'
  if (visibility === 'class') return 'warning'
  return 'info'
}

const statusTagType = (s) => ({ READY: 'success', PROCESSING: '', PENDING: 'warning', FAILED: 'danger' }[s] || 'info')
const statusLabel = (s) => ({ READY: '已就绪', PROCESSING: '处理中', PENDING: '等待处理', FAILED: '失败' }[s] || s)
const docTypeLabel = (t) => ({ textbook: '教材', lecture: '讲义', reference: '参考书', exercise: '习题集', faq: 'FAQ' }[t] || t || '其他')

function renderMarkdown(text) {
  if (!text) return ''
  return text
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/\n/g, '<br>')
    .replace(/\[(\d+)\]/g, '<sup style="color:#1a73e8;cursor:pointer">[$1]</sup>')
}

// ---- Spaces ----
async function loadSpaces() {
  loading.value = true
  try {
    const res = await getCourseSpaces()
    spaces.value = res?.data || res || []
  } catch (e) { ElMessage.error('加载失败: ' + e.message) }
  loading.value = false
}

async function loadTeachingClasses() {
  try {
    const res = await getTeachingClasses()
    teacherClasses.value = res?.data || res || []
  } catch (e) {
    teacherClasses.value = []
  }
}

function selectSpace(space) {
  selectedSpace.value = space
  activeTab.value = 'docs'
  chatMessages.value = []
  loadDocuments()
}

function showCreateDialog() {
  editingSpace.value = null
  spaceForm.value = {
    name: '',
    term: '',
    courseName: '',
    description: '',
    defaultMode: 'strict',
    allowWebSearch: false,
    requireCitation: true,
    docVisibility: 'public',
    classIds: []
  }
  dialogVisible.value = true
}

function editSpace(space) {
  editingSpace.value = space
  spaceForm.value = {
    name: space.name,
    term: space.term || '',
    courseName: space.courseName || '',
    description: space.description || '',
    defaultMode: space.defaultMode || 'strict',
    allowWebSearch: !!space.allowWebSearch,
    requireCitation: space.requireCitation !== false,
    docVisibility: space.docVisibility || 'private',
    classIds: Array.isArray(space.boundClassIds) ? [...space.boundClassIds] : []
  }
  dialogVisible.value = true
}

async function saveSpace() {
  if (!spaceForm.value.name) return
  if (spaceForm.value.docVisibility === 'class' && (!spaceForm.value.classIds || spaceForm.value.classIds.length === 0)) {
    ElMessage.warning('Please bind at least one teaching class for class-scoped access.')
    return
  }
  saving.value = true
  try {
    if (editingSpace.value) {
      await updateCourseSpace(editingSpace.value.id, spaceForm.value)
      ElMessage.success('已更新')
    } else {
      await createCourseSpace(spaceForm.value)
      ElMessage.success('已创建')
    }
    dialogVisible.value = false
    loadSpaces()
  } catch (e) { ElMessage.error(e.message) }
  saving.value = false
}

async function confirmDeleteSpace(space) {
  try {
    await ElMessageBox.confirm(`确定删除「${space.name}」？`, '确认', { type: 'warning' })
    await deleteCourseSpace(space.id)
    ElMessage.success('已删除')
    loadSpaces()
  } catch (e) { if (e !== 'cancel') ElMessage.error(e.message) }
}

// ---- Documents ----
async function loadDocuments() {
  if (!selectedSpace.value) return
  docsLoading.value = true
  try {
    const res = await getCourseSpaceDocuments(selectedSpace.value.id)
    documents.value = res?.data || res || []
  } catch (e) { ElMessage.error('加载文档失败: ' + e.message) }
  docsLoading.value = false
}

function onFileChange(file, fileList) { pendingFiles.value = fileList }

async function uploadFiles() {
  if (!selectedSpace.value || pendingFiles.value.length === 0) return
  uploading.value = true
  let ok = 0, fail = 0
  for (const f of pendingFiles.value) {
    try {
      await uploadCourseSpaceDocument(selectedSpace.value.id, f.raw, uploadDocType.value)
      ok++
    } catch { fail++ }
  }
  uploading.value = false
  pendingFiles.value = []
  if (ok) ElMessage.success(`成功上传 ${ok} 个文件`)
  if (fail) ElMessage.warning(`${fail} 个文件失败`)
  loadDocuments()
}

// ---- Chunks ----
async function loadChunksAndAnnotations() {
  if (!selectedSpace.value) return
  chunksLoading.value = true
  try {
    const res = await getCourseSpaceChunks(selectedSpace.value.id)
    chunks.value = res?.data || res || []
  } catch (e) { console.warn(e) }
  chunksLoading.value = false
}

async function addAnnotation(chunkId, type) {
  try {
    await createAnnotation(selectedSpace.value.id, { chunkId, annotationType: type, note: '' })
    ElMessage.success('标注已添加')
  } catch (e) { ElMessage.error(e.message) }
}

// ---- Chat ----
function askQuestion(q) { chatInput.value = q; sendChat() }

async function sendChat() {
  const q = chatInput.value.trim()
  if (!q || chatLoading.value || !selectedSpace.value) return
  chatMessages.value.push({ role: 'user', content: q })
  chatInput.value = ''
  chatLoading.value = true
  scrollToBottom()

  try {
    const resp = await ragChatStream(selectedSpace.value.id, q, 'strict')
    if (!resp.ok) {
      chatMessages.value.push({ role: 'assistant', content: `请求失败 (${resp.status})` })
      chatLoading.value = false
      return
    }

    const reader = resp.body.getReader()
    const decoder = new TextDecoder()
    let fullText = ''
    const msgIdx = chatMessages.value.length
    chatMessages.value.push({ role: 'assistant', content: '', citations: [] })

    while (true) { // eslint-disable-line no-constant-condition
      const { done, value } = await reader.read()
      if (done) break
      const chunk = decoder.decode(value, { stream: true })
      fullText += chunk
      // Extract citations if present
      const citMatch = fullText.match(/<!--CITATIONS:(.+?)-->/)
      if (citMatch) {
        try {
          chatMessages.value[msgIdx].citations = JSON.parse(citMatch[1])
        } catch (e) { /* ignore parse error */ } // eslint-disable-line no-empty
        chatMessages.value[msgIdx].content = fullText.replace(/\n\n<!--CITATIONS:.+?-->/, '')
      } else {
        chatMessages.value[msgIdx].content = fullText
      }
      scrollToBottom()
    }
  } catch (e) {
    chatMessages.value.push({ role: 'assistant', content: '网络错误: ' + e.message })
  }
  chatLoading.value = false
  scrollToBottom()
}

function scrollToBottom() {
  nextTick(() => {
    const el = chatMessagesRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

// ---- Lifecycle ----
onMounted(() => {
  loadSpaces()
  loadTeachingClasses()
  refreshTimer = setInterval(() => {
    if (selectedSpace.value && documents.value.some(d => d.status === 'PROCESSING' || d.status === 'PENDING')) {
      loadDocuments()
    }
  }, 5000)
})
onUnmounted(() => { if (refreshTimer) clearInterval(refreshTimer) })
</script>

<style scoped>
.knowledge-base-container { padding: 0; }
.space-actions { margin-bottom: 20px; }
.space-card { cursor: pointer; transition: all 0.25s; border-radius: 16px; border: 1px solid #dadce0; }
.space-card:hover { transform: translateY(-3px); box-shadow: 0 8px 20px rgba(0,0,0,0.08); }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.card-title { font-weight: 600; font-size: 16px; color: #202124; }
.card-more { cursor: pointer; font-size: 18px; color: #9aa0a6; }
.card-body p { margin: 6px 0; color: #3c4043; font-size: 14px; display: flex; align-items: center; gap: 4px; }
.card-stats { color: #9aa0a6 !important; font-size: 13px !important; }
.detail-header { display: flex; align-items: center; gap: 12px; }
.detail-header h2 { margin: 0; font-size: 20px; color: #202124; }
.detail-meta { color: #5f6368; font-size: 14px; background: #f1f3f4; padding: 2px 10px; border-radius: 6px; }
.section-card { margin-bottom: 16px; }

/* Chat */
.chat-container { display: flex; flex-direction: column; height: 520px; border: 1px solid #dadce0; border-radius: 12px; overflow: hidden; }
.chat-messages { flex: 1; overflow-y: auto; padding: 16px; background: #f8f9fa; }
.chat-empty { text-align: center; padding: 60px 20px; color: #9aa0a6; }
.chat-empty p { margin: 12px 0; }
.chat-suggestions { margin-top: 16px; display: flex; flex-wrap: wrap; gap: 8px; justify-content: center; }
.chat-msg { margin-bottom: 12px; display: flex; }
.chat-msg.user { justify-content: flex-end; }
.chat-msg.assistant { justify-content: flex-start; }
.msg-bubble { max-width: 80%; padding: 10px 14px; border-radius: 12px; font-size: 14px; line-height: 1.6; }
.chat-msg.user .msg-bubble { background: #1a73e8; color: #fff; border-bottom-right-radius: 4px; }
.chat-msg.assistant .msg-bubble { background: #fff; color: #202124; border: 1px solid #e8eaed; border-bottom-left-radius: 4px; }
.msg-citations { margin-top: 8px; padding-top: 8px; border-top: 1px solid #e8eaed; }
.citation-label { font-size: 12px; color: #9aa0a6; margin-right: 4px; }
.typing-indicator { color: #9aa0a6; font-style: italic; }
.chat-input-area { display: flex; gap: 8px; padding: 12px; background: #fff; border-top: 1px solid #e8eaed; align-items: flex-end; }
.chat-input-area .el-textarea { flex: 1; }

/* Chunks */
.chunk-list { max-height: 500px; overflow-y: auto; }
.chunk-item { padding: 12px; border-bottom: 1px solid #f1f3f4; }
.chunk-meta { font-size: 12px; color: #9aa0a6; }
.chunk-content p { margin: 6px 0; font-size: 14px; color: #3c4043; line-height: 1.5; }
.chunk-actions { margin-top: 6px; }

.knowledge-base-container :deep(.el-card) { border-radius: 16px; border: 1px solid #dadce0; }
</style>
