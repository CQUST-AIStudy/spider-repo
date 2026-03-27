<template>
  <div class="leetcode-practice">
    <!-- 题目详情区域 -->
    <div class="problem-section">
      <div class="problem-header">
        <div class="problem-title">
          <h2>{{ problem.problemCode }} {{ problem.title }}</h2>
          <el-tag :type="difficultyType" size="large">{{ problem.difficulty }}</el-tag>
        </div>
        <div class="problem-actions">
          <el-button @click="showSolution = !showSolution" type="info" plain>
            {{ showSolution ? '隐藏题解' : '查看题解' }}
          </el-button>
          <el-button @click="resetCode" type="warning" plain>重置代码</el-button>
        </div>
      </div>

      <!-- 题目内容 -->
      <div class="problem-content">
        <div class="problem-description">
          <div class="content-section">
            <h3>题目描述</h3>
            <div class="formatted-content" v-html="renderedProblemText"></div>
          </div>
          
          <div class="content-section" v-if="problem.examples">
            <h3>示例</h3>
            <div class="examples-container">
              <div 
                v-for="(example, index) in parsedExamples" 
                :key="index" 
                class="example-item"
              >
                <h4>示例 {{ index + 1 }}:</h4>
                <div class="example-content">
                  <div class="example-input">
                    <strong>输入:</strong> <code>{{ example.input }}</code>
                  </div>
                  <div class="example-output">
                    <strong>输出:</strong> <code>{{ example.output }}</code>
                  </div>
                  <div v-if="example.explanation" class="example-explanation">
                    <strong>解释:</strong> {{ example.explanation }}
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div class="content-section" v-if="problem.constraints">
            <h3>提示</h3>
            <div class="constraints-content" v-html="renderedConstraints"></div>
          </div>
        </div>
      </div>

      <!-- 官方题解 -->
      <el-collapse v-if="showSolution" class="solution-section">
        <el-collapse-item name="solution">
          <template #title>
            <div class="solution-title">
              <el-icon><Document /></el-icon>
              <span>官方题解</span>
            </div>
          </template>
          <div class="solution-content">
            <div class="solution-approach" v-if="parsedSolution.approach">
              <h4>解题思路</h4>
              <div class="approach-content" v-html="parsedSolution.approach"></div>
            </div>
            
            <div class="solution-code" v-if="parsedSolution.code">
              <h4>参考代码</h4>
              <el-tabs v-model="solutionLanguage" class="solution-tabs">
                <el-tab-pane 
                  v-for="(codeBlock, lang) in parsedSolution.code" 
                  :key="lang"
                  :label="getLanguageLabel(lang)" 
                  :name="lang"
                >
                  <pre class="solution-code-block"><code>{{ codeBlock }}</code></pre>
                </el-tab-pane>
              </el-tabs>
            </div>

            <div class="solution-complexity" v-if="parsedSolution.complexity">
              <h4>复杂度分析</h4>
              <div class="complexity-content" v-html="parsedSolution.complexity"></div>
            </div>
          </div>
        </el-collapse-item>
      </el-collapse>
    </div>

    <!-- 代码编辑区域 -->
    <div class="code-section">
      <div class="code-header">
        <div class="language-selector">
          <el-select v-model="selectedLanguage" @change="onLanguageChange">
            <el-option label="Java" value="java" />
            <el-option label="Python" value="python" />
            <el-option label="C" value="c" />
            <el-option label="C++" value="cpp" />
            <el-option label="JavaScript" value="javascript" />
          </el-select>
        </div>
        <div class="code-actions">
          <el-button @click="runCode" :loading="running" type="primary" plain>
            运行代码
          </el-button>
          <el-button @click="submitCode" :loading="submitting" type="success">
            提交解答
          </el-button>
        </div>
      </div>

      <!-- 代码编辑器 -->
      <div class="code-editor" @click="focusEditor">
        <codemirror
          ref="editorRef"
          v-model="code"
          :extensions="editorExtensions"
          :autofocus="false"
          :tab-size="4"
          :style="{ height: '100%' }"
          @ready="onEditorReady"
        />
      </div>

      <!-- 测试用例输入 -->
      <div class="test-input">
        <el-tabs v-model="activeTab">
          <el-tab-pane label="测试用例" name="testcase">
            <el-input
              v-model="testInput"
              type="textarea"
              :rows="4"
              placeholder="输入测试用例，每行一个..."
            />
          </el-tab-pane>
          <el-tab-pane label="运行结果" name="result" v-if="runResult">
            <div class="run-result">
              <div class="result-status" :class="runResult.status">
                <el-icon><Check v-if="runResult.status === 'success'" /><Close v-else /></el-icon>
                {{ runResult.status === 'success' ? '运行成功' : '运行失败' }}
              </div>
              <div class="result-content">
                <pre>{{ runResult.output }}</pre>
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </div>

    <!-- 提交结果弹窗 -->
    <el-dialog
      v-model="showSubmitResult"
      title="提交结果"
      width="80%"
      :close-on-click-modal="false"
    >
      <div v-if="submitResult" class="submit-result">
        <div class="result-header">
          <div class="status" :class="submitResult.status">
            <el-icon><Check v-if="submitResult.accepted" /><Close v-else /></el-icon>
            {{ submitResult.status === 'unavailable' ? '评测暂不可用' : (submitResult.accepted ? '通过' : '未通过') }}
          </div>
          <div class="score" v-if="submitResult.score !== null && submitResult.score !== undefined">
            得分: {{ submitResult.score }}/100
          </div>
        </div>

        <!-- AI评测结果 -->
        <div class="ai-feedback" v-if="submitResult.aiFeedback">
          <h3>AI 评测反馈</h3>
          <div class="feedback-content" v-html="renderedAiFeedback"></div>
        </div>

        <!-- 执行详情 -->
        <div class="execution-details" v-if="submitResult.details">
          <el-descriptions title="执行详情" :column="2" border>
            <el-descriptions-item label="执行时间">
              {{ submitResult.details.runtime || 'N/A' }}
            </el-descriptions-item>
            <el-descriptions-item label="内存消耗">
              {{ submitResult.details.memory || 'N/A' }}
            </el-descriptions-item>
            <el-descriptions-item label="通过用例">
              {{ submitResult.details.passedCases || 0 }} / {{ submitResult.details.totalCases || 0 }}
            </el-descriptions-item>
            <el-descriptions-item label="错误信息" v-if="submitResult.details.error">
              <pre class="error-message">{{ submitResult.details.error }}</pre>
            </el-descriptions-item>
          </el-descriptions>
        </div>

        <!-- 技能提升建议 -->
        <div class="skill-suggestions" v-if="submitResult.skillSuggestions">
          <h3>技能提升建议</h3>
          <el-tag
            v-for="suggestion in submitResult.skillSuggestions"
            :key="suggestion"
            class="suggestion-tag"
            type="info"
          >
            {{ suggestion }}
          </el-tag>
        </div>
      </div>

      <template #footer>
        <el-button @click="showSubmitResult = false">关闭</el-button>
        <el-button type="primary" @click="continuePractice">继续练习</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, Close, Document } from '@element-plus/icons-vue'
