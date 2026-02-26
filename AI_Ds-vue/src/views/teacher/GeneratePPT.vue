<template>
  <div class="generate-ppt">
    <page-header
      class="my-page-header"
      title="生成教学PPT"
      description="基于AI智能生成教学PPT内容"
    />

    <div class="content-container">
      <el-card class="form-card">
        <template #header>
          <div class="card-header">
            <span>PPT生成配置</span>
          </div>
        </template>

        <el-form :model="pptForm" label-position="top" :rules="pptRules" ref="pptFormRef">
          <el-form-item label="课件主题" prop="title">
            <el-input v-model="pptForm.title" placeholder="请输入PPT主题，如：二叉树的遍历" />
          </el-form-item>

          <el-form-item label="课件类型" prop="type">
            <el-radio-group v-model="pptForm.type">
              <el-radio label="lecture">教学讲义</el-radio>
              <el-radio label="review">复习资料</el-radio>
              <el-radio label="lab">实验指导</el-radio>
            </el-radio-group>
          </el-form-item>

          <el-form-item label="知识点选择" prop="topics">
            <el-select
              v-model="pptForm.topics"
              multiple
              collapse-tags
              placeholder="选择要包含的知识点"
              style="width: 100%"
            >
              <el-option
                v-for="item in knowledgeTopics"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="课件难度" prop="difficulty">
            <el-slider
              v-model="pptForm.difficulty"
              :step="1"
              :min="1"
              :max="5"
              :marks="difficultyMarks"
              show-stops
            />
          </el-form-item>

          <el-form-item label="包含内容" prop="includes">
            <el-checkbox-group v-model="pptForm.includes">
              <el-checkbox label="theory">理论知识</el-checkbox>
              <el-checkbox label="examples">示例代码</el-checkbox>
              <el-checkbox label="exercises">练习题</el-checkbox>
              <el-checkbox label="applications">实际应用</el-checkbox>
            </el-checkbox-group>
          </el-form-item>

          <el-form-item label="额外说明">
            <el-input
              v-model="pptForm.notes"
              type="textarea"
              :rows="3"
              placeholder="有什么特殊需求可以在这里说明"
            />
          </el-form-item>

          <el-form-item>
            <el-button type="primary" :loading="generating" @click="generatePPT">
              {{ generating ? 'AI生成中...' : '生成PPT' }}
            </el-button>
            <el-button @click="resetForm">重置</el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <!-- AI生成进度 -->
      <el-card v-if="generating" class="progress-card">
        <div class="generating-status">
          <el-icon class="is-loading" :size="24"><Loading /></el-icon>
          <span>AI正在生成PPT内容，请稍候...</span>
        </div>
        <div class="ai-stream-output" v-if="streamText">
          <pre class="stream-text">{{ streamText }}</pre>
        </div>
      </el-card>

      <el-card class="preview-card" v-if="showPreview && !generating">
        <template #header>
          <div class="card-header">
            <span>PPT预览（共{{ previewSlides.length }}页）</span>
            <el-button type="primary" :icon="Download" @click="downloadPPT">
              下载为文本
            </el-button>
          </div>
        </template>

        <div class="preview-container">
          <div class="slides-container">
            <div v-for="(slide, index) in previewSlides" :key="index" class="slide-item">
              <div class="slide-header">第 {{ index + 1 }} 页</div>
              <div class="slide-content" :class="{ 'title-slide': slide.isTitle }">
                <h3 v-if="slide.isTitle">{{ slide.title }}</h3>
                <div v-if="slide.isTitle" class="slide-subtitle">{{ pptTypeText }}</div>
                <template v-else>
                  <h4>{{ slide.title }}</h4>
                  <div class="slide-body">
                    <div v-if="slide.isCode" class="code-block">
                      <pre><code>{{ slide.content }}</code></pre>
                    </div>
                    <div v-else class="text-content" v-html="formatSlideContent(slide.content)"></div>
                  </div>
                </template>
              </div>
            </div>
          </div>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, Loading } from '@element-plus/icons-vue'
import PageHeader from '../../components/PageHeader.vue'
import axios from 'axios'

const pptFormRef = ref(null)
const generating = ref(false)
const showPreview = ref(false)
const previewSlides = ref([])
const streamText = ref('')

const pptForm = reactive({
  title: '',
  type: 'lecture',
  topics: [],
  difficulty: 3,
  includes: ['theory', 'examples'],
  notes: ''
})

