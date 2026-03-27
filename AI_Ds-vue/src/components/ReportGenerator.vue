<template>
  <div class="report-generator">
    <el-card class="report-card">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <span>{{ isEditMode ? '编辑报告' : '预览报告' }}</span>
            <el-button v-if="isEditMode" type="success" @click="saveEdits">保存修改</el-button>
          </div>
          <div class="header-right">
            <el-button v-if="!isEditMode" type="primary" @click="toggleEditMode">编辑报告</el-button>
            <el-button v-else @click="cancelEdits">取消编辑</el-button>
          </div>
        </div>
      </template>

      <div v-if="isEditMode" class="edit-mode">
        <el-form :model="editingData" label-position="top">
          <el-divider content-position="left">基本信息</el-divider>
          <el-row :gutter="20">
            <el-col :span="8">
              <el-form-item label="课程名称">
                <el-input v-model="editingData.courseName" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="实验项目">
                <el-input v-model="editingData.experimentName" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="机房名称">
                <el-input v-model="editingData.labName" />
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="20">
            <el-col :span="8">
              <el-form-item label="上机时间">
                <el-date-picker
                  v-model="editingData.labTime"
                  type="date"
                  placeholder="选择日期"
                  format="YYYY/MM/DD"
                  value-format="YYYY/MM/DD"
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="指导教师">
                <el-input v-model="editingData.teacherName" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="上机成绩">
                <el-input v-model="editingData.score" />
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="20">
            <el-col :span="8">
              <el-form-item label="学生姓名">
                <el-input v-model="editingData.studentName" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="学号">
                <el-input v-model="editingData.studentId" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="专业班级">
                <el-input v-model="editingData.className" />
              </el-form-item>
            </el-col>
          </el-row>

          <el-divider content-position="left">报告内容</el-divider>
          <el-form-item label="一、实验目的和要求">
            <el-input v-model="editingData.purpose" type="textarea" :rows="4" />
          </el-form-item>
          <el-form-item label="二、实验环境">
            <el-input v-model="editingData.requirements" type="textarea" :rows="4" />
          </el-form-item>
          <el-form-item label="三、实验内容">
            <el-input v-model="editingData.tasks" type="textarea" :rows="5" />
          </el-form-item>
          <el-form-item label="四、实验步骤与关键代码">
            <el-input v-model="editingData.steps" type="textarea" :rows="10" />
          </el-form-item>
          <el-form-item label="五、实验结果与问题分析">
            <el-input v-model="editingData.results" type="textarea" :rows="6" />
          </el-form-item>
          <el-form-item label="六、实验总结">
            <el-input v-model="editingData.summary" type="textarea" :rows="6" />
          </el-form-item>
          <el-form-item label="教师评语">
            <el-input v-model="editingData.teacherComment" type="textarea" :rows="5" />
          </el-form-item>
        </el-form>
      </div>

      <div v-else class="report-preview">
        <div class="report-container">
          <div class="report-title">
            <div class="university-name">重庆科技大学</div>
            <div class="report-type">上机实验报告</div>
          </div>

          <div v-if="hasTeacherReview" class="teacher-review-banner">
            <div class="teacher-review-top">
              <div class="teacher-review-score">
                <span class="score-label">教师评分</span>
                <span class="score-value">{{ displayScore }}</span>
              </div>
              <div class="teacher-review-meta">
                <span>教师评语已同步到报告</span>
              </div>
            </div>
            <div v-if="experimentData.teacherComment" class="teacher-comment-block handwritten-text">
              <div class="teacher-comment-label">教师评语</div>
              <pre>{{ experimentData.teacherComment }}</pre>
            </div>
          </div>

          <table class="info-table">
            <tr>
              <td class="label-cell">课程名称</td>
              <td class="value-cell" colspan="2">{{ experimentData.courseName || '课程待补充' }}</td>
              <td class="label-cell">实验项目</td>
              <td class="value-cell" colspan="2">{{ experimentData.experimentName || '实验待补充' }}</td>
            </tr>
            <tr>
              <td class="label-cell">机房名称</td>
              <td class="value-cell" colspan="2">{{ experimentData.labName || '实验机房' }}</td>
              <td class="label-cell">上机时间</td>
              <td class="value-cell" colspan="2">{{ experimentData.labTime || currentDate }}</td>
            </tr>
            <tr>
              <td class="label-cell">指导教师</td>
              <td class="value-cell" colspan="2">{{ experimentData.teacherName || '指导教师' }}</td>
              <td class="label-cell">上机成绩</td>
              <td class="value-cell" colspan="2">{{ displayScore }}</td>
            </tr>
            <tr>
              <td class="label-cell">学生姓名</td>
              <td class="value-cell">{{ experimentData.studentName || '未命名学生' }}</td>
              <td class="label-cell">学号</td>
              <td class="value-cell">{{ experimentData.studentId || '-' }}</td>
              <td class="label-cell">专业班级</td>
              <td class="value-cell">{{ experimentData.className || '-' }}</td>
            </tr>
          </table>

          <div class="report-content-sections">
            <section class="content-section">
              <div class="section-title">一、实验目的和要求</div>
              <div class="section-content white-space-pre">{{ experimentData.purpose || '待补充。' }}</div>
            </section>

            <section class="content-section">
              <div class="section-title">二、实验环境</div>
              <div class="section-content white-space-pre">{{ experimentData.requirements || '待补充。' }}</div>
            </section>

            <section class="content-section">
              <div class="section-title">三、实验内容</div>
              <div class="section-content white-space-pre">{{ experimentData.tasks || '待补充。' }}</div>
            </section>

            <section class="content-section">
              <div class="section-title">四、实验步骤与关键代码</div>
              <div class="section-content markdown-shell">
                <div v-html="processContent(experimentData.steps || '')" class="markdown-content"></div>
              </div>
            </section>

            <section class="content-section">
              <div class="section-title">五、实验结果与问题分析</div>
              <div class="section-content white-space-pre">{{ experimentData.results || '待补充。' }}</div>
            </section>

            <section class="content-section">
              <div class="section-title">六、实验总结</div>
              <div class="section-content white-space-pre">{{ experimentData.summary || '待补充。' }}</div>
            </section>
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { ensureHandwritingFont } from '@/utils/handwritingFont'

