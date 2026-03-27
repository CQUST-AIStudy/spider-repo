<template>
  <div class="generate-ppt">
    <page-header
      title="生成教学 PPT"
      description="根据课程主题、知识点和难度要求，快速生成可直接整理成课件的教学大纲。"
    />

    <div class="page-layout">
      <el-card class="form-card">
        <template #header>
          <div class="card-header">
            <span>生成配置</span>
          </div>
        </template>

        <el-form ref="pptFormRef" :model="pptForm" :rules="pptRules" label-position="top">
          <el-form-item label="PPT 主题" prop="title">
            <el-input v-model="pptForm.title" placeholder="例如：二叉树的遍历与应用" />
          </el-form-item>

          <el-form-item label="课件类型" prop="type">
            <el-radio-group v-model="pptForm.type">
              <el-radio label="lecture">课堂讲授</el-radio>
              <el-radio label="review">复习梳理</el-radio>
              <el-radio label="lab">实验指导</el-radio>
            </el-radio-group>
          </el-form-item>

          <el-form-item label="知识点" prop="topics">
            <el-select
              v-model="pptForm.topics"
              multiple
              collapse-tags
              collapse-tags-tooltip
              placeholder="选择本次 PPT 需要覆盖的知识点"
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

          <el-form-item label="内容难度" prop="difficulty">
            <el-slider
              v-model="pptForm.difficulty"
              :step="1"
              :min="1"
              :max="5"
              :marks="difficultyMarks"
              show-stops
            />
          </el-form-item>

          <el-form-item label="内容模块" prop="includes">
            <el-checkbox-group v-model="pptForm.includes">
              <el-checkbox label="theory">理论讲解</el-checkbox>
              <el-checkbox label="examples">示例代码</el-checkbox>
              <el-checkbox label="exercises">课堂练习</el-checkbox>
              <el-checkbox label="applications">应用场景</el-checkbox>
            </el-checkbox-group>
          </el-form-item>

          <el-form-item label="补充说明">
            <el-input
              v-model="pptForm.notes"
              type="textarea"
              :rows="4"
              placeholder="例如：偏向实验课、需要突出易错点、希望加入课堂讨论问题。"
            />
          </el-form-item>

          <div class="form-actions">
            <el-button type="primary" :loading="generating" @click="generatePPT">
              {{ generating ? '正在生成...' : '生成 PPT 大纲' }}
            </el-button>
            <el-button @click="resetForm">重置</el-button>
          </div>
        </el-form>
      </el-card>

      <el-card class="preview-card">
        <template #header>
          <div class="card-header">
            <span>内容预览</span>
            <el-button type="primary" plain :disabled="!previewSlides.length" @click="downloadPPT">
              下载文本
            </el-button>
          </div>
        </template>

        <div v-if="generating" class="loading-state">
          <el-icon class="is-loading" :size="22"><Loading /></el-icon>
          <span>AI 正在生成课件内容，请稍候。</span>
        </div>

        <div v-else-if="previewSlides.length" class="slides-grid">
          <article
            v-for="(slide, index) in previewSlides"
            :key="`${slide.title}-${index}`"
            class="slide-card"
            :class="{ 'slide-card--title': slide.isTitle }"
          >
            <header class="slide-card__header">第 {{ index + 1 }} 页</header>
            <div class="slide-card__body">
              <h3>{{ slide.title }}</h3>
              <p v-if="slide.isTitle" class="slide-card__subtitle">{{ pptTypeText }}</p>
              <div v-else-if="slide.isCode" class="code-block">
                <pre><code>{{ slide.content }}</code></pre>
              </div>
              <div v-else class="slide-card__content" v-html="formatSlideContent(slide.content)"></div>
            </div>
          </article>
        </div>

        <el-empty v-else description="生成后将在这里显示 PPT 大纲预览" />
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import PageHeader from '../../components/PageHeader.vue'
import { chatSend } from '../../api/tap'

const pptFormRef = ref(null)
const generating = ref(false)
const previewSlides = ref([])

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
    { required: true, message: '请输入 PPT 主题', trigger: 'blur' },
    { min: 2, max: 50, message: '主题长度保持在 2 到 50 个字符之间', trigger: 'blur' }
  ],
  type: [{ required: true, message: '请选择课件类型', trigger: 'change' }],
  topics: [
    { required: true, message: '请至少选择一个知识点', trigger: 'change' },
    { type: 'array', min: 1, message: '请至少选择一个知识点', trigger: 'change' }
  ]
}

const difficultyMarks = {
  1: '入门',
  2: '基础',
  3: '中等',
  4: '进阶',
  5: '挑战'
}

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
  { value: 'shortest_path', label: '最短路径' },
  { value: 'sorting', label: '排序算法' },
  { value: 'searching', label: '查找算法' },
  { value: 'hashing', label: '哈希' },
  { value: 'dynamic_programming', label: '动态规划' }
]

const selectedTopics = computed(() =>
  pptForm.topics.map(topic => knowledgeTopics.find(item => item.value === topic)?.label || topic)
)

const pptTypeText = computed(() => {
  const map = {
    lecture: '课堂讲授',
    review: '复习梳理',
    lab: '实验指导'
  }
  return map[pptForm.type] || '教学课件'
})