const pptRules = {
  title: [
    { required: true, message: '请输入PPT主题', trigger: 'blur' },
    { min: 2, max: 50, message: '长度在2到50个字符', trigger: 'blur' }
  ],
  type: [{ required: true, message: '请选择课件类型', trigger: 'change' }],
  topics: [
    { required: true, message: '请至少选择一个知识点', trigger: 'change' },
    { type: 'array', min: 1, message: '至少选择一个知识点', trigger: 'change' }
  ]
}

const difficultyMarks = { 1: '入门', 2: '基础', 3: '中等', 4: '进阶', 5: '高级' }

const knowledgeTopics = [
  { value: 'array', label: '数组' },
  { value: 'linked_list', label: '链表' },
  { value: 'stack', label: '栈' },
  { value: 'queue', label: '队列' },
  { value: 'binary_tree', label: '二叉树' },
  { value: 'balanced_tree', label: '平衡树' },
  { value: 'heap', label: '堆' },
  { value: 'graph_representation', label: '图的表示' },
  { value: 'graph_traversal', label: '图的遍历' },
  { value: 'shortest_path', label: '最短路径算法' },
  { value: 'sorting', label: '排序算法' },
  { value: 'searching', label: '查找算法' },
  { value: 'hashing', label: '哈希技术' },
  { value: 'dynamic_programming', label: '动态规划' }
]

const selectedTopics = computed(() =>
  pptForm.topics.map(t => knowledgeTopics.find(k => k.value === t)?.label || t)
)

const pptTypeText = computed(() => {
  const map = { lecture: '教学讲义', review: '复习资料', lab: '实验指导' }
  return map[pptForm.type] || '教学资料'
})

const difficultyText = computed(() => difficultyMarks[pptForm.difficulty] || '中等')

// 构建AI提示词
const buildPrompt = () => {
  const topicNames = selectedTopics.value.join('、')
  const includeItems = pptForm.includes.map(i => {
    const map = { theory: '理论知识', examples: '示例代码', exercises: '练习题', applications: '实际应用' }
    return map[i] || i
  }).join('、')

  return `请为我生成一份数据结构课程的${pptTypeText.value}PPT大纲内容。

主题：${pptForm.title}
知识点：${topicNames}
难度级别：${difficultyText.value}
需要包含：${includeItems}
${pptForm.notes ? '额外要求：' + pptForm.notes : ''}

请按以下格式输出PPT内容，每页用"---PAGE---"分隔：
第一页是封面，只写标题。
之后每页格式为：
标题行（一行标题文字）
然后是该页的具体内容（要点用"- "开头，代码用\`\`\`包裹）。

请生成8-12页的内容，确保内容专业、准确、适合大学教学使用。`
}

