<template>
  <div class="generate-ppt">
    <page-header
      class="my-page-header"
      title="鐢熸垚鏁欏PPT"
      description="鍩轰簬AI鏅鸿兘鐢熸垚鏁欏PPT鍐呭"
    />

    <div class="content-container">
      <el-card class="form-card">
        <template #header>
          <div class="card-header">
            <span>PPT鐢熸垚閰嶇疆</span>
          </div>
        </template>

        <el-form :model="pptForm" label-position="top" :rules="pptRules" ref="pptFormRef">
          <el-form-item label="璇句欢涓婚" prop="title">
            <el-input v-model="pptForm.title" placeholder="璇疯緭鍏PT涓婚锛屽锛氫簩鍙夋爲鐨勯亶鍘? />
          </el-form-item>

          <el-form-item label="璇句欢绫诲瀷" prop="type">
            <el-radio-group v-model="pptForm.type">
              <el-radio label="lecture">鏁欏璁蹭箟</el-radio>
              <el-radio label="review">澶嶄範璧勬枡</el-radio>
              <el-radio label="lab">瀹為獙鎸囧</el-radio>
            </el-radio-group>
          </el-form-item>

          <el-form-item label="鐭ヨ瘑鐐归€夋嫨" prop="topics">
            <el-select
              v-model="pptForm.topics"
              multiple
              collapse-tags
              placeholder="閫夋嫨瑕佸寘鍚殑鐭ヨ瘑鐐?
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

          <el-form-item label="璇句欢闅惧害" prop="difficulty">
            <el-slider
              v-model="pptForm.difficulty"
              :step="1"
              :min="1"
              :max="5"
              :marks="difficultyMarks"
              show-stops
            />
          </el-form-item>

          <el-form-item label="鍖呭惈鍐呭" prop="includes">
            <el-checkbox-group v-model="pptForm.includes">
              <el-checkbox label="theory">鐞嗚鐭ヨ瘑</el-checkbox>
              <el-checkbox label="examples">绀轰緥浠ｇ爜</el-checkbox>
              <el-checkbox label="exercises">缁冧範棰?/el-checkbox>
              <el-checkbox label="applications">瀹為檯搴旂敤</el-checkbox>
            </el-checkbox-group>
          </el-form-item>

          <el-form-item label="棰濆璇存槑">
            <el-input
              v-model="pptForm.notes"
              type="textarea"
              :rows="3"
              placeholder="鏈変粈涔堢壒娈婇渶姹傚彲浠ュ湪杩欓噷璇存槑"
            />
          </el-form-item>

          <el-form-item>
            <el-button type="primary" :loading="generating" @click="generatePPT">
              {{ generating ? 'AI鐢熸垚涓?..' : '鐢熸垚PPT' }}
            </el-button>
            <el-button @click="resetForm">閲嶇疆</el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <!-- AI鐢熸垚杩涘害 -->
      <el-card v-if="generating" class="progress-card">
        <div class="generating-status">
          <el-icon class="is-loading" :size="24"><Loading /></el-icon>
          <span>AI姝ｅ湪鐢熸垚PPT鍐呭锛岃绋嶅€?..</span>
        </div>
        <div class="ai-stream-output" v-if="streamText">
          <pre class="stream-text">{{ streamText }}</pre>
        </div>
      </el-card>

      <el-card class="preview-card" v-if="showPreview && !generating">
        <template #header>
          <div class="card-header">
            <span>PPT棰勮锛堝叡{{ previewSlides.length }}椤碉級</span>
            <el-button type="primary" :icon="Download" @click="downloadPPT">
              涓嬭浇涓烘枃鏈?            </el-button>
          </div>
        </template>

        <div class="preview-container">
          <div class="slides-container">
            <div v-for="(slide, index) in previewSlides" :key="index" class="slide-item">
              <div class="slide-header">绗?{{ index + 1 }} 椤?/div>
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
import { buildApiUrl } from '../../config/runtime'
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
    { required: true, message: '璇疯緭鍏PT涓婚', trigger: 'blur' },
    { min: 2, max: 50, message: '闀垮害鍦?鍒?0涓瓧绗?, trigger: 'blur' }
  ],
  type: [{ required: true, message: '璇烽€夋嫨璇句欢绫诲瀷', trigger: 'change' }],
  topics: [
    { required: true, message: '璇疯嚦灏戦€夋嫨涓€涓煡璇嗙偣', trigger: 'change' },
    { type: 'array', min: 1, message: '鑷冲皯閫夋嫨涓€涓煡璇嗙偣', trigger: 'change' }
  ]
}

const difficultyMarks = { 1: '鍏ラ棬', 2: '鍩虹', 3: '涓瓑', 4: '杩涢樁', 5: '楂樼骇' }