import { Codemirror } from 'vue-codemirror'
import { javascript } from '@codemirror/lang-javascript'
import { python } from '@codemirror/lang-python'
import { java } from '@codemirror/lang-java'
import { cpp } from '@codemirror/lang-cpp'
import { oneDark } from '@codemirror/theme-one-dark'
import { EditorView } from '@codemirror/view'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import api from '@/api'
import { getCurrentStudentId as readCurrentStudentId } from '../../constants/auth'
import {
  readWeaknessTrainingState,
  recordWeaknessTrainingReview,
  writeWeaknessTrainingState
} from '../../utils/weaknessTraining'

const route = useRoute()
const router = useRouter()
marked.setOptions({ gfm: true, breaks: true })

const COMPLETED_STORAGE_KEY = 'leetcode_completed_problem_ids'

// 响应式数据
const problem = ref({})
const selectedLanguage = ref('java')
const code = ref('')
const testInput = ref('')
const showSolution = ref(false)
const running = ref(false)
const submitting = ref(false)
const runResult = ref(null)
const submitResult = ref(null)
const showSubmitResult = ref(false)
const activeTab = ref('testcase')
const editorRef = ref(null)
const editorInstance = ref(null)
const solutionLanguage = ref('java')

// 代码模板
const codeTemplates = {
  java: `class Solution {
    public int[] twoSum(int[] nums, int target) {
        // 请在这里编写你的代码
        
    }
}`,
  python: `class Solution:
    def twoSum(self, nums: List[int], target: int) -> List[int]:
        # 请在这里编写你的代码
        pass`,
  c: `#include <stdio.h>
#include <stdlib.h>

int* twoSum(int* nums, int numsSize, int target, int* returnSize) {
    // write your code here
    *returnSize = 0;
    return NULL;
}`,
  cpp: `class Solution {
public:
    vector<int> twoSum(vector<int>& nums, int target) {
        // 请在这里编写你的代码
        
    }
};`,
  javascript: `/**
 * @param {number[]} nums
 * @param {number} target
 * @return {number[]}
 */
var twoSum = function(nums, target) {
    // 请在这里编写你的代码
    
};`
}

