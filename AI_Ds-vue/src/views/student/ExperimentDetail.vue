<template>
  <div class="g-page">
    <page-header title="瀹為獙璇︽儏" :description="currentExp?.name || '鍔犺浇涓?..'">
      <button class="g-outline-btn" @click="$router.push('/student/experiments')">鈫?杩斿洖鍒楄〃</button>
    </page-header>

    <loading-state :loading="loading">
      <div v-if="currentExp" class="g-content">
        <!-- 淇℃伅鏉?-->
        <div class="g-info-bar">
          <span class="g-chip" :class="'c-' + currentExp.status">{{ statusText }}</span>
          <span v-if="currentExp.score" class="g-info-item">
            <span class="g-info-label">寰楀垎</span>
            <span class="g-info-val" style="color:#1a73e8;font-size:18px">{{ currentExp.score }}</span>
          </span>
          <span v-if="currentExp.deadline" class="g-info-item">
            <span class="g-info-label">鎴</span>
            <span class="g-info-val">{{ currentExp.deadline }}</span>
          </span>
          <span v-if="currentExp.submitTime" class="g-info-item">
            <span class="g-info-label">鎻愪氦</span>
            <span class="g-info-val">{{ currentExp.submitTime }}</span>
          </span>
          <span v-if="isCompleted && currentExp.plagiarismRate != null" class="g-info-item">
            <span class="g-info-label">鏌ラ噸鐜?/span>
            <span class="g-info-val" :class="plagiarismClass">{{ currentExp.plagiarismRate }}%</span>
          </span>
        </div>

        <!-- 鏍囩椤?-->
        <div class="g-card">
          <div class="g-tabs">
            <button class="g-tab" :class="{ active: activeTab === 'code' }" @click="activeTab = 'code'">馃搫 浠ｇ爜</button>
            <button class="g-tab" :class="{ active: activeTab === 'ai' }" @click="activeTab = 'ai'">馃 AI鍔╂暀鐐硅瘎</button>
            <button class="g-tab" :class="{ active: activeTab === 'report' }" @click="activeTab = 'report'" :disabled="!isCompleted">馃搵 瀹為獙鎶ュ憡</button>
          </div>

          <!-- 浠ｇ爜 -->
          <div v-if="activeTab === 'code'" class="g-tab-body">
            <div v-if="!currentExp.code || !isCompleted" class="g-empty">
              <div class="g-empty-icon">馃搫</div>
              <div class="g-empty-text">鏆傛棤浠ｇ爜鎻愪氦</div>
              <button class="g-primary-btn" @click="goToPTA">鍓嶅線PTA骞冲彴瀹屾垚瀹為獙</button>
            </div>
            <div v-else>
              <div class="g-toolbar">
                <span class="g-toolbar-title">鎻愪氦浠ｇ爜</span>
                <button class="g-outline-btn-sm" @click="copyCode">澶嶅埗</button>
              </div>
              <pre class="g-code"><code>{{ currentExp.code }}</code></pre>
            </div>
          </div>

          <!-- AI鐐硅瘎 -->
          <div v-if="activeTab === 'ai'" class="g-tab-body">
            <div class="g-toolbar">
              <div class="g-ai-badge">
                <span class="g-ai-dot"></span> AI 鍔╂暀鐐硅瘎
                <span v-if="aiSource === 'cache'" class="g-chip c-info">宸茬紦瀛?/span>
                <span v-else-if="aiSource === 'deepseek'" class="g-chip c-ok">鍒氱敓鎴?/span>
              </div>
              <button v-if="isCompleted" class="g-primary-btn-sm" :disabled="aiGenerating" @click="generateAiComment(true)">
                {{ aiGenerating ? '鍒嗘瀽涓?..' : (hasAiComment ? '馃攧 閲嶆柊鐢熸垚' : '鉁?鐢熸垚AI鐐硅瘎') }}
              </button>
            </div>
            <div v-if="aiGenerating" class="g-ai-loading">
              <el-skeleton :rows="6" animated />
              <div class="g-ai-loading-tip">
                <el-icon class="is-loading"><Loading /></el-icon>
                姝ｅ湪璋冪敤 DeepSeek 鍒嗘瀽浠ｇ爜锛岄璁￠渶瑕?10-20 绉?..
              </div>
            </div>
            <div v-else-if="hasAiComment" class="g-ai-content markdown-body" v-html="renderedAiComment"></div>
            <div v-else class="g-empty">
              <div class="g-empty-icon">馃</div>
              <div class="g-empty-text">{{ isCompleted ? '鏆傛棤AI鐐硅瘎' : '璇峰厛瀹屾垚瀹為獙' }}</div>
              <div class="g-empty-sub">{{ isCompleted ? '鐐瑰嚮涓婃柟鎸夐挳锛孉I鍔╂暀灏嗕负鎮ㄧ殑浠ｇ爜杩涜涓撲笟鐐硅瘎' : '瀹屾垚瀹為獙鎻愪氦鍚庯紝鍗冲彲鑾峰彇AI鍔╂暀鐨勪唬鐮佺偣璇? }}</div>
              <button v-if="isCompleted" class="g-primary-btn" @click="generateAiComment(false)">鉁?鐢熸垚AI鐐硅瘎</button>
            </div>
          </div>

          <!-- 鎶ュ憡 -->
          <div v-if="activeTab === 'report'" class="g-tab-body">
            <div v-if="!isCompleted" class="g-empty">
              <div class="g-empty-text">瀹屾垚瀹為獙鍚庡彲鐢熸垚鎶ュ憡</div>
            </div>
            <div v-else class="g-empty">
              <div class="g-empty-icon">馃搵</div>
              <div class="g-empty-text">AI瀹為獙鎶ュ憡鐢熸垚</div>
              <div class="g-empty-sub">鍩轰簬鎮ㄧ殑浠ｇ爜鍜孉I鐐硅瘎锛屽揩閫熺敓鎴愪笓涓氱殑瀹為獙鎶ュ憡</div>
              <button class="g-primary-btn" @click="$router.push('/student/ai-report')">鍓嶅線AI鎶ュ憡鐢熸垚涓績</button>
            </div>
          </div>
        </div>
      </div>
      <el-empty v-else description="鏈壘鍒拌瀹為獙" />
    </loading-state>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useExperimentStore } from '../../store'
import PageHeader from '../../components/PageHeader.vue'
import LoadingState from '../../components/LoadingState.vue'
import { ElMessage } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import axios from 'axios'
import { API_BASE_URL } from '../../config/runtime'

const API_BASE = API_BASE_URL
const route = useRoute()
const experimentStore = useExperimentStore()
const loading = ref(true)
const activeTab = ref('code')
const aiGenerating = ref(false)
const aiSource = ref('')
const localAiComment = ref('')

const experimentId = computed(() => Number(route.params.id))
const currentExp = computed(() => {
  const exp = experimentStore.currentExperiment
  if (!exp) return null
  return (exp.data && typeof exp.data === 'object' && exp.data.id) ? exp.data : exp
})

const isCompleted = computed(() => currentExp.value?.status === 'completed')
const statusText = computed(() => ({ completed: '宸插畬鎴?, in_progress: '杩涜涓? }[currentExp.value?.status] || '鏈紑濮?))
const plagiarismClass = computed(() => {
  const r = currentExp.value?.plagiarismRate || 0
  return r > 20 ? 'danger-text' : r > 10 ? 'warning-text' : ''
})

const aiCommentRaw = computed(() => localAiComment.value || currentExp.value?.aiComment || '')
const hasAiComment = computed(() => {
  const c = aiCommentRaw.value; return c && c.trim() && !c.includes('鏆傛椂杩樻病鏈夌敓鎴怉I鐐硅瘎')
})
const renderedAiComment = computed(() => hasAiComment.value ? DOMPurify.sanitize(marked(aiCommentRaw.value)) : '')

function copyCode() {
  if (currentExp.value?.code) { navigator.clipboard.writeText(currentExp.value.code); ElMessage.success('浠ｇ爜宸插鍒?) }
}
function goToPTA() { ElMessage.info('璇峰墠寰€PTA骞冲彴瀹屾垚瀹為獙') }

async function generateAiComment(force) {
  if (!isCompleted.value) return
  aiGenerating.value = true; aiSource.value = ''
  try {
    const res = await axios.post(`${API_BASE}/api/experiments/${experimentId.value}/ai-comment/generate?force=${force}`, null, { withCredentials: true })
    const data = res.data || res
    if (data.success && data.aiComment) {
      localAiComment.value = data.aiComment; aiSource.value = data.source || 'deepseek'
      if (data.source === 'deepseek') ElMessage.success('AI鐐硅瘎宸茬敓鎴?)
    } else { ElMessage.warning(data.message || 'AI鐐硅瘎鐢熸垚澶辫触') }
  } catch (e) { ElMessage.error('璇锋眰澶辫触: ' + (e.response?.data?.message || e.message)) }
  finally { aiGenerating.value = false }
}

onMounted(async () => {
  loading.value = true
  try {
    await experimentStore.fetchExperimentDetail(experimentId.value)
    if (isCompleted.value && !hasAiComment.value) generateAiComment(false)
  } catch (e) { console.error('鍔犺浇瀹為獙璇︽儏澶辫触:', e) }
  finally { loading.value = false }
})
</script>

<style scoped>
.g-page { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; }
.g-content { display: flex; flex-direction: column; gap: 16px; }

/* 淇℃伅鏉?*/
.g-info-bar {
  display: flex; align-items: center; gap: 20px; padding: 16px 20px;
  background: #fff; border-radius: 16px; border: 1px solid #dadce0; flex-wrap: wrap;
}
.g-info-item { display: flex; align-items: center; gap: 6px; }
.g-info-label { font-size: 12px; color: #5f6368; }
.g-info-val { font-size: 14px; font-weight: 500; color: #202124; }
.danger-text { color: #d93025; }
.warning-text { color: #e37400; }

/* 鍗＄墖 */
.g-card { background: #fff; border-radius: 16px; border: 1px solid #dadce0; overflow: hidden; }

/* 鏍囩 */
.g-tabs { display: flex; border-bottom: 1px solid #dadce0; padding: 0 20px; }
.g-tab {
  background: none; border: none; padding: 12px 16px; font-size: 14px; font-weight: 500;
  color: #5f6368; cursor: pointer; border-bottom: 2px solid transparent; transition: all 0.2s;
}
.g-tab.active { color: #1a73e8; border-bottom-color: #1a73e8; }
.g-tab:hover:not(.active):not(:disabled) { color: #202124; }
.g-tab:disabled { color: #9aa0a6; cursor: not-allowed; }
.g-tab-body { padding: 20px; }

/* Chips */
.g-chip { display: inline-block; font-size: 11px; padding: 2px 10px; border-radius: 100px; font-weight: 500; }
.c-completed { background: #e6f4ea; color: #1e8e3e; }
.c-in_progress { background: #fef7e0; color: #e37400; }
.c-not_started { background: #f1f3f4; color: #5f6368; }
.c-info { background: #f1f3f4; color: #5f6368; }
.c-ok { background: #e6f4ea; color: #1e8e3e; }

/* 宸ュ叿鏍?*/
.g-toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.g-toolbar-title { font-size: 14px; font-weight: 500; color: #202124; }

/* 鎸夐挳 */
.g-primary-btn {
  background: #1a73e8; color: #fff; border: none; border-radius: 100px;
  padding: 10px 24px; font-size: 14px; font-weight: 500; cursor: pointer; transition: background 0.2s;
}
.g-primary-btn:hover { background: #1765cc; }
.g-primary-btn-sm {
  background: #1a73e8; color: #fff; border: none; border-radius: 100px;
  padding: 6px 16px; font-size: 13px; font-weight: 500; cursor: pointer; transition: background 0.2s;
}
.g-primary-btn-sm:hover { background: #1765cc; }
.g-primary-btn-sm:disabled { background: #9aa0a6; cursor: not-allowed; }
.g-outline-btn {
  background: #fff; border: 1px solid #dadce0; border-radius: 100px;
  padding: 8px 20px; font-size: 13px; color: #5f6368; font-weight: 500; cursor: pointer; transition: all 0.2s;
}
.g-outline-btn:hover { background: #f8f9fa; border-color: #bdc1c6; }
.g-outline-btn-sm {
  background: #fff; border: 1px solid #dadce0; border-radius: 100px;
  padding: 4px 14px; font-size: 12px; color: #5f6368; cursor: pointer; transition: all 0.2s;
}
.g-outline-btn-sm:hover { background: #f8f9fa; }

/* 浠ｇ爜鍧?*/
.g-code {
  background: #1e1e2e; color: #cdd6f4; padding: 20px; border-radius: 12px;
  overflow-x: auto; font-size: 13px; line-height: 1.7; max-height: 600px; overflow-y: auto;
  white-space: pre-wrap; word-break: break-all; font-family: 'Cascadia Code', 'Fira Code', Consolas, monospace; margin: 0;
}

/* AI 鐐硅瘎 */
.g-ai-badge { display: flex; align-items: center; gap: 8px; font-size: 14px; font-weight: 500; color: #202124; }
.g-ai-dot { width: 8px; height: 8px; border-radius: 50%; background: #1e8e3e; animation: pulse 2s infinite; }
@keyframes pulse { 0%, 100% { opacity: 1 } 50% { opacity: 0.4 } }
.g-ai-loading { padding: 20px 0; }
.g-ai-loading-tip { display: flex; align-items: center; justify-content: center; gap: 8px; color: #5f6368; font-size: 13px; margin-top: 16px; }
.g-ai-content { background: #f8f9fa; padding: 24px; border-radius: 12px; border: 1px solid #e8eaed; font-size: 14px; line-height: 1.8; color: #202124; }

/* Markdown */
.markdown-body :deep(h1), .markdown-body :deep(h2), .markdown-body :deep(h3) { color: #202124; margin: 20px 0 10px; font-size: 16px; }
.markdown-body :deep(h3) { font-size: 15px; }
.markdown-body :deep(p) { margin: 8px 0; line-height: 1.8; }
.markdown-body :deep(ul), .markdown-body :deep(ol) { padding-left: 20px; margin: 8px 0; }
.markdown-body :deep(li) { margin: 4px 0; }
.markdown-body :deep(strong) { color: #1a73e8; }
.markdown-body :deep(code) { background: #e8eaed; padding: 2px 6px; border-radius: 4px; font-size: 13px; color: #d93025; }
.markdown-body :deep(pre) { background: #1e1e2e; color: #cdd6f4; padding: 16px; border-radius: 8px; overflow-x: auto; margin: 10px 0; }
.markdown-body :deep(pre code) { background: none; color: inherit; padding: 0; }
.markdown-body :deep(blockquote) { border-left: 4px solid #1a73e8; padding: 8px 16px; margin: 10px 0; background: #e8f0fe; border-radius: 0 8px 8px 0; color: #5f6368; }

/* 绌虹姸鎬?*/
.g-empty { text-align: center; padding: 48px 20px; }
.g-empty-icon { font-size: 48px; margin-bottom: 12px; }
.g-empty-text { font-size: 16px; font-weight: 500; color: #202124; margin-bottom: 6px; }
.g-empty-sub { font-size: 13px; color: #5f6368; margin-bottom: 20px; }
</style>