const props = defineProps({
  reportData: {
    type: Object,
    default: () => ({}),
  },
})

const emit = defineEmits(['update:reportData', 'report-saved'])

const experimentData = ref({})
const editingData = ref({})
const isEditMode = ref(false)
const currentDate = new Date().toLocaleDateString()

const displayScore = computed(() => {
  const value = experimentData.value?.score
  return value === null || value === undefined || value === '' ? '' : value
})

const hasTeacherReview = computed(() => Boolean(displayScore.value || experimentData.value?.teacherComment))

const processContent = content => {
  if (!content) return ''
  const processedContent = content.replace(
    /<div class="comment-image-container" data-image="(.*?)"><\/div>/g,
    (_, imageDataUrl) => `<div class="teacher-comment-image"><img src="${imageDataUrl}" alt="教师评语" /></div>`,
  )
  const html = marked.parse(processedContent)
  return DOMPurify.sanitize(html)
}

watch(
  () => props.reportData,
  newData => {
    if (newData && Object.keys(newData).length > 0) {
      experimentData.value = { ...newData }
    }
  },
  { deep: true, immediate: true },
)

onMounted(() => {
  ensureHandwritingFont()
})

function updateReport() {
  if (props.reportData && Object.keys(props.reportData).length > 0) {
    experimentData.value = { ...props.reportData }
  }
}

defineExpose({ updateReport })

