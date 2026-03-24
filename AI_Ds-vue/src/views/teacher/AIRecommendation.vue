<template>
  <div class="ai-recommendation">
    <page-header class="my-page-header" title="AI鏁欏寤鸿" description="鍩轰簬瀹為檯璇剧▼鏁版嵁鐨勬櫤鑳芥暀瀛﹀垎鏋愪笌寤鸿" />

    <div class="recommendation-content">
      <el-card class="form-card">
        <template #header>
          <div class="card-header"><span>鏁欏鍒嗘瀽閰嶇疆</span></div>
        </template>

        <el-form :model="analysisForm" label-position="top">
          <el-form-item label="鍒嗘瀽鍐呭">
            <el-checkbox-group v-model="analysisForm.content">
              <el-checkbox label="learning_status">瀛︿範鐘舵€佸垎鏋?/el-checkbox>
              <el-checkbox label="knowledge_points">鐭ヨ瘑鐐规帉鎻℃儏鍐?/el-checkbox>
              <el-checkbox label="improvement">鏀硅繘寤鸿</el-checkbox>
              <el-checkbox label="course_design">璇剧▼璁捐浼樺寲</el-checkbox>
            </el-checkbox-group>
          </el-form-item>

          <el-form-item>
            <el-button type="primary" :loading="loading" @click="generateRecommendation">
              {{ loading ? 'AI鍒嗘瀽涓?..' : '鐢熸垚AI鏁欏寤鸿' }}
            </el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <!-- 鏁版嵁鍔犺浇鐘舵€?-->
      <el-card v-if="dataLoading" class="result-card">
        <div class="loading-hint">
          <el-icon class="is-loading" :size="24"><Loading /></el-icon>
          <span>姝ｅ湪鍔犺浇璇剧▼鏁版嵁...</span>
        </div>
      </el-card>

      <!-- AI缁撴灉 - 娴佸紡杈撳嚭 -->
      <el-card v-if="aiContent || loading" class="result-card">
        <template #header>
          <div class="card-header">
            <span>AI鏁欏寤鸿</span>
            <el-button v-if="aiContent && !loading" type="primary" size="small" @click="copyResult">澶嶅埗缁撴灉</el-button>
          </div>
        </template>

        <div class="ai-content">
          <div class="ai-header">
            <el-avatar :size="36" src="https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png" />
            <span class="ai-name">AI鏁欏椤鹃棶</span>
            <el-tag v-if="loading" type="warning" size="small" effect="plain">鐢熸垚涓?..</el-tag>
          </div>
          <div class="ai-text" v-html="renderedContent"></div>
          <div v-if="loading" class="typing-cursor">|</div>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import api from '../../api'
import PageHeader from '../../components/PageHeader.vue'
import { buildApiUrl } from '../../config/runtime'

const loading = ref(false)
const dataLoading = ref(false)
const aiContent = ref('')

const analysisForm = reactive({
  content: ['learning_status', 'knowledge_points', 'improvement']
})

// 璇剧▼鏁版嵁
const courseData = ref(null)

const renderedContent = computed(() => {
  if (!aiContent.value) return ''
  return DOMPurify.sanitize(marked.parse(aiContent.value))
})

