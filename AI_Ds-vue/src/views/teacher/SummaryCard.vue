<template>
  <div class="summary-page">
    <!-- 顶部 -->
    <div class="hero">
      <div class="hero-inner">
        <div class="hero-icon">📖</div>
        <div class="hero-text">
          <h1>AI 精读卡片</h1>
          <p>支持 arXiv、DOI、粘贴文本、本地文档，一键生成结构化精读</p>
        </div>
      </div>
    </div>

    <!-- Tab 切换 -->
    <div class="tab-bar">
      <div v-for="t in tabs" :key="t.key"
        :class="['tab-item', { active: activeTab === t.key }]"
        @click="activeTab = t.key">
        <span class="tab-icon">{{ t.icon }}</span>
        <span>{{ t.label }}</span>
      </div>
    </div>

    <!-- arXiv -->
    <div v-if="activeTab === 'arxiv'" class="panel">
      <p class="panel-desc">输入 arXiv ID，自动抓取论文全文并生成精读卡（首次抓取可能需要 30-60 秒）</p>
      <div class="inline-form">
        <el-input v-model="arxivId" placeholder="例如：1706.03762" class="form-input"
          @keydown.enter="genArxiv(false)" clearable />
        <el-button type="primary" :loading="arxivLoading" :disabled="!arxivId.trim()" @click="genArxiv(false)">
          {{ arxivLoading ? '正在抓取论文...' : '生成' }}
        </el-button>
        <el-button :loading="arxivLoading" :disabled="!arxivId.trim()" @click="genArxiv(true)">重新生成</el-button>
      </div>
      <div v-if="arxivLoading" class="loading-hint">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>正在从 arXiv 抓取论文并生成精读，请耐心等待...</span>
      </div>
      <ResultBlock v-if="arxivResult" :result="arxivResult" :meta="arxivMeta" />
    </div>

    <!-- DOI -->
    <div v-if="activeTab === 'doi'" class="panel">
      <p class="panel-desc">输入论文 DOI，通过 Crossref 获取元数据并生成精读卡</p>
      <div class="inline-form">
        <el-input v-model="doi" placeholder="例如：10.1145/3292500.3330919" class="form-input"
          @keydown.enter="genDoi" clearable />
        <el-button type="primary" :loading="doiLoading" :disabled="!doi.trim()" @click="genDoi">生成</el-button>
      </div>
      <ResultBlock v-if="doiResult" :result="doiResult" :meta="doiMeta" />
    </div>

    <!-- 粘贴文本 -->
    <div v-if="activeTab === 'freetext'" class="panel">
      <p class="panel-desc">粘贴论文标题和摘要，快速生成精读卡</p>
      <el-input v-model="ftTitle" placeholder="论文标题" style="margin-bottom:12px" clearable />
      <el-input v-model="ftText" type="textarea" :rows="5" placeholder="粘贴摘要或正文内容..." />
      <el-button type="primary" :loading="ftLoading" :disabled="!ftTitle.trim() || !ftText.trim()"
        style="margin-top:14px" @click="genFreeText">生成精读卡</el-button>
      <ResultBlock v-if="ftResult" :result="ftResult" :meta="ftMeta" />
    </div>

    <!-- 文档精读 -->
    <div v-if="activeTab === 'doc'" class="panel">
      <p class="panel-desc">对已上传的本地文档生成精读卡</p>
      <div class="inline-form">
        <el-select v-model="docId" placeholder="选择文档" :loading="docsLoading" filterable class="form-input">
          <el-option v-for="d in docs" :key="d.id" :value="String(d.id)"
            :label="`${d.filename} (${(d.sizeBytes/1024).toFixed(0)} KB)`" />
        </el-select>
        <el-button type="primary" :loading="docLoading" :disabled="!docId" @click="genDoc(false)">生成</el-button>
        <el-button :loading="docLoading" :disabled="!docId" @click="genDoc(true)">重新生成</el-button>
      </div>
      <ResultBlock v-if="docResult" :result="docResult" :meta="docMeta" />
    </div>

    <el-alert v-if="error" :title="error" type="error" show-icon closable
      style="margin-top:16px" @close="error = ''" />
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Loading } from '@element-plus/icons-vue'
import { getDocuments, summarizeArxiv, summarizeDoi, summarizeFreeText, summarizeDocument } from '../../api/tap'
import ResultBlock from './components/ResultBlock.vue'

const route = useRoute()
const activeTab = ref(route.query.docId ? 'doc' : 'arxiv')
const error = ref('')

const tabs = [
  { key: 'arxiv', label: 'arXiv 论文', icon: '📄' },
  { key: 'doi', label: 'DOI 查询', icon: '🔗' },
  { key: 'freetext', label: '粘贴文本', icon: '📝' },
  { key: 'doc', label: '文档精读', icon: '📁' },
]