function toggleEditMode() {
  editingData.value = JSON.parse(JSON.stringify(experimentData.value || {}))
  isEditMode.value = true
}

function saveEdits() {
  experimentData.value = JSON.parse(JSON.stringify(editingData.value || {}))
  emit('update:reportData', experimentData.value)
  emit('report-saved', experimentData.value)
  isEditMode.value = false
  ElMessage.success('报告已更新')
}

function cancelEdits() {
  ElMessageBox.confirm('未保存的修改会丢失，确定取消编辑吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '继续编辑',
    type: 'warning',
  })
    .then(() => {
      isEditMode.value = false
    })
    .catch(() => {})
}
</script>

<style scoped>
.report-generator {
  width: 100%;
  margin: 0 auto;
}

.report-card {
  margin-bottom: 20px;
}

.card-header,
.header-left,
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.card-header {
  justify-content: space-between;
}

.edit-mode,
.report-preview {
  padding: 20px;
}

.report-preview {
  background: #fff;
}

.report-container {
  max-width: 1024px;
  margin: 0 auto;
  padding: 20px;
  background: #fff;
}

.report-title {
  text-align: center;
  margin-bottom: 24px;
}

.university-name,
.report-type {
  font-family: 'STHeiti', 'SimHei', sans-serif;
  font-size: 30px;
  font-weight: 700;
  color: #111827;
}

.report-type {
  margin-top: 8px;
}

.teacher-review-banner {
  margin-bottom: 18px;
  padding: 18px 20px;
  border: 2px solid #f3b8b8;
  border-radius: 14px;
  background: linear-gradient(180deg, #fff7f7, #fff1f1);
}

.teacher-review-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.teacher-review-score {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.teacher-review-score .score-label {
  color: #991b1b;
  font-weight: 600;
}

.teacher-review-score .score-value {
  font-size: 28px;
  font-weight: 700;
  color: #b91c1c;
}

.teacher-review-meta {
  font-size: 13px;
  color: #7f1d1d;
}

.teacher-comment-block {
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px dashed rgba(185, 28, 28, 0.35);
}

.teacher-comment-label {
  margin-bottom: 8px;
  font-size: 14px;
  color: #991b1b;
  font-weight: 600;
}

.teacher-comment-block pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  color: #cc1f1f;
  font-size: 20px;
  line-height: 1.7;
}

.info-table {
  width: 100%;
  border-collapse: collapse;
  border: 2px solid #111827;
}

.label-cell,
.value-cell {
  border: 1px solid #111827;
  padding: 12px 8px;
  text-align: center;
  vertical-align: middle;
  font-size: 15px;
  font-family: 'SimSun', serif;
}

.label-cell {
  width: 12%;
}

.report-content-sections {
  width: 100%;
}

.content-section {
  border-left: 2px solid #111827;
  border-right: 2px solid #111827;
  border-bottom: 1px solid #111827;
  padding: 18px 24px;
}

.content-section:last-child {
  border-bottom: 2px solid #111827;
}

.section-title {
  margin-bottom: 12px;
  font-size: 16px;
  font-family: 'SimSun', serif;
}

.section-content {
  font-size: 15px;
  line-height: 1.8;
  color: #111827;
}

.white-space-pre {
  white-space: pre-line;
}

.markdown-shell {
  background: #fafafa;
  border-radius: 8px;
}

.markdown-content :deep(pre) {
  margin: 0;
  padding: 12px;
  overflow: auto;
  background: #f3f4f6;
  border-radius: 8px;
}

.markdown-content :deep(code) {
  font-family: 'Consolas', 'Courier New', monospace;
}

.teacher-comment-image {
  margin: 14px auto;
  max-width: 520px;
}

.teacher-comment-image img {
  width: 100%;
  border-radius: 10px;
  border: 1px solid #d1d5db;
  background: #fff;
}

@media print {
  .report-card {
    box-shadow: none;
    border: none;
  }
}
</style>