const difficultyText = computed(() => difficultyMarks[pptForm.difficulty] || '中等')

function buildPrompt() {
  const includesText = pptForm.includes.map(item => {
    const map = {
      theory: '理论讲解',
      examples: '示例代码',
      exercises: '课堂练习',
      applications: '应用场景'
    }
    return map[item] || item
  }).join('、')

  return [
    `请为我生成一份“${pptForm.title}”的数据结构课程 ${pptTypeText.value} PPT 大纲。`,
    `知识点：${selectedTopics.value.join('、')}`,
    `难度：${difficultyText.value}`,
    `需包含：${includesText}`,
    pptForm.notes ? `补充要求：${pptForm.notes}` : '',
    '',
    '输出要求：',
    '1. 使用 ---PAGE--- 分隔每一页。',
    '2. 第一页只输出标题。',
    '3. 后续页面先给页面标题，再给要点列表。',
    '4. 总页数控制在 6 到 10 页。'
  ].filter(Boolean).join('\n')
}

function parseSlides(text) {
  const pages = text
    .split(/---PAGE---/i)
    .map(item => item.trim())
    .filter(Boolean)

  if (!pages.length) return []

  return pages.map((page, index) => {
    const lines = page.split('\n').map(line => line.trim()).filter(Boolean)
    const title = lines[0]?.replace(/^#+\s*/, '') || (index === 0 ? pptForm.title : `第 ${index + 1} 页`)
    const content = lines.slice(1).join('\n')
    const isCode = content.includes('```')
    return {
      title,
      content: content.replace(/```[\w-]*/g, '').replace(/```/g, '').trim(),
      isTitle: index === 0,
      isCode
    }
  })
}

function formatSlideContent(content) {
  if (!content) return ''
  return content
    .replace(/^- (.+)$/gm, '<li>$1</li>')
    .replace(/(<li>.*<\/li>)/s, '<ul>$1</ul>')
    .replace(/\n/g, '<br>')
}

async function generatePPT() {
  const valid = await pptFormRef.value?.validate().catch(() => false)
  if (!valid) {
    ElMessage.error('请先完善表单信息')
    return
  }

  generating.value = true
  previewSlides.value = []

  try {
    const res = await chatSend(buildPrompt(), [])
    const data = res?.data ?? res
    const fullText = (data?.reply || '').trim()

    if (!fullText) {
      throw new Error('AI 未返回可用的 PPT 内容')
    }

    const slides = parseSlides(fullText)
    previewSlides.value = slides.length
      ? slides
      : [
          { title: pptForm.title, content: '', isTitle: true, isCode: false },
          { title: '内容', content: fullText, isTitle: false, isCode: false }
        ]

    ElMessage.success(`PPT 生成完成，共 ${previewSlides.value.length} 页`)
  } catch (error) {
    ElMessage.error(`生成失败：${error?.message || '请稍后重试'}`)
  } finally {
    generating.value = false
  }
}

function resetForm() {
  pptFormRef.value?.resetFields()
  previewSlides.value = []
}

function downloadPPT() {
  if (!previewSlides.value.length) return

  const content = previewSlides.value
    .map((slide, index) => `===== 第 ${index + 1} 页 =====\n${slide.title}\n${slide.content || ''}`)
    .join('\n\n')

  const blob = new Blob([content], { type: 'text/plain;charset=utf-8' })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = `${pptForm.title || '教学PPT大纲'}.txt`
  link.click()
  URL.revokeObjectURL(link.href)
}
</script>

<style scoped>
.generate-ppt {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.page-layout {
  display: grid;
  grid-template-columns: minmax(320px, 420px) minmax(0, 1fr);
  gap: 20px;
  align-items: start;
}

.form-card,
.preview-card {
  border-radius: 20px;
  border: 1px solid #dbe5ef;
  box-shadow: 0 12px 30px rgba(28, 52, 84, 0.06);
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-weight: 600;
  color: #1d3557;
}

.form-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.loading-state {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 18px 2px;
  color: #48607c;
}

.slides-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 16px;
}

.slide-card {
  overflow: hidden;
  border: 1px solid #dbe5ef;
  border-radius: 18px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fbff 100%);
}

.slide-card--title {
  background: linear-gradient(160deg, #eef5ff 0%, #f8fbff 100%);
}

.slide-card__header {
  padding: 10px 14px;
  border-bottom: 1px solid #e6edf5;
  font-size: 13px;
  color: #68809c;
}

.slide-card__body {
  padding: 18px;
}

.slide-card__body h3 {
  margin: 0 0 10px;
  font-size: 18px;
  line-height: 1.35;
  color: #16304f;
}

.slide-card__subtitle {
  margin: 0;
  color: #5f7690;
}

.slide-card__content {
  color: #31465f;
  line-height: 1.8;
}

.slide-card__content :deep(ul) {
  margin: 0;
  padding-left: 18px;
}

.slide-card__content :deep(li) {
  margin-bottom: 6px;
}

.code-block {
  overflow: auto;
  border-radius: 14px;
  background: #10233b;
  color: #f8fbff;
  padding: 14px;
}

.code-block pre {
  margin: 0;
}

@media (max-width: 1080px) {
  .page-layout {
    grid-template-columns: 1fr;
  }
}
</style>