// 鍔犺浇鐪熷疄璇剧▼鏁版嵁
const loadCourseData = async () => {
  dataLoading.value = true
  try {
    const [expRes, subRes] = await Promise.all([
      api.getTeacherExperimentList(),
      api.getAllStudentExperiments()
    ])

    let experiments = []
    if (expRes?.data && Array.isArray(expRes.data)) experiments = expRes.data
    else if (Array.isArray(expRes)) experiments = expRes

    const studentCount = expRes?.studentCount || 49

    // 缁熻鏁版嵁
    const totalExperiments = experiments.length
    const avgSubmissionRate = experiments.length > 0
      ? Math.round(experiments.reduce((s, e) => s + (e.submissionCount || 0), 0) / experiments.length / studentCount * 100)
      : 0

    // 鍚勫疄楠屽畬鎴愮巼鍜屽钩鍧囧垎
    const experimentStats = experiments.map(e => ({
      name: e.name,
      submissionCount: e.submissionCount || 0,
      completionRate: studentCount > 0 ? Math.round((e.submissionCount || 0) / studentCount * 100) : 0,
      averageScore: e.averageScore || 0
    }))

    // 浣庡畬鎴愮巼瀹為獙
    const lowCompletionExps = experimentStats.filter(e => e.completionRate < 70)
    // 浣庡垎瀹為獙
    const lowScoreExps = experimentStats.filter(e => e.averageScore > 0 && e.averageScore < 60)

    courseData.value = {
      studentCount,
      totalExperiments,
      avgSubmissionRate,
      experimentStats,
      lowCompletionExps,
      lowScoreExps
    }
  } catch (e) {
    console.error('鍔犺浇璇剧▼鏁版嵁澶辫触:', e)
    ElMessage.warning('鍔犺浇璇剧▼鏁版嵁澶辫触锛屽皢浣跨敤鏈夐檺淇℃伅鐢熸垚寤鸿')
  } finally {
    dataLoading.value = false
  }
}

// 鏋勫缓prompt
const buildPrompt = () => {
  const sections = analysisForm.content
  let prompt = '浣犳槸涓€浣嶈祫娣辩殑楂樻牎鏁欏椤鹃棶銆傝鏍规嵁浠ヤ笅鐪熷疄璇剧▼鏁版嵁锛屼负鏁欏笀鎻愪緵涓撲笟鐨勬暀瀛﹀垎鏋愬拰寤鸿銆俓n\n'

  if (courseData.value) {
    const d = courseData.value
    prompt += `## 璇剧▼鍩烘湰淇℃伅\n`
    prompt += `- 璇剧▼锛氭暟鎹粨鏋刓n- 瀛︾敓鎬绘暟锛?{d.studentCount}浜篭n- 瀹為獙鎬绘暟锛?{d.totalExperiments}涓猏n- 骞冲潎鎻愪氦鐜囷細${d.avgSubmissionRate}%\n\n`

    prompt += `## 鍚勫疄楠屾暟鎹甛n`
    d.experimentStats.forEach(e => {
      prompt += `- ${e.name}锛氬畬鎴愮巼${e.completionRate}%锛屾彁浜?{e.submissionCount}浜猴紝骞冲潎鍒?{e.averageScore}\n`
    })
    prompt += '\n'

    if (d.lowCompletionExps.length > 0) {
      prompt += `## 浣庡畬鎴愮巼瀹為獙锛?70%锛塡n`
      d.lowCompletionExps.forEach(e => {
        prompt += `- ${e.name}锛氬畬鎴愮巼浠?{e.completionRate}%\n`
      })
      prompt += '\n'
    }

    if (d.lowScoreExps.length > 0) {
      prompt += `## 浣庡垎瀹為獙锛堝钩鍧囧垎<60锛塡n`
      d.lowScoreExps.forEach(e => {
        prompt += `- ${e.name}锛氬钩鍧囧垎${e.averageScore}\n`
      })
      prompt += '\n'
    }
  }

  prompt += '## 璇峰垎鏋愪互涓嬫柟闈細\n'
  if (sections.includes('learning_status')) prompt += '1. **瀛︿範鐘舵€佸垎鏋?*锛氬熀浜庢彁浜ょ巼鍜屾垚缁╂暟鎹紝鍒嗘瀽瀛︾敓鏁翠綋瀛︿範鐘舵€佸拰瓒嬪娍\n'
  if (sections.includes('knowledge_points')) prompt += '2. **鐭ヨ瘑鐐规帉鎻℃儏鍐?*锛氭牴鎹悇瀹為獙鐨勬垚缁╁拰瀹屾垚鐜囷紝鎺ㄦ柇瀛︾敓鍦ㄤ笉鍚岀煡璇嗙偣涓婄殑鎺屾彙绋嬪害\n'
  if (sections.includes('improvement')) prompt += '3. **鏀硅繘寤鸿**锛氶拡瀵硅杽寮辩幆鑺傛彁鍑哄叿浣撳彲鎿嶄綔鐨勬暀瀛︽敼杩涘缓璁紝鎸変紭鍏堢骇鎺掑簭\n'
  if (sections.includes('course_design')) prompt += '4. **璇剧▼璁捐浼樺寲**锛氬瀹為獙瀹夋帓銆侀毦搴︽搴︺€佹暀瀛﹁妭濂忔彁鍑轰紭鍖栧缓璁甛n'

  prompt += '\n璇风敤Markdown鏍煎紡杈撳嚭锛岀粨鏋勬竻鏅帮紝寤鸿鍏蜂綋鍙搷浣溿€?
  return prompt
}