// 编辑器配置
const editorExtensions = computed(() => ([
  getLanguageExtension(),
  oneDark,
  EditorView.lineWrapping
]))

// 计算属性
const difficultyType = computed(() => {
  const difficulty = problem.value.difficulty?.toLowerCase()
  switch (difficulty) {
    case 'easy': return 'success'
    case 'medium': return 'warning'
    case 'hard': return 'danger'
    default: return 'info'
  }
})

const renderedProblemText = computed(() => {
  if (!problem.value.problemText) return ''
  return DOMPurify.sanitize(marked(problem.value.problemText))
})

const renderedSolutionText = computed(() => {
  if (!problem.value.solutionText) return ''
  return DOMPurify.sanitize(marked(problem.value.solutionText))
})

const renderedAiFeedback = computed(() => {
  if (!submitResult.value?.aiFeedback) return ''
  return DOMPurify.sanitize(marked(submitResult.value.aiFeedback))
})

const parsedExamples = computed(() => {
  if (!problem.value.examples) return []
  try {
    return JSON.parse(problem.value.examples)
  } catch {
    return []
  }
})

const renderedConstraints = computed(() => {
  if (!problem.value.constraints) return ''
  return DOMPurify.sanitize(marked(problem.value.constraints))
})

const parsedSolution = computed(() => {
  if (!problem.value.solutionText) return {}
  
  try {
    // 尝试解析结构化题解
    const solution = JSON.parse(problem.value.solutionText)
    return solution
  } catch {
    // 如果不是 JSON 格式，则按 markdown 处理
    const text = problem.value.solutionText
    return {
      approach: DOMPurify.sanitize(marked(text))
    }
  }
})

// 方法
function getLanguageExtension() {
  switch (selectedLanguage.value) {
    case 'java': return java()
    case 'python': return python()
    case 'c': return cpp()
    case 'cpp': return cpp()
    case 'javascript': return javascript()
    default: return javascript()
  }
}

function onLanguageChange() {
  code.value = codeTemplates[selectedLanguage.value] || ''
}

function onEditorReady(payload) {
  editorInstance.value = payload?.view || payload || null
  nextTick(() => {
    if (editorInstance.value?.focus) {
      editorInstance.value.focus()
    }
  })
}

function focusEditor() {
  if (editorInstance.value?.focus) {
    editorInstance.value.focus()
  }
}

function getCurrentStudentId() {
  return readCurrentStudentId()
}

function getLanguageLabel(lang) {
  const labels = {
    java: 'Java',
    python: 'Python',
    cpp: 'C++',
    javascript: 'JavaScript',
    c: 'C'
  }
  return labels[lang] || lang.toUpperCase()
}

