<template>
  <div class="result-block">
    <div class="result-header">
      <p v-if="meta" class="meta-text">{{ meta }}</p>
      <div class="actions">
        <el-button size="small" plain @click="copyText">
          <el-icon><DocumentCopy /></el-icon> {{ copied ? '已复制' : '复制' }}
        </el-button>
        <el-button size="small" plain @click="downloadMd">
          <el-icon><Download /></el-icon> 下载
        </el-button>
        <el-button size="small" plain @click="showRaw = !showRaw">
          <el-icon><Edit /></el-icon> {{ showRaw ? '渲染' : '源码' }}
        </el-button>
      </div>
    </div>
    <el-input
      v-if="showRaw"
      type="textarea"
      :model-value="result"
      readonly
      :rows="12"
      class="raw-textarea"
    />
    <div v-else class="markdown-body" v-html="renderedHtml"></div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { DocumentCopy, Download, Edit } from '@element-plus/icons-vue'
import MarkdownIt from 'markdown-it'

const props = defineProps({
  result: { type: String, default: '' },
  meta: { type: String, default: '' }
})

const md = new MarkdownIt({ html: true, linkify: true, typographer: true })
const showRaw = ref(false)
const copied = ref(false)

const renderedHtml = computed(() => md.render(props.result))

const copyText = () => {
  navigator.clipboard.writeText(props.result)
  copied.value = true
  setTimeout(() => { copied.value = false }, 2000)
}

const downloadMd = () => {
  const blob = new Blob([props.result], { type: 'text/markdown' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url; a.download = `summary-${Date.now()}.md`; a.click()
  URL.revokeObjectURL(url)
}
</script>

<style scoped>
.result-block {
  margin-top: 20px;
  border: 1px solid #dadce0;
  border-radius: 16px;
  overflow: hidden;
  background: #fff;
}
.result-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 12px 20px;
  background: #f8f9fa;
  border-bottom: 1px solid #dadce0;
}
.meta-text { color: #5f6368; font-size: 12px; margin: 0; }
.actions { display: flex; gap: 6px; }
.raw-textarea :deep(.el-textarea__inner) {
  font-family: 'Menlo', 'Consolas', monospace;
  font-size: 12px;
  background: #f8f9fa;
  border: none;
  border-radius: 0;
}
.markdown-body {
  padding: 24px;
  font-size: 14px;
  line-height: 1.8;
  color: #202124;
}
.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3) {
  margin-top: 20px; margin-bottom: 10px;
  color: #202124; font-weight: 700;
}
.markdown-body :deep(h2) { font-size: 18px; padding-bottom: 6px; border-bottom: 2px solid #dadce0; }
.markdown-body :deep(table) { border-collapse: collapse; width: 100%; margin: 12px 0; }
.markdown-body :deep(th),
.markdown-body :deep(td) { border: 1px solid #dadce0; padding: 8px 12px; text-align: left; }
.markdown-body :deep(th) { background: #f8f9fa; font-weight: 600; color: #202124; }
.markdown-body :deep(code) {
  background: #f1f3f4; padding: 2px 6px; border-radius: 4px;
  font-size: 13px; color: #1a73e8;
}
.markdown-body :deep(pre) {
  background: #202124; padding: 16px; border-radius: 8px;
  overflow-x: auto; margin: 12px 0;
}
.markdown-body :deep(pre code) { background: none; padding: 0; color: #dadce0; }
.markdown-body :deep(blockquote) {
  border-left: 4px solid #1a73e8; padding-left: 16px;
  color: #5f6368; margin: 12px 0;
}
.markdown-body :deep(a) { color: #1a73e8; text-decoration: none; }
.markdown-body :deep(a:hover) { text-decoration: underline; }
.markdown-body :deep(ul), .markdown-body :deep(ol) { padding-left: 20px; }
.markdown-body :deep(li) { margin: 4px 0; }
</style>