// 解析AI返回的文本为幻灯片
const parseSlides = (text) => {
  const pages = text.split(/---PAGE---/).map(p => p.trim()).filter(Boolean)
  const slides = []

  pages.forEach((page, idx) => {
    const lines = page.split('\n').filter(l => l.trim())
    if (lines.length === 0) return

    if (idx === 0) {
      slides.push({ title: lines[0].replace(/^#+\s*/, '').trim() || pptForm.title, isTitle: true, content: '' })
      return
    }

    const title = lines[0].replace(/^#+\s*/, '').trim()
    const contentLines = lines.slice(1)
    const content = contentLines.join('\n')
    const isCode = content.includes('```') || contentLines.every(l => l.startsWith('  ') || l.startsWith('\t'))

    slides.push({ title, content: content.replace(/```\w*/g, '').replace(/```/g, '').trim(), isTitle: false, isCode })
  })

  // 如果解析失败，按段落分割
  if (slides.length <= 1) {
    const paragraphs = text.split(/\n\n+/).filter(p => p.trim())
    slides.length = 0
    slides.push({ title: pptForm.title, isTitle: true, content: '' })
    paragraphs.forEach(p => {
      const lines = p.split('\n').filter(l => l.trim())
      const title = lines[0].replace(/^#+\s*/, '').replace(/^\d+[.、]\s*/, '').trim()
      const content = lines.slice(1).join('\n').trim() || lines[0]
      const isCode = content.includes('int ') || content.includes('void ') || content.includes('#include')
      slides.push({ title, content, isTitle: false, isCode })
    })
  }

  return slides
}

const formatSlideContent = (content) => {
  if (!content) return ''
  return content
    .replace(/^- (.+)$/gm, '<li>$1</li>')
    .replace(/(<li>.*<\/li>)/s, '<ul>$1</ul>')
    .replace(/\n/g, '<br>')
}

// 调用DeepSeek AI生成PPT
const generatePPT = () => {
  pptFormRef.value.validate(async (valid) => {
    if (!valid) { ElMessage.error('请完善表单信息'); return }

    generating.value = true
    showPreview.value = false
    streamText.value = ''

    try {
      const prompt = buildPrompt()
      const response = await fetch('http://localhost:8081/api/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ userInput: prompt })
      })

      if (!response.ok) throw new Error('AI服务请求失败')

      const reader = response.body.getReader()
      const decoder = new TextDecoder('utf-8')
      let fullText = ''

      let reading = true
      while (reading) {
        const { done, value } = await reader.read()
        if (done) { reading = false; break }
        const chunk = decoder.decode(value, { stream: true })
        fullText += chunk
        streamText.value = fullText
      }

      previewSlides.value = parseSlides(fullText)
      if (previewSlides.value.length === 0) {
        previewSlides.value = [{ title: pptForm.title, isTitle: true, content: '' },
          { title: '内容', content: fullText, isTitle: false, isCode: false }]
      }
      showPreview.value = true
      ElMessage.success(`PPT生成完成，共${previewSlides.value.length}页`)
    } catch (error) {
      console.error('生成PPT出错:', error)
      ElMessage.error('生成PPT失败: ' + (error.message || '请稍后重试'))
    } finally {
      generating.value = false
    }
  })
}

const resetForm = () => {
  pptFormRef.value.resetFields()
  showPreview.value = false
  streamText.value = ''
  previewSlides.value = []
}

const downloadPPT = () => {
  if (previewSlides.value.length === 0) return
  let text = ''
  previewSlides.value.forEach((slide, i) => {
    text += `===== 第${i + 1}页 =====\n`
    text += slide.title + '\n'
    if (slide.content) text += slide.content + '\n'
    text += '\n'
  })
  const blob = new Blob([text], { type: 'text/plain;charset=utf-8' })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = `${pptForm.title || 'PPT'}_大纲.txt`
  link.click()
  URL.revokeObjectURL(link.href)
  ElMessage.success('PPT大纲已下载')
}
</script>

<style scoped>
.content-container { display: flex; flex-direction: column; gap: 20px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.progress-card { margin-top: 10px; }
.generating-status { display: flex; align-items: center; gap: 10px; margin-bottom: 15px; font-size: 15px; color: #1a73e8; }
.ai-stream-output { background: #f8f9fa; border-radius: 10px; padding: 15px; max-height: 300px; overflow-y: auto; border: 1px solid #dadce0; }
.stream-text { white-space: pre-wrap; font-size: 13px; line-height: 1.6; margin: 0; font-family: 'Microsoft YaHei', sans-serif; }
.preview-container { display: flex; flex-direction: column; gap: 20px; }
.slides-container { display: flex; flex-wrap: wrap; gap: 20px; justify-content: center; }
.slide-item { width: 320px; border: 1px solid #dadce0; border-radius: 16px; overflow: hidden; box-shadow: 0 1px 3px rgba(0,0,0,0.04); transition: all 0.25s; }
.slide-item:hover { box-shadow: 0 6px 16px rgba(0,0,0,0.08); transform: translateY(-2px); }
.slide-header { background: #f8f9fa; padding: 8px 12px; font-size: 14px; color: #5f6368; border-bottom: 1px solid #dadce0; }
.slide-content { padding: 15px; min-height: 180px; max-height: 250px; overflow: auto; display: flex; flex-direction: column; background-color: white; }
.title-slide { justify-content: center; align-items: center; text-align: center; }
.slide-subtitle { color: #5f6368; margin-top: 10px; }
.slide-body { font-size: 13px; line-height: 1.6; }
.code-block { background: #202124; border-radius: 8px; padding: 10px; font-family: monospace; font-size: 12px; overflow: auto; max-height: 180px; color: #dadce0; }
.text-content :deep(ul) { padding-left: 18px; margin: 5px 0; }
.text-content :deep(li) { margin-bottom: 4px; }
.generate-ppt :deep(.el-card) {
  border-radius: 16px;
  border: 1px solid #dadce0;
  box-shadow: 0 1px 3px rgba(0,0,0,0.04);
}
@media (max-width: 768px) { .slide-item { width: 100%; } }
</style>