function resetCode() {
  ElMessageBox.confirm('确定要重置代码吗？未保存的修改将会丢失。', '重置代码', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    code.value = codeTemplates[selectedLanguage.value] || ''
    ElMessage.success('代码已重置')
  }).catch(() => {})
}

async function runCode() {
  if (!code.value.trim()) {
    ElMessage.warning('请先编写代码')
    return
  }

  running.value = true
  try {
    const response = await api.runLeetCodeSolution({
      problemId: problem.value.id,
      code: code.value,
      language: selectedLanguage.value,
      testInput: testInput.value
    })

    if (response.success) {
      runResult.value = response.data
      activeTab.value = 'result'

      if (response.data.status === 'success') {
        ElMessage.success('代码运行成功')
      } else {
        ElMessage.error('代码运行失败')
      }
    } else {
      ElMessage.error('运行失败: ' + (response.message || '未知错误'))
    }
  } catch (error) {
    console.error('运行代码失败:', error)
    let errorMessage = '运行代码失败'
    
    if (error.response) {
      errorMessage += ': ' + (error.response.data?.message || error.response.statusText)
    } else if (error.message) {
      errorMessage += ': ' + error.message
    }
    
    ElMessage.error(errorMessage)
  } finally {
    running.value = false
  }
}

async function submitCode() {
  if (!code.value.trim()) {
    ElMessage.warning('请先编写代码')
    return
  }

  submitting.value = true
  try {
    const studentId = getCurrentStudentId()
    const response = await api.submitLeetCodeSolution({
      problemId: problem.value.id,
      code: code.value,
      language: selectedLanguage.value,
      studentId,
      recommendationRequestId: getRecommendationRequestId(),
      recommendationSessionId: getRecommendationSessionId()
    })

    if (response.success) {
      submitResult.value = response.data
      showSubmitResult.value = true
      recordTrainingReview(!!response.data.accepted)

      if (response.data.status === 'unavailable') {
        ElMessage.warning('AI evaluation is temporarily unavailable. Fallback result is shown.')
      } else if (response.data.accepted) {
        markProblemCompleted(problem.value.id)
        ElMessage.success('答案通过')
      } else {
        ElMessage.error('答案未通过，请查看详细反馈')
      }
    } else {
      ElMessage.error('提交失败: ' + (response.message || '未知错误'))
    }
  } catch (error) {
    console.error('提交代码失败:', error)
    let errorMessage = '提交代码失败'
    
    if (error.response) {
      // 服务器返回错误
      errorMessage += ': ' + (error.response.data?.message || error.response.statusText)
    } else if (error.message) {
      errorMessage += ': ' + error.message
    }
    
    ElMessage.error(errorMessage)
  } finally {
    submitting.value = false
  }
}

function continuePractice() {
  showSubmitResult.value = false
  router.push('/student/practice')
}

async function loadProblem() {
  const problemId = route.params.id
  if (!problemId) {
    ElMessage.error('题目ID不存在')
    router.push('/student/practice')
    return
  }

  try {
    const response = await api.getLeetCodeProblem(problemId)
    if (!response?.success || !response.data) {
      throw new Error(response?.message || '题目数据为空')
    }
    problem.value = response.data
    
    // 设置默认代码模板
    code.value = codeTemplates[selectedLanguage.value] || ''
    
    // 设置默认测试用例
    if (problem.value.sampleTestCases) {
      testInput.value = problem.value.sampleTestCases.join('\n')
    }
  } catch (error) {
    console.error('加载题目失败:', error)
    ElMessage.error('加载题目失败')
    router.push('/student/practice')
  }
}

function getRecommendationRequestId() {
  const value = route.query.recommendationRequestId
  return typeof value === 'string' && value.trim() ? value.trim() : null
}

function getRecommendationSessionId() {
  const value = route.query.recommendationSessionId
  return typeof value === 'string' && value.trim() ? value.trim() : null
}

function getTrainingExperimentId() {
  const value = Number(route.query.trainingExperimentId)
  return Number.isFinite(value) ? value : null
}

function getTrainingDimension() {
  const value = route.query.trainingDimension
  return typeof value === 'string' ? value : ''
}