const arxivId = ref(''); const arxivLoading = ref(false); const arxivResult = ref(''); const arxivMeta = ref('')
const doi = ref(''); const doiLoading = ref(false); const doiResult = ref(''); const doiMeta = ref('')
const ftTitle = ref(''); const ftText = ref(''); const ftLoading = ref(false); const ftResult = ref(''); const ftMeta = ref('')
const docId = ref(route.query.docId || ''); const docLoading = ref(false); const docResult = ref(''); const docMeta = ref('')
const docs = ref([]); const docsLoading = ref(false)

watch(() => route.query.docId, (val) => { if (val) { docId.value = val; activeTab.value = 'doc' } })

const loadDocs = async () => {
  docsLoading.value = true
  try { const res = await getDocuments(); docs.value = res?.data ?? res ?? [] } catch (e) { console.error(e) }
  docsLoading.value = false
}

const unwrap = (res) => res?.data ?? res

const genArxiv = async (force) => {
  const id = arxivId.value.trim()
  if (!id) return
  arxivLoading.value = true; error.value = ''; arxivResult.value = ''
  try {
    const d = unwrap(await summarizeArxiv(id, force))
    arxivResult.value = d?.markdown ?? ''
    arxivMeta.value = d ? `引擎：${d.provider} | 模型：${d.model} | 字数：${d.charCountZh}` : ''
  } catch (e) {
    const msg = e.message || ''
    if (msg.includes('timeout') || msg.includes('Timeout') || msg.includes('ECONNABORTED')) {
      error.value = 'arXiv 论文抓取超时，请稍后重试。部分论文 PDF 较大，可能需要更长时间。'
    } else {
      error.value = msg
    }
  }
  arxivLoading.value = false
}

const genDoi = async () => {
  if (!doi.value.trim()) return; doiLoading.value = true; error.value = ''
  try {
    const d = unwrap(await summarizeDoi(doi.value.trim()))
    doiResult.value = d?.markdown ?? ''
    doiMeta.value = d ? `《${d.title}》 | 引擎：${d.provider}` : ''
  } catch (e) { error.value = e.message }
  doiLoading.value = false
}

const genFreeText = async () => {
  if (!ftTitle.value.trim() || !ftText.value.trim()) return; ftLoading.value = true; error.value = ''
  try {
    const d = unwrap(await summarizeFreeText(ftTitle.value.trim(), ftText.value.trim()))
    ftResult.value = d?.markdown ?? ''
    ftMeta.value = d ? `引擎：${d.provider} | 字数：${d.charCountZh}` : ''
  } catch (e) { error.value = e.message }
  ftLoading.value = false
}

const genDoc = async (force) => {
  if (!docId.value) return; docLoading.value = true; error.value = ''
  try {
    const d = unwrap(await summarizeDocument(docId.value, force))
    docResult.value = d?.markdown ?? ''
    docMeta.value = d ? `引擎：${d.provider} | 模型：${d.model}` : ''
  } catch (e) { error.value = e.message }
  docLoading.value = false
}

onMounted(loadDocs)
</script>

<style scoped>
.summary-page { min-height: 100%; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; }

.hero {
  background: #fff; border-radius: 16px; padding: 28px 32px; margin-bottom: 24px;
  border: 1px solid #dadce0; display: flex; align-items: center;
}
.hero-inner { display: flex; align-items: center; gap: 16px; }
.hero-icon { font-size: 36px; }
.hero-text h1 { margin: 0 0 4px; font-size: 22px; font-weight: 400; color: #202124; }
.hero-text p { margin: 0; font-size: 14px; color: #5f6368; }

.tab-bar {
  display: flex; gap: 0; margin-bottom: 20px;
  border-bottom: 1px solid #dadce0;
}
.tab-item {
  flex: 1; display: flex; align-items: center; justify-content: center; gap: 6px;
  padding: 12px 0; cursor: pointer;
  font-size: 14px; color: #5f6368; transition: all .2s;
  border-bottom: 2px solid transparent;
}
.tab-item:hover { color: #202124; }
.tab-item.active {
  color: #1a73e8; font-weight: 500;
  border-bottom-color: #1a73e8;
}
.tab-icon { font-size: 16px; }

.panel {
  background: #fff; border-radius: 16px; padding: 24px 28px;
  border: 1px solid #dadce0;
}
.panel-desc { color: #5f6368; font-size: 13px; margin: 0 0 16px; }
.inline-form { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.form-input { flex: 1; min-width: 200px; }

.loading-hint {
  display: flex; align-items: center; gap: 8px;
  margin-top: 16px; padding: 12px 16px;
  background: #e8f0fe; border-radius: 8px;
  color: #1a73e8; font-size: 13px;
}
</style>
