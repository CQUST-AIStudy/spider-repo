<template>
  <div class="bilingual-page">
    <!-- 顶部 -->
    <div class="hero">
      <div class="hero-inner">
        <div class="hero-icon">🌐</div>
        <div class="hero-text">
          <h1>双语对照阅读</h1>
          <p>高质量翻译，左右对照查看文档内容</p>
        </div>
      </div>
    </div>

    <!-- 控制面板 -->
    <div class="control-panel">
      <el-select v-model="docId" placeholder="选择文档" :loading="docsLoading" filterable class="ctrl-select"
        @change="onDocChange">
        <el-option v-for="d in docs" :key="d.id" :value="String(d.id)"
          :label="`${d.filename} (${(d.sizeBytes/1024).toFixed(0)} KB)`" />
      </el-select>
      <el-select v-model="lang" class="ctrl-lang">
        <el-option label="中文" value="ZH" />
        <el-option label="英文" value="EN-US" />
        <el-option label="日文" value="JA" />
        <el-option label="韩文" value="KO" />
        <el-option label="法文" value="FR" />
        <el-option label="德文" value="DE" />
      </el-select>
      <el-checkbox v-model="force" label="强制重新翻译" />
      <el-button type="primary" :loading="loading" :disabled="!docId" @click="translate">
        {{ loading ? '翻译中...' : '开始翻译' }}
      </el-button>
      <span v-if="meta" class="meta-tag">{{ meta }}</span>
    </div>

    <el-alert v-if="error" :title="error" type="error" show-icon closable
      style="margin-bottom:20px" @close="error = ''" />

    <!-- 翻译结果 -->
    <div v-if="segments.length > 0" class="segments-list">
      <div class="segments-header">
        <span class="seg-count">共 {{ segments.length }} 段</span>
      </div>
      <div v-for="seg in segments" :key="seg.index" class="seg-row">
        <div class="seg-num">{{ seg.index + 1 }}</div>
        <div class="seg-source">
          <div class="seg-label">原文</div>
          <div class="seg-body">{{ seg.source }}</div>
        </div>
        <div class="seg-divider"></div>
        <div class="seg-target">
          <div class="seg-label target">译文</div>
          <div class="seg-body">{{ seg.target }}</div>
        </div>
      </div>
    </div>

    <el-empty v-if="!loading && segments.length === 0 && !error" description="选择文档后点击「开始翻译」">
      <template #image>
        <div style="font-size:48px">📄</div>
      </template>
    </el-empty>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { getDocuments, translateDocument } from '../../api/tap'

const route = useRoute()
const docId = ref(route.query.docId || '')
const lang = ref('ZH')
const force = ref(false)
const loading = ref(false)
const meta = ref('')
const segments = ref([])
const error = ref('')
const docs = ref([])
const docsLoading = ref(false)

watch(() => route.query.docId, (val) => { if (val) docId.value = val })

const onDocChange = () => {
  segments.value = []
  meta.value = ''
  error.value = ''
}

const loadDocs = async () => {
  docsLoading.value = true
  try {
    const res = await getDocuments()
    docs.value = res?.data ?? res ?? []
  } catch (e) {
    console.error(e)
    error.value = '获取文档列表失败: ' + (e.message || '未知错误')
  }
  docsLoading.value = false
}

const translate = async () => {
  if (!docId.value) return
  loading.value = true; error.value = ''; segments.value = []
  try {
    const res = await translateDocument(docId.value, lang.value, force.value)
    const data = res?.data ?? res
    if (!data) {
      error.value = '翻译返回数据为空'
      loading.value = false
      return
    }
    // Handle the response - segments may be nested in data
    const segs = data.segments ?? data.data?.segments ?? []
    if (segs.length === 0) {
      error.value = '文档没有可翻译的文本内容，请确认文档已正确上传且包含文本'
      loading.value = false
      return
    }
    meta.value = `文档：${data.path ?? docId.value} | 引擎：${data.provider ?? 'unknown'}`
    segments.value = segs
  } catch (e) {
    const msg = e.message || ''
    if (msg.includes('DEEPL_API_KEY')) {
      error.value = '翻译服务未配置 API Key，当前使用模拟翻译模式'
    } else if (msg.includes('document not found')) {
      error.value = '文档未找到，请刷新文档列表后重试'
    } else if (msg.includes('no text to translate')) {
      error.value = '文档没有可提取的文本内容'
    } else {
      error.value = '翻译失败: ' + msg
    }
  }
  loading.value = false
}

onMounted(loadDocs)
</script>

<style scoped>
.bilingual-page { min-height: 100%; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; }

.hero {
  background: #fff; border-radius: 16px; padding: 28px 32px; margin-bottom: 24px;
  border: 1px solid #dadce0; display: flex; align-items: center;
}
.hero-inner { display: flex; align-items: center; gap: 16px; }
.hero-icon { font-size: 36px; }
.hero-text h1 { margin: 0 0 4px; font-size: 22px; font-weight: 400; color: #202124; }
.hero-text p { margin: 0; font-size: 14px; color: #5f6368; }

.control-panel {
  display: flex; align-items: center; gap: 12px; flex-wrap: wrap;
  background: #fff; border-radius: 16px; padding: 16px 20px;
  margin-bottom: 24px; border: 1px solid #dadce0;
}
.ctrl-select { flex: 1; min-width: 200px; }
.ctrl-lang { width: 120px; }
.meta-tag {
  font-size: 12px; color: #5f6368; background: #f1f3f4;
  padding: 4px 10px; border-radius: 100px;
}

.segments-header {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 12px;
}
.seg-count { font-size: 13px; color: #5f6368; }

.segments-list { display: flex; flex-direction: column; gap: 10px; }
.seg-row {
  display: flex; align-items: stretch; gap: 0;
  background: #fff; border-radius: 16px; overflow: hidden;
  border: 1px solid #dadce0;
  transition: all .2s;
}
.seg-row:hover {
  box-shadow: 0 1px 3px rgba(60,64,67,0.15), 0 4px 8px rgba(60,64,67,0.08);
}
.seg-num {
  width: 44px; display: flex; align-items: center; justify-content: center;
  background: #f8f9fa; color: #9aa0a6; font-size: 13px; font-weight: 500;
  flex-shrink: 0; border-right: 1px solid #e8eaed;
}
.seg-source, .seg-target { flex: 1; padding: 16px 20px; }
.seg-divider { width: 1px; background: #e8eaed; flex-shrink: 0; }
.seg-label {
  font-size: 11px; font-weight: 500; text-transform: uppercase;
  color: #1a73e8; margin-bottom: 8px; letter-spacing: .5px;
}
.seg-label.target { color: #1e8e3e; }
.seg-body { font-size: 14px; line-height: 1.8; color: #202124; white-space: pre-wrap; }
</style>