function getTrainingSource() {
  const value = route.query.trainingSource
  return typeof value === 'string' && value.trim() ? value.trim() : 'weakness_training'
}

function recordTrainingReview(accepted) {
  const experimentId = getTrainingExperimentId()
  const problemId = Number(problem.value?.id)
  const studentId = getCurrentStudentId()

  if (!experimentId || !Number.isFinite(problemId) || !studentId) return

  const nextState = recordWeaknessTrainingReview(readWeaknessTrainingState(studentId), {
    experimentId,
    problemId,
    problemTitle: problem.value?.title || `题目 ${problemId}`,
    dimension: getTrainingDimension(),
    accepted,
    source: getTrainingSource()
  })
  writeWeaknessTrainingState(nextState, studentId)
}

function markProblemCompleted(problemId) {
  const parsed = Number(problemId)
  if (!Number.isFinite(parsed)) return

  try {
    const raw = sessionStorage.getItem(COMPLETED_STORAGE_KEY)
    const existing = raw ? JSON.parse(raw) : []
    const normalized = Array.isArray(existing)
      ? existing.map(item => Number(item)).filter(Number.isFinite)
      : []

    if (!normalized.includes(parsed)) {
      normalized.push(parsed)
      sessionStorage.setItem(COMPLETED_STORAGE_KEY, JSON.stringify(normalized))
    }
  } catch (error) {
    console.warn('保存完成题目记录失败:', error)
  }
}

// 生命周期
onMounted(() => {
  loadProblem()
})
</script>

<style scoped>
.leetcode-practice {
  display: flex;
  height: calc(100vh - 120px);
  gap: 16px;
  padding: 16px;
}

.problem-section {
  flex: 1;
  background: white;
  border-radius: 8px;
  padding: 20px;
  overflow-y: auto;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.problem-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid #eee;
}

.problem-title h2 {
  margin: 0 0 8px 0;
  color: #333;
  font-size: 24px;
}

.problem-actions {
  display: flex;
  gap: 8px;
}

.problem-content {
  line-height: 1.6;
}

.content-section {
  margin-bottom: 24px;
}

.content-section h3 {
  color: #333;
  font-size: 18px;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 2px solid #409eff;
}

.formatted-content {
  font-size: 14px;
  color: #555;
  background: #fafafa;
  padding: 16px;
  border-radius: 8px;
  border-left: 4px solid #409eff;
  max-height: 340px;
  overflow: auto;
}

.examples-container {
  background: #f8f9fa;
  padding: 16px;
  border-radius: 8px;
}

.example-item {
  margin-bottom: 16px;
  padding: 12px;
  background: white;
  border-radius: 6px;
  border: 1px solid #e0e0e0;
}

.example-item:last-child {
  margin-bottom: 0;
}

.example-item h4 {
  margin: 0 0 8px 0;
  color: #409eff;
  font-size: 14px;
}

.example-content {
  font-family: 'Courier New', monospace;
  font-size: 13px;
}

.example-input, .example-output {
  margin-bottom: 4px;
}

.example-input code, .example-output code {
  background: #f0f0f0;
  padding: 2px 6px;
  border-radius: 3px;
  color: #e74c3c;
}

.example-explanation {
  margin-top: 8px;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  color: #666;
  font-style: italic;
}

.constraints-content {
  background: #fff3cd;
  padding: 12px;
  border-radius: 6px;
  border-left: 4px solid #ffc107;
  font-size: 13px;
}

.solution-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  color: #409eff;
}

.solution-content {
  max-height: none;
  overflow: visible;
}

.solution-approach h4,
.solution-code h4,
.solution-complexity h4 {
  color: #333;
  margin: 16px 0 8px 0;
  font-size: 16px;
}

.approach-content {
  background: #f8f9fa;
  padding: 16px;
  border-radius: 8px;
  margin-bottom: 16px;
}

.solution-tabs {
  margin-top: 8px;
}

.solution-code-block {
  background: #2d3748;
  color: #e2e8f0;
  padding: 16px;
  border-radius: 8px;
  overflow-x: auto;
  font-family: 'Courier New', monospace;
  font-size: 14px;
  line-height: 1.5;
  margin: 0;
}