// 娴佸紡璋冪敤DeepSeek
const generateRecommendation = async () => {
  if (loading.value) return
  if (analysisForm.content.length === 0) {
    ElMessage.warning('璇疯嚦灏戦€夋嫨涓€椤瑰垎鏋愬唴瀹?)
    return
  }

  loading.value = true
  aiContent.value = ''

  try {
    if (!courseData.value) await loadCourseData()

    const prompt = buildPrompt()

    const response = await fetch(buildApiUrl('/api/chat'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify({ userInput: prompt })
    })

    if (!response.ok) throw new Error('AI鏈嶅姟璇锋眰澶辫触: ' + response.status)

    const reader = response.body.getReader()
    const decoder = new TextDecoder('utf-8')

    let reading = true
    while (reading) {
      const { done, value } = await reader.read()
      if (done) { reading = false; break }
      aiContent.value += decoder.decode(value, { stream: true })
    }

    ElMessage.success('AI鏁欏寤鸿鐢熸垚瀹屾垚')
  } catch (e) {
    console.error('鐢熸垚澶辫触:', e)
    ElMessage.error('鐢熸垚澶辫触: ' + e.message)
  } finally {
    loading.value = false
  }
}

const copyResult = () => {
  navigator.clipboard.writeText(aiContent.value).then(() => {
    ElMessage.success('宸插鍒跺埌鍓创鏉?)
  })
}

onMounted(() => {
  loadCourseData()
})
</script>

<style scoped>
.recommendation-content { display: flex; flex-direction: column; gap: 20px; margin-bottom: 40px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.loading-hint { display: flex; align-items: center; gap: 10px; padding: 20px; color: #9aa0a6; }
.ai-content { padding: 10px 0; }
.ai-header { display: flex; align-items: center; gap: 10px; margin-bottom: 16px; }
.ai-name { font-weight: 600; color: #202124; }
.ai-text { line-height: 1.8; color: #202124; font-size: 14px; }
.ai-text :deep(h1), .ai-text :deep(h2), .ai-text :deep(h3) { margin-top: 20px; margin-bottom: 10px; color: #202124; }
.ai-text :deep(ul), .ai-text :deep(ol) { padding-left: 20px; }
.ai-text :deep(li) { margin-bottom: 6px; }
.ai-text :deep(strong) { color: #409eff; }
.ai-text :deep(code) { background: #f5f7fa; padding: 2px 6px; border-radius: 3px; font-size: 13px; }
.ai-text :deep(blockquote) { border-left: 4px solid #409eff; padding-left: 12px; color: #5f6368; margin: 12px 0; }
.typing-cursor { display: inline; animation: blink 1s infinite; font-weight: bold; color: #409eff; }
@keyframes blink { 0%, 100% { opacity: 1; } 50% { opacity: 0; } }
.my-page-header { padding: 20px; }
</style>