const knowledgeTopics = [
  { value: 'array', label: '鏁扮粍' },
  { value: 'linked_list', label: '閾捐〃' },
  { value: 'stack', label: '鏍? },
  { value: 'queue', label: '闃熷垪' },
  { value: 'binary_tree', label: '浜屽弶鏍? },
  { value: 'balanced_tree', label: '骞宠　鏍? },
  { value: 'heap', label: '鍫? },
  { value: 'graph_representation', label: '鍥剧殑琛ㄧず' },
  { value: 'graph_traversal', label: '鍥剧殑閬嶅巻' },
  { value: 'shortest_path', label: '鏈€鐭矾寰勭畻娉? },
  { value: 'sorting', label: '鎺掑簭绠楁硶' },
  { value: 'searching', label: '鏌ユ壘绠楁硶' },
  { value: 'hashing', label: '鍝堝笇鎶€鏈? },
  { value: 'dynamic_programming', label: '鍔ㄦ€佽鍒? }
]

const selectedTopics = computed(() =>
  pptForm.topics.map(t => knowledgeTopics.find(k => k.value === t)?.label || t)
)

const pptTypeText = computed(() => {
  const map = { lecture: '鏁欏璁蹭箟', review: '澶嶄範璧勬枡', lab: '瀹為獙鎸囧' }
  return map[pptForm.type] || '鏁欏璧勬枡'
})

const difficultyText = computed(() => difficultyMarks[pptForm.difficulty] || '涓瓑')

// 鏋勫缓AI鎻愮ず璇?const buildPrompt = () => {
  const topicNames = selectedTopics.value.join('銆?)
  const includeItems = pptForm.includes.map(i => {
    const map = { theory: '鐞嗚鐭ヨ瘑', examples: '绀轰緥浠ｇ爜', exercises: '缁冧範棰?, applications: '瀹為檯搴旂敤' }
    return map[i] || i
  }).join('銆?)

  return `璇蜂负鎴戠敓鎴愪竴浠芥暟鎹粨鏋勮绋嬬殑${pptTypeText.value}PPT澶х翰鍐呭銆?
涓婚锛?{pptForm.title}
鐭ヨ瘑鐐癸細${topicNames}
闅惧害绾у埆锛?{difficultyText.value}
闇€瑕佸寘鍚細${includeItems}
${pptForm.notes ? '棰濆瑕佹眰锛? + pptForm.notes : ''}

璇锋寜浠ヤ笅鏍煎紡杈撳嚭PPT鍐呭锛屾瘡椤电敤"---PAGE---"鍒嗛殧锛?绗竴椤垫槸灏侀潰锛屽彧鍐欐爣棰樸€?涔嬪悗姣忛〉鏍煎紡涓猴細
鏍囬琛岋紙涓€琛屾爣棰樻枃瀛楋級
鐒跺悗鏄椤电殑鍏蜂綋鍐呭锛堣鐐圭敤"- "寮€澶达紝浠ｇ爜鐢╘`\`\`鍖呰９锛夈€?
璇风敓鎴?-12椤电殑鍐呭锛岀‘淇濆唴瀹逛笓涓氥€佸噯纭€侀€傚悎澶у鏁欏浣跨敤銆俙
}

// 瑙ｆ瀽AI杩斿洖鐨勬枃鏈负骞荤伅鐗?const parseSlides = (text) => {
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

  // 濡傛灉瑙ｆ瀽澶辫触锛屾寜娈佃惤鍒嗗壊
  if (slides.length <= 1) {
    const paragraphs = text.split(/\n\n+/).filter(p => p.trim())
    slides.length = 0
    slides.push({ title: pptForm.title, isTitle: true, content: '' })
    paragraphs.forEach(p => {
      const lines = p.split('\n').filter(l => l.trim())
      const title = lines[0].replace(/^#+\s*/, '').replace(/^\d+[.銆乚\s*/, '').trim()
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

// 璋冪敤DeepSeek AI鐢熸垚PPT
const generatePPT = () => {
  pptFormRef.value.validate(async (valid) => {
    if (!valid) { ElMessage.error('璇峰畬鍠勮〃鍗曚俊鎭?); return }

    generating.value = true
    showPreview.value = false
    streamText.value = ''

    try {
      const prompt = buildPrompt()
      const response = await fetch(buildApiUrl('/api/chat'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ userInput: prompt })
      })

      if (!response.ok) throw new Error('AI鏈嶅姟璇锋眰澶辫触')

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
          { title: '鍐呭', content: fullText, isTitle: false, isCode: false }]
      }
      showPreview.value = true
      ElMessage.success(`PPT鐢熸垚瀹屾垚锛屽叡${previewSlides.value.length}椤礰)
    } catch (error) {
      console.error('鐢熸垚PPT鍑洪敊:', error)
      ElMessage.error('鐢熸垚PPT澶辫触: ' + (error.message || '璇风◢鍚庨噸璇?))
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
    text += `===== 绗?{i + 1}椤?=====\n`
    text += slide.title + '\n'
    if (slide.content) text += slide.content + '\n'
    text += '\n'
  })
  const blob = new Blob([text], { type: 'text/plain;charset=utf-8' })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = `${pptForm.title || 'PPT'}_澶х翰.txt`
  link.click()
  URL.revokeObjectURL(link.href)
  ElMessage.success('PPT澶х翰宸蹭笅杞?)
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