.complexity-content {
  background: #e8f5e8;
  padding: 12px;
  border-radius: 6px;
  border-left: 4px solid #28a745;
}

.problem-description {
  font-size: 14px;
  color: #555;
}

.problem-description :deep(pre) {
  background: #f5f5f5;
  padding: 12px;
  border-radius: 4px;
  overflow-x: auto;
}

.problem-description :deep(code) {
  background: #f0f0f0;
  padding: 2px 4px;
  border-radius: 2px;
  font-family: 'Courier New', monospace;
}

.solution-section {
  margin-top: 20px;
}

.solution-content {
  max-height: 400px;
  overflow-y: auto;
}

.code-section {
  flex: 1;
  background: white;
  border-radius: 8px;
  padding: 20px;
  display: flex;
  flex-direction: column;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.code-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #eee;
}

.code-actions {
  display: flex;
  gap: 8px;
}

.code-editor {
  flex: 1;
  border: 1px solid #ddd;
  border-radius: 4px;
  overflow: hidden;
  cursor: text;
}

.code-editor :deep(.CodeMirror) {
  height: 100%;
  font-family: 'Courier New', monospace;
  font-size: 14px;
  cursor: text;
}

.code-editor :deep(.CodeMirror-cursor) {
  border-left: 2px solid #409eff;
}

.code-editor :deep(.CodeMirror-selected) {
  background: #409eff33;
}

.code-editor :deep(.CodeMirror-line) {
  cursor: text;
}

.code-editor :deep(.CodeMirror-scroll) {
  cursor: text;
}

.code-editor :deep(.cm-editor) {
  height: 100%;
  font-family: 'Courier New', monospace;
  font-size: 14px;
}

.code-editor :deep(.cm-focused) {
  outline: 2px solid #409eff;
  outline-offset: -2px;
}

.code-editor :deep(.cm-editor.cm-focused .cm-cursor) {
  border-left-color: #409eff;
}

.code-editor :deep(.cm-selectionBackground) {
  background: #409eff33 !important;
}

.test-input {
  margin-top: 16px;
  height: 200px;
}

.run-result {
  padding: 12px;
}

.result-status {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  font-weight: bold;
}

.result-status.success {
  color: #67c23a;
}

.result-status.error {
  color: #f56c6c;
}

.result-content pre {
  background: #f5f5f5;
  padding: 12px;
  border-radius: 4px;
  margin: 0;
  white-space: pre-wrap;
  word-wrap: break-word;
}

.submit-result {
  max-height: 70vh;
  overflow-y: auto;
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding: 16px;
  background: #f8f9fa;
  border-radius: 8px;
}

.status {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: bold;
}

.status.accepted {
  color: #67c23a;
}

.status.rejected {
  color: #f56c6c;
}

.score {
  font-size: 16px;
  font-weight: bold;
  color: #409eff;
}

.ai-feedback {
  margin: 20px 0;
}

.ai-feedback h3 {
  margin-bottom: 12px;
  color: #333;
}

.feedback-content {
  background: #f8f9fa;
  padding: 16px;
  border-radius: 8px;
  border-left: 4px solid #409eff;
}

.feedback-content :deep(pre) {
  background: #fff;
  padding: 12px;
  border-radius: 4px;
  border: 1px solid #ddd;
}

.execution-details {
  margin: 20px 0;
}

.error-message {
  color: #f56c6c;
  background: #fef0f0;
  padding: 8px;
  border-radius: 4px;
  margin: 0;
}

.skill-suggestions {
  margin: 20px 0;
}

.skill-suggestions h3 {
  margin-bottom: 12px;
  color: #333;
}

.suggestion-tag {
  margin: 4px 8px 4px 0;
}

@media (max-width: 1200px) {
  .leetcode-practice {
    flex-direction: column;
    height: auto;
  }
  
  .problem-section,
  .code-section {
    flex: none;
  }
  
  .code-section {
    min-height: 600px;
  }
}
</style>

