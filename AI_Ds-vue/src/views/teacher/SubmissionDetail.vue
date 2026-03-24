<template>
  <div class="submission-detail">
    <page-header class="my-page-header" title="瀛︾敓鎻愪氦璇︽儏" :description="`${studentName} 鐨勫疄楠屾彁浜">
      <el-button @click="goBack" icon="Back">杩斿洖鎻愪氦鍒楄〃</el-button>
    </page-header>

    <div class="page-content" v-loading="loading">
      <el-row :gutter="20">
        <!-- 宸︿晶鍖哄煙锛氬鐢熶俊鎭€佺粺璁℃暟鎹強灏忓崱鐗?-->
        <el-col :span="6">
          <!-- 瀛︾敓鍩烘湰淇℃伅鍗＄墖 -->
          <el-card class="info-card">
            <div class="student-info">
              <div class="avatar-container text-center">
                <el-avatar :size="80"
                           :src="submission.studentAvatar || 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png'" />
              </div>
              <div class="student-details">
                <h3 class="text-center">{{ submission.studentName }}</h3>
                <div class="detail-item">
                  <span class="label">瀛﹀彿锛?/span>
                  <span>{{ submission.studentId }}</span>
                </div>
                <div class="detail-item">
                  <span class="label">鐝骇锛?/span>
                  <span>{{ submission.class }}</span>
                </div>
                <!-- <div class="text-center">
                  <el-tag :type="getStatusType(submission.status)" effect="dark" size="large" class="status-tag">
                    {{ getStatusText(submission.status) }}
                  </el-tag>
                </div> -->
              </div>
            </div>
          </el-card>

          <!-- 缁熻鏁版嵁鍗＄墖 -->
          <el-card class="stats-card">
            <div class="stat-grid">
              <div class="stat-item">
                <div class="stat-label">涓婃満鎴愮哗璇勫垎</div>
                <div class="stat-value" :class="{ 'highlighted': submission.status === 'graded' }">
                  {{ submission.score !== null ? submission.score : '鏈瘎鍒? }}
                </div>
              </div>
              <div class="stat-item">
                <div class="stat-label">鏌ラ噸鐜?/div>
                <div class="stat-value" :class="{
                  'low-plagiarism': submission.plagiarismRate < 15,
                  'medium-plagiarism': submission.plagiarismRate >= 15 && submission.plagiarismRate < 30,
                  'high-plagiarism': submission.plagiarismRate >= 30
                }">
                  {{ submission.plagiarismRate !== null ? `${submission.plagiarismRate}%` : '鏈娴? }}
                </div>
              </div>
              <div class="stat-item">
                <div class="stat-label">鎻愪氦鏃堕棿</div>
                <div class="stat-value time-value">{{ submission.date }}</div>
              </div>
            </div>
          </el-card>

          <!-- 鎿嶄綔鎸夐挳鍗＄墖 -->
          <el-card class="action-card">
            <div class="action-buttons">
              <el-button type="primary" @click="openGradeDialog" class="full-width-btn">
                <el-icon>
                  <Edit />
                </el-icon>{{ submission.status === 'graded' ? '閲嶆柊璇勫垎' : '璇勫垎' }}
              </el-button>
              <!-- <el-button type="danger" @click="rejectSubmission" v-if="submission.status !== 'rejected'"
                class="full-width-btn">
                <el-icon>
                  <Close />
                </el-icon>鎷掔粷鎻愪氦
              </el-button> -->
              <!-- 鍏朵粬鎸夐挳 -->

              <!-- <el-button 
                type="success"
                @click="generateAIComment"
                :loading="generatingComment"
                :disabled="generatingComment"
                class="full-width-btn"
              >
                <el-icon><MagicStick /></el-icon>AI杈呭姪璇勬祴
              </el-button> -->

            </div>
          </el-card>

          <!-- AI璇勬祴缁撴灉鍗＄墖 -->
          <!-- <el-card class="ai-comment-card" v-if="submission.aiComment">
            <template #header>
              <div class="card-header">
                <div class="ai-header">
                  <el-icon>
                    <MagicStick />
                  </el-icon>
                  <span>AI鐐硅瘎</span>
                </div>
                <div class="ai-actions">
                  <el-button type="primary" circle size="small" @click="editAIComment">
                    <el-icon>
                      <Edit />
                    </el-icon>
                  </el-button>
                  <el-button type="primary" circle size="small" @click="regenerateAIComment"
                    :loading="generatingComment" :disabled="generatingComment">
                    <el-icon>
                      <Refresh />
                    </el-icon>
                  </el-button>
                </div>
              </div>
            </template>
<div class="ai-comment-content">
  {{ submission.aiComment }}
</div>
</el-card> -->
        </el-col>

        <!-- 鍙充晶鍖哄煙锛氫唬鐮佸拰鎶ュ憡鍐呭 -->
        <el-col :span="18">
          <!-- 鎻愪氦鍐呭鏍囩椤?-->
          <el-card class="content-card">
            <el-tabs v-model="activeTab" class="main-tabs">
              <el-tab-pane label="浠ｇ爜" name="code">
                <div class="code-header">
                  <h3>瀹為獙浠ｇ爜</h3>
                  <div class="code-actions">
                    <el-button type="info" @click="copyCode">
                      <el-icon>
                        <CopyDocument />
                      </el-icon>
                      澶嶅埗浠ｇ爜
                    </el-button>
                    <el-dropdown>
                      <el-button type="success">
                        <el-icon>
                          <Document />
                        </el-icon>
                        鏂囦欢鎿嶄綔
                        <el-icon class="el-icon--right">
                          <ArrowDown />
                        </el-icon>
                      </el-button>
                      <template #dropdown>
                        <el-dropdown-menu>
                          <el-dropdown-item @click="downloadCode">
                            <el-icon>
                              <Download />
                            </el-icon>
                            涓嬭浇浠ｇ爜
                          </el-dropdown-item>
                          <!-- <el-dropdown-item @click="formatCode">
                            <el-icon>
                              <Operation />
                            </el-icon>
                            鏍煎紡鍖栦唬鐮?                          </el-dropdown-item> -->
                        </el-dropdown-menu>
                      </template>
                    </el-dropdown>
                  </div>
                </div>

                <!-- 棰樼洰鍒楄〃鏄剧ず -->
                <el-tabs v-model="activeQuestionTab" tab-position="left" class="question-tabs"
                         v-if="parsedQuestions.length > 0">
                  <el-tab-pane v-for="(question, index) in parsedQuestions" :key="index" :label="`绗?{question.number}棰榒"
                               :name="String(index)">
                    <div class="question-container">
                      <pre class="code-display"><code>{{ question.code }}</code></pre>

                      <div class="test-results" v-if="question.testResults">
                        <h4>娴嬭瘯缁撴灉</h4>
                        <div v-html="formatTestResults(question.testResults)"></div>
                      </div>

                      <el-divider content-position="left">鏁欏笀璇勮</el-divider>

                      <!-- 鍦ㄨ瘎璇紪杈戝尯娣诲姞ref寮曠敤 -->
                      <div class="comment-edit" ref="commentDivs">
                        <el-input v-model="question.comment" type="textarea" :rows="3" placeholder="璇疯緭鍏ュ鏈鐨勮瘎璇?.."
                                  @input="updateQuestionComment(index, $event)" />
                        <div class="comment-actions">
                          <el-button type="primary" size="small" @click="saveQuestionComment(index)"
                                     :loading="question.saving">
                            淇濆瓨璇勮
                          </el-button>
                        </div>
                      </div>

                    </div>
                  </el-tab-pane>
                </el-tabs>

                <!-- 濡傛灉娌℃湁瑙ｆ瀽鍑洪鐩紝鍒欐樉绀哄畬鏁翠唬鐮?-->
                <pre class="code-display" v-else><code>{{ submission.code }}</code></pre>

                <!-- 浠ｇ爜杩愯缁撴灉 -->
                <div class="code-result" v-if="codeResult">
                  <div class="result-header">
                    <h3>杩愯缁撴灉</h3>
                    <el-tag :type="codeResult.success ? 'success' : 'danger'">
                      {{ codeResult.success ? '杩愯鎴愬姛' : '杩愯澶辫触' }}
                    </el-tag>
                  </div>
                  <pre class="result-output">{{ codeResult.output }}</pre>
                </div>
              </el-tab-pane>

              <el-tab-pane label="瀹為獙鎶ュ憡" name="report">
                <div class="report-header">
                  <h3>瀹為獙鎶ュ憡</h3>
                  <div class="report-actions">
                    <!-- <el-button type="primary" round size="small" @click="printReport">
                      <el-icon>
                        <Printer />
                      </el-icon>
                      鎵撳嵃鎶ュ憡
                    </el-button> -->
                    <el-dropdown>
                      <el-button type="success">
                        <el-icon>
                          <Download />
                        </el-icon>
                        涓嬭浇鎶ュ憡
                        <el-icon class="el-icon--right">
                          <ArrowDown />
                        </el-icon>
                      </el-button>
                      <template #dropdown>
                        <el-dropdown-menu>
                          <!-- <el-dropdown-item @click="downloadReport">
                            <el-icon>
                              <Document />
                            </el-dropdown-item> -->
                          <el-dropdown-item @click="downloadWordDoc">
                            <el-icon>
                              <Document />
                            </el-icon>
                            涓嬭浇 Word 鏂囨。
                          </el-dropdown-item>
                          <el-dropdown-item @click="downloadPDF">
                            <el-icon>
                              <Document />
                            </el-icon>
                            涓嬭浇 PDF 鏂囨。
                          </el-dropdown-item>
                        </el-dropdown-menu>
                      </template>
                    </el-dropdown>
                  </div>
                </div>

                <div v-if="submission.report" class="report-container">
                  <!-- 浣跨敤鏍囧噯鎶ュ憡缁勪欢 -->
                  <report-generator :report-data="reportData" @update:report-data="handleReportDataUpdate"
                                    ref="reportGeneratorRef" />

                </div>
                <el-empty v-else description="瀛︾敓鏈彁浜ゅ疄楠屾姤鍛?></el-empty>
              </el-tab-pane>

              <!-- <el-tab-pane label="瀛︿範鍘嗙▼" name="history">
                <div class="history-header">
                  <h3>瀛︿範鍘嗙▼涓庝唬鐮佹彁浜よ褰?/h3>
                </div>

                <el-timeline>
                  <el-timeline-item v-for="(item, index) in submissionHistory" :key="index" :timestamp="item.time"
                    :color="getHistoryItemColor(item.type)">
                    <div class="history-item">
                      <div class="history-title">{{ item.title }}</div>
                      <div class="history-content">{{ item.content }}</div>
                      <div class="history-actions" v-if="item.code">
                        <el-button type="primary" round size="small" @click="viewHistoryCode(item)">
                          鏌ョ湅浠ｇ爜
                        </el-button>
                      </div>
                    </div>
                  </el-timeline-item>
                </el-timeline>
              </el-tab-pane> -->

              <el-tab-pane label="瀛︾敓琛ㄧ幇" name="performance" class="performance">
                <div class="performance-header">
                  <h3>瀛︿範琛ㄧ幇鍒嗘瀽</h3>
                </div>

                <el-row :gutter="20">
                  <el-col :span="12">
                    <el-card class="chart-card" shadow="hover">
                      <template #header>
                        <div class="chart-header">
                          <span>瀹為獙鎴愮哗瓒嬪娍</span>
                        </div>
                      </template>
                      <div class="chart-container" ref="scoreChartContainer"></div>
                    </el-card>
                  </el-col>

                  <el-col :span="12">
                    <el-card class="chart-card" shadow="hover">
                      <template #header>
                        <div class="chart-header">
                          <span>瀹為獙瀹屾垚鎯呭喌</span>
                        </div>
                      </template>
                      <div class="chart-container" ref="completionChartContainer"></div>
                    </el-card>
                  </el-col>
                </el-row>

                <el-card class="performance-card" shadow="hover">
                  <template #header>
                    <div class="card-header">
                      <span>缁煎悎琛ㄧ幇璇勪及</span>
                      <!-- <el-button type="primary" round size="small" @click="generatePerformanceReport">
                        鐢熸垚缁煎悎璇勪及鎶ュ憡
                      </el-button> -->
                    </div>
                  </template>

                  <el-descriptions :column="3" border>
                    <el-descriptions-item label="骞冲潎鎴愮哗">
                      <span :class="getScoreClass(studentPerformance.averageScore)">
                        {{ studentPerformance.averageScore }}
                      </span>
                    </el-descriptions-item>
                    <el-descriptions-item label="瀹為獙瀹屾垚鐜?>
                      {{ studentPerformance.completionRate }}%
                    </el-descriptions-item>
                    <el-descriptions-item label="鐝骇鎺掑悕">
                      绗?{{ studentPerformance.classRank }} 鍚?                    </el-descriptions-item>
                    <el-descriptions-item label="浣滀笟鎻愪氦鍙婃椂鎬?>
                      <el-rate v-model="studentPerformance.punctuality" disabled show-score
                               :colors="['#F56C6C', '#E6A23C', '#67C23A']" />
                    </el-descriptions-item>
                    <el-descriptions-item label="浠ｇ爜璐ㄩ噺璇勫垎">
                      <el-rate v-model="studentPerformance.codeQuality" disabled show-score
                               :colors="['#F56C6C', '#E6A23C', '#67C23A']" />
                    </el-descriptions-item>
                    <el-descriptions-item label="娌熼€氬弬涓庡害">
                      <el-rate v-model="studentPerformance.participation" disabled show-score
                               :colors="['#F56C6C', '#E6A23C', '#67C23A']" />
                    </el-descriptions-item>
                  </el-descriptions>

                  <div class="performance-analysis">
                    <h4>AI鍔╂暀鐐硅瘎</h4>
                    <div class="ai-comment-content" v-html="renderMarkdown(submission.aiRemarks)"></div>
                  </div>

                  <el-divider />

                  <div class="learning-recommendation">
                    <h4>瀛︿範寤鸿</h4>
                    <el-collapse v-if="learningRecommendations.length > 0">
                      <el-collapse-item v-for="(rec, index) in learningRecommendations" :key="index" :title="rec.title">
                        <div class="recommendation-content">
                          <p>{{ rec.content }}</p>
                          <div class="resource-links" v-if="rec.resources && rec.resources.length">
                            <h5>鎺ㄨ崘璧勬簮锛?/h5>
                            <ul>
                              <li v-for="(resource, rIndex) in rec.resources" :key="rIndex">
                                <a :href="resource.url" target="_blank">{{ resource.name }}</a>
                              </li>
                            </ul>
                          </div>
                        </div>
                      </el-collapse-item>
                    </el-collapse>
                    <el-empty v-else description="鏆傛棤瀛︿範寤鸿锛岃鐐瑰嚮'鐢熸垚缁煎悎璇勪及鎶ュ憡'鐢熸垚"></el-empty>
                  </div>
                </el-card>
              </el-tab-pane>
            </el-tabs>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <!-- 璇勫垎瀵硅瘽妗?-->
    <el-dialog v-model="gradeDialogVisible" title="璇勫垎" width="500px">
      <el-form :model="gradeForm" label-width="100px">
        <el-form-item label="瀛︾敓">
          <span>{{ submission.studentName }}</span>
        </el-form-item>

        <el-form-item label="瀹為獙鍚嶇О">
          <span>{{ submission.experimentName }}</span>
        </el-form-item>

        <el-form-item label="涓婃満鎴愮哗寰楀垎" prop="score">
          <el-input-number v-model="gradeForm.score" :min="0" :max="100" :precision="1" style="width: 150px;" />
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="gradeDialogVisible = false">鍙栨秷</el-button>
          <el-button type="primary" @click="submitGrade">鎻愪氦璇勫垎</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 鍘嗗彶浠ｇ爜瀵硅瘽妗?-->
    <el-dialog v-model="historyCodeVisible" :title="selectedHistory ? selectedHistory.title : '鍘嗗彶浠ｇ爜'" width="70%">
      <div v-if="selectedHistory" class="history-code-dialog">
        <div class="history-info">
          <div><strong>鎻愪氦鏃堕棿锛?/strong>{{ selectedHistory.time }}</div>
          <div><strong>鎻忚堪锛?/strong>{{ selectedHistory.content }}</div>
        </div>
        <pre class="code-display"><code>{{ selectedHistory.code }}</code></pre>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, ElLoading } from 'element-plus'
import api from '../../api'
import PageHeader from '../../components/PageHeader.vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import * as echarts from 'echarts/core'
import axios from 'axios'
import { LineChart, BarChart, PieChart } from 'echarts/charts'
import {
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import {
  VideoPlay, Warning, CopyDocument, Document, ArrowDown, Download,
  Operation, Printer, MagicStick, ChatLineRound, Check, Close, Edit
} from '@element-plus/icons-vue'
import ReportGenerator from '../../components/ReportGenerator.vue'
import { buildApiUrl } from '../../config/runtime'
// 寮曞叆 DocxGenerator
import { DocxGenerator } from '../../utils/docxGenerator'
import html2canvas from 'html2canvas';


echarts.use([
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent,
  LineChart,
  BarChart,
  PieChart,
  CanvasRenderer
])

const route = useRoute()
const router = useRouter()
const loading = ref(true)
const activeTab = ref('code')
const submissionId = computed(() => String(route.params.id))
const submission = ref({})
const studentName = computed(() => submission.value?.studentName || '')
const codeResult = ref(null)
const scoreChartContainer = ref(null)
const completionChartContainer = ref(null)
let scoreChart = null
let completionChart = null

// 棰樼洰瑙ｆ瀽鐩稿叧
const activeQuestionTab = ref('0') // 榛樿閫変腑绗竴棰?const parsedQuestions = ref([]) // 瑙ｆ瀽鍚庣殑棰樼洰鍒楄〃

// 璇勫垎鐩稿叧
const gradeDialogVisible = ref(false)
const gradeForm = reactive({
  score: 0,
  plagiarismRate: 0,
  aiComment: '',
  teacherComment: ''
})
const generatingComment = ref(false)

// 鍘嗗彶璁板綍鐩稿叧
const submissionHistory = ref([])
const historyCodeVisible = ref(false)
const selectedHistory = ref(null)

// 瀛︾敓琛ㄧ幇锛堜粠鐪熷疄鏁版嵁璁＄畻锛?const studentPerformance = reactive({
  averageScore: 0,
  completionRate: 0,
  classRank: '-',
  punctuality: 0,
  codeQuality: 0,
  participation: 0,
  aiAnalysis: ''
})

// 瀛︿範寤鸿
const learningRecommendations = ref([])

// 鏂板鎶ュ憡鐩稿叧鍙橀噺
const reportData = ref({})
const reportGeneratorRef = ref(null)
const isEditingComment = ref(false)
const editingTeacherComment = ref('')





// 瑙ｆ瀽鎻愪氦浠ｇ爜锛屾寜棰樼洰鍒嗗壊
const parseQuestionCode = () => {
  if (!submission.value || !submission.value.code) return

  // 浣跨敤姝ｅ垯琛ㄨ揪寮忓尮閰嶆墍鏈夐鐩?  const regex = /绗琝s*(\d+)\s*棰樺涓?([\s\S]*?)(?=绗琝s*\d+\s*棰樺涓?|$)/g
  const code = submission.value.code
  const questions = []

  let match
  while ((match = regex.exec(code)) !== null) {
    const questionNumber = match[1]
    let questionCode = match[2].trim()

    // 鎻愬彇娴嬭瘯缁撴灉琛ㄦ牸锛堝鏋滄湁锛?    const testResultsRegex = /([\s\S]*?)((?:\|\s*娴嬭瘯鐐筟\s\S]*?)+$)/
    const resultMatch = questionCode.match(testResultsRegex)

    let testResults = null
    if (resultMatch) {
      questionCode = resultMatch[1].trim()
      testResults = resultMatch[2].trim()
    }
    // 娣诲姞鍒伴鐩垪琛紝鍖呮嫭浠ｇ爜銆佹祴璇曠粨鏋溿€佽瘎璇拰淇濆瓨鐘舵€?    questions.push({
      number: parseInt(questionNumber),
      code: questionCode,
      testResults,
      comment: '', // 鍒濆璇勮涓虹┖
      saving: false // 淇濆瓨鐘舵€侊紝鐢ㄤ簬鎺у埗鎸夐挳loading
    })
  }

  parsedQuestions.value = questions

  // 濡傛灉娌℃湁瑙ｆ瀽鍑洪鐩紝灏嗘暣涓唬鐮佷綔涓轰竴涓鐩?  if (questions.length === 0 && code) {
    parsedQuestions.value = [{
      number: 1,
      code: code,
      testResults: null,
      comment: '',
      saving: false
    }]
  }
}

// 鏍煎紡鍖栨祴璇曠粨鏋滀负HTML
const formatTestResults = (resultsText) => {
  if (!resultsText) return ''
  // 绠€鍗曟浛鎹繚鎸佽〃鏍兼牸寮?  return resultsText.replace(/\|/g, '|')
      .replace(/\n/g, '<br>')
      .replace(/\s/g, '&nbsp;')
}

// 鏇存柊棰樼洰璇勮
const updateQuestionComment = (index, comment) => {
  parsedQuestions.value[index].comment = comment
  updateReportWithComments()
}

// 娓叉煋Markdown鏍煎紡鏂囨湰涓篐TML
const renderMarkdown = (text) => {
  if (!text) return ''
  const rawHtml = marked.parse(text)
  return DOMPurify.sanitize(rawHtml)
}

const splitAiRemarksToQuestions = () => {
  const aiRemarks = submission.value.aiRemarks || '';
  // 鍋囪鏍煎紡锛氱1棰樿瘎璇? ... 绗?棰樿瘎璇? ... 鎬昏瘎璇? ...
  const questionRegex = /棰樼洰\s*([123456789\d]+)[:锛歖([\s\S]*?)(?=棰樼洰[123456789\d]+[:锛歖|鎬讳綋璇勪及|$)/g;
  const summaryRegex = /鎬讳綋璇勪及([\s\S]*)$/;
  let match;
  let questionComments = [];
  while ((match = questionRegex.exec(aiRemarks)) !== null) {
    questionComments.push(match[2].trim());
  }
  // 鍙繚鐣欐€昏瘎璇?  const summaryMatch = aiRemarks.match(summaryRegex);
  if (summaryMatch) {
    submission.value.aiRemarks = summaryMatch[1].trim();
  } else {
    submission.value.aiRemarks = '';
  }
  // 璁剧疆姣忛鐨勯粯璁よ瘎璇?  parsedQuestions.value.forEach((q, i) => {
    if (questionComments[i]) q.comment = questionComments[i];
  });
};

const generateCommentImage = async (question) => {
  const viewportWidth = window.innerWidth;
  const imageWidth = Math.min(viewportWidth * 0.15, 500);
  const commentContainer = document.createElement('div');
  commentContainer.className = 'teacher-comment-preview';
  commentContainer.style.width = `${imageWidth}px`;
  commentContainer.style.backgroundColor = '#ffffff';
  commentContainer.style.position = 'absolute';
  commentContainer.style.left = '-9999px';
  commentContainer.style.top = '-9999px';
  commentContainer.innerHTML = `
    <div style="padding:15px; border:1px solid #ddd; background:#f9f9f9; width:100%; box-sizing:border-box;">
      <h3 style="margin-top:0; color:#333; font-size:16px; border-bottom:1px solid #eee; padding-bottom:8px; color: red" class="handwritten-title">
        鏁欏笀璇勮锛?      </h3>
      <div style="color:#333; line-height:1.5; font-size:14px; white-space:pre-wrap; color: red" class="handwritten-romantic">
        ${question.comment.replace(/\n/g, '<br>')}
      </div>
    </div>
  `;
  await ensureFontsLoaded();
  document.body.appendChild(commentContainer);
  const canvas = await html2canvas(commentContainer, {
    backgroundColor: '#ffffff',
    scale: 2,
    logging: false,
    useCORS: true,
    width: imageWidth,
    timeout: 1000
  });
  document.body.removeChild(commentContainer);
  question.commentImage = canvas.toDataURL('image/png', 1.0);
  question.commentImageWidth = imageWidth;
};



// 淇濆瓨棰樼洰璇勮
const saveQuestionComment = async (index) => {
  const question = parsedQuestions.value[index];

  if (!question.saving) {
    question.saving = true;
  }

  try {
    await api.saveQuestionComment(submissionId.value, index, question.comment);

    // 璁＄畻鍚堥€傜殑鍥剧墖瀹藉害锛堝熀浜庡綋鍓嶈鍥惧搴︼級
    const viewportWidth = window.innerWidth;
    // 鍦ㄥ皬灞忓箷涓婂噺灏忓搴︼紝澶у睆骞曚繚鎸佸悎鐞嗗ぇ灏?    const imageWidth = Math.min(viewportWidth * 0.1, 500);

    // 鍒涘缓璇勮瀹瑰櫒
    const commentContainer = document.createElement('div');
    commentContainer.className = 'teacher-comment-preview';
    commentContainer.style.width = `${imageWidth}px`;
    commentContainer.style.backgroundColor = '#ffffff';
    commentContainer.style.position = 'absolute';
    commentContainer.style.left = '-9999px';
    commentContainer.style.top = '-9999px';

    // 淇敼璇勮瀹瑰櫒HTML閮ㄥ垎
    commentContainer.innerHTML = `
  <div style="padding:15px; border:1px solid #ddd; background:#f9f9f9; width:100%; box-sizing:border-box;">
    <h3 style="margin-top:0; color:#333; font-size:16px; border-bottom:1px solid #eee; padding-bottom:8px; color: red" class="handwritten-title">
      鏁欏笀璇勮锛?    </h3>
    <div style="color:#333; line-height:1.5; font-size:14px; white-space:pre-wrap; color: red" class="handwritten-romantic">
      ${question.comment.replace(/\n/g, '<br>')}
    </div>
  </div>
`;

    // 娣诲姞瀛椾綋鍔犺浇妫€鏌?    await ensureFontsLoaded();

    document.body.appendChild(commentContainer);

    // 浣跨敤html2canvas鐢熸垚鍥剧墖
    const canvas = await html2canvas(commentContainer, {
      backgroundColor: '#ffffff',
      scale: 2, // 鎻愰珮娓呮櫚搴?      logging: true,
      useCORS: true,
      width: imageWidth,
      // 澧炲姞涓€涓皬寤惰繜浠ョ‘淇濆瓧浣撳畬鍏ㄥ簲鐢?      timeout: 1000
    });

    document.body.removeChild(commentContainer);

    // 鎸囧畾PNG鏍煎紡
    const imageDataUrl = canvas.toDataURL('image/png', 1.0);
    question.commentImage = imageDataUrl;

    // 瀛樺偍鐢熸垚鐨勫浘鐗囧昂瀵革紝渚夸簬鍚庣画澶勭悊
    question.commentImageWidth = imageWidth;

    updateReportWithComments();
    ElMessage.success(`绗?{question.number}棰樿瘎璇繚瀛樻垚鍔焋);
  } catch (error) {
    console.error('淇濆瓨璇勮澶辫触:', error);
    ElMessage.error(`绗?{question.number}棰樿瘎璇繚瀛樺け璐? ${error.message}`);
  } finally {
    question.saving = false;
  }
};

// 杈呭姪鍑芥暟锛氱‘淇濆瓧浣撳凡鍔犺浇
const ensureFontsLoaded = () => {
  return new Promise((resolve) => {
    // 浣跨敤FontFaceObserver搴撴鏌ュ瓧浣撳姞杞?    // 濡傛灉涓嶄娇鐢ㄨ搴擄紝鍙互浣跨敤绠€鍗曠殑瓒呮椂鏂规硶
    setTimeout(resolve, 500); // 缁欏瓧浣撳姞杞介鐣?00ms鏃堕棿

    // 濡傛灉浣跨敤FontFaceObserver搴擄紝浠ｇ爜浼氭槸锛?    // const zitangKai = new FontFaceObserver('ZitangKai');
    // const maoShanCat = new FontFaceObserver('MaoShanCat');
    // Promise.all([zitangKai.load(), maoShanCat.load()]).then(resolve);
  });
};

// 鏇存柊鎶ュ憡鏁版嵁涓殑瀹為獙姝ラ锛屽寘鍚唬鐮佸拰璇勮
const updateReportWithComments = () => {
  if (!reportData.value) {
    prepareReportData();
  }

  // 鏋勫缓steps鍐呭锛屽寘鍚鐩€佷唬鐮佸拰璇勮鍥剧墖
  let stepsContent = '';
  parsedQuestions.value.forEach((question) => {
    stepsContent += `### 绗?{question.number}棰榎n\n`;
    stepsContent += '```c\n' + question.code + '\n```\n\n';

    // 濡傛灉鏈夎瘎璇浘鐗囷紝娣诲姞璇勮鍥剧墖鏍囪
    if (question.commentImage) {
      stepsContent += `<div class="comment-image-container" data-image="${question.commentImage}"></div>\n\n`;
    }
    // 濡傛灉鍙湁鏂囧瓧璇勮浣嗘病鏈夊浘鐗囷紝淇濈暀鏂囧瓧璇勮浣滀负澶囬€?    else if (question.comment) {
      stepsContent += `**鏁欏笀璇勮**锛?{question.comment}\n\n`;
    }
  });

  // 鏇存柊鎶ュ憡鏁版嵁
  reportData.value.steps = stepsContent;

  // 濡傛灉褰撳墠鍦ㄦ姤鍛婇瑙堥〉闈紝鍒锋柊瑙嗗浘
  if (activeTab.value === 'report' && reportGeneratorRef.value &&
      typeof reportGeneratorRef.value.updateReport === 'function') {
    nextTick(() => {
      reportGeneratorRef.value.updateReport();
    });
  }
};

// 鎴愮哗鏍峰紡
const getScoreClass = (score) => {
  if (!score) return ''
  if (score >= 90) return 'score-excellent'
  if (score >= 80) return 'score-good'
  if (score >= 60) return 'score-pass'
  return 'score-fail'
}


// 鑾峰彇鎻愪氦璇︽儏
const loadSubmissionDetail = async () => {
  loading.value = true
  try {
    const data = await api.getSubmissionDetail(submissionId.value)
    submission.value = data
    gradeForm.score = submission.value.score || 0
    gradeForm.plagiarismRate = submission.value.plagiarismRate || 0
    gradeForm.aiComment = submission.value.aiComment || ''
    gradeForm.teacherComment = submission.value.teacherComment || ''
    parseQuestionCode()

    // 1. 鍒嗗壊AI璇勮骞惰祴鍊间负姣忛鍒濆璇勮
    splitAiRemarksToQuestions();

    // 2. 鑷姩鐢熸垚姣忛璇勮鍥剧墖
    for (const q of parsedQuestions.value) {
      if (q.comment) {
        await generateCommentImage(q);
      }
    }

    // ...existing code...
    prepareReportData()
    loadSubmissionHistory()
    loadLearningRecommendations()
    loadStudentPerformance()
    nextTick(() => {
      initCharts()
    })
  } catch (error) {
    console.error('鍔犺浇鎻愪氦璇︽儏澶辫触:', error)
    ElMessage.error(error?.message || '鍔犺浇鎻愪氦璇︽儏澶辫触')
  } finally {
    loading.value = false
  }
}

// 鍔犺浇鎻愪氦鍘嗗彶 - 浠庣湡瀹濧PI鑾峰彇璇ュ鐢熺殑鎵€鏈夋彁浜よ褰?const loadSubmissionHistory = async () => {
  try {
    const allData = await api.getAllStudentExperiments()
    const studentId = submission.value.studentId
    if (!allData || !studentId) {
      submissionHistory.value = []
      return
    }
    // 绛涢€夎瀛︾敓鐨勬墍鏈夋彁浜わ紝鎸夋椂闂存帓搴?    const studentSubs = allData
      .filter(s => String(s.studentId) === String(studentId))
      .sort((a, b) => new Date(a.submitTime || a.date || 0) - new Date(b.submitTime || b.date || 0))

    submissionHistory.value = studentSubs.map((s, idx) => ({
      time: s.submitTime || s.date || '鏈煡鏃堕棿',
      type: s.status === 'completed' ? 'submit' : 'edit',
      title: s.experimentName || `瀹為獙${idx + 1}`,
      content: `寰楀垎: ${s.score || '鏈瘎鍒?} | 鐘舵€? ${s.status === 'completed' ? '宸插畬鎴? : '杩涜涓?}`,
      code: null
    }))
  } catch (error) {
    console.error('鍔犺浇鎻愪氦鍘嗗彶澶辫触:', error)
    submissionHistory.value = []
  }
}

// 鍔犺浇瀛︿範寤鸿
const loadLearningRecommendations = async () => {
  try {
    // 鍩轰簬瀛︾敓鐪熷疄鎻愪氦鏁版嵁鐢熸垚瀛︿範寤鸿
    const allData = await api.getAllStudentExperiments()
    const studentId = submission.value.studentId
    if (!allData || !studentId) { learningRecommendations.value = []; return }

    const studentSubs = allData.filter(s => String(s.studentId) === String(studentId))
    const scored = studentSubs.filter(s => s.score > 0)
    const avgScore = scored.length > 0 ? scored.reduce((a, b) => a + b.score, 0) / scored.length : 0
    const lowScoreExps = scored.filter(s => s.score < 70)

    const recs = []
    if (lowScoreExps.length > 0) {
      recs.push({
        title: '钖勫急瀹為獙闇€瑕佸姞寮?,
        content: `浠ヤ笅瀹為獙寰楀垎杈冧綆锛屽缓璁噸鐐瑰涔狅細${lowScoreExps.map(s => s.experimentName + '(' + s.score + '鍒?').join('銆?)}`,
        resources: []
      })
    }
    if (avgScore < 80 && avgScore > 0) {
      recs.push({
        title: '鎻愬崌鏁翠綋鎴愮哗',
        content: `褰撳墠骞冲潎鎴愮哗涓?{Math.round(avgScore * 10) / 10}鍒嗭紝寤鸿澶氬仛缁冧範棰樺珐鍥哄熀纭€鐭ヨ瘑锛屼簤鍙栧皢骞冲潎鍒嗘彁鍗囧埌80鍒嗕互涓娿€俙,
        resources: []
      })
    }
    const completed = studentSubs.filter(s => s.status === 'completed').length
    const total = studentSubs.length
    if (total > 0 && completed / total < 0.8) {
      recs.push({
        title: '鎻愰珮瀹為獙瀹屾垚鐜?,
        content: `鐩墠瀹屾垚浜?{completed}/${total}涓疄楠岋紙${Math.round(completed / total * 100)}%锛夛紝寤鸿灏藉揩瀹屾垚鏈彁浜ょ殑瀹為獙銆俙,
        resources: []
      })
    }
    if (recs.length === 0) {
      recs.push({
        title: '琛ㄧ幇浼樼锛岀户缁繚鎸?,
        content: `璇ュ鐢熷悇椤瑰疄楠屽畬鎴愭儏鍐佃壇濂斤紝骞冲潎鎴愮哗${Math.round(avgScore * 10) / 10}鍒嗭紝寤鸿缁х画淇濇寔骞舵寫鎴樻洿楂橀毦搴︾殑棰樼洰銆俙,
        resources: []
      })
    }
    learningRecommendations.value = recs
  } catch (error) {
    console.error('鍔犺浇瀛︿範寤鸿澶辫触:', error)
    learningRecommendations.value = []
  }
}

// 浠庣湡瀹炴暟鎹绠楀鐢熻〃鐜?const loadStudentPerformance = async () => {
  try {
    const allData = await api.getAllStudentExperiments()
    const studentId = submission.value.studentId
    if (!allData || !studentId) return

    const studentSubs = allData.filter(s => String(s.studentId) === String(studentId))
    const scored = studentSubs.filter(s => s.score > 0)
    const completed = studentSubs.filter(s => s.status === 'completed')
    const total = studentSubs.length

    // 骞冲潎鎴愮哗
    studentPerformance.averageScore = scored.length > 0
      ? Math.round(scored.reduce((a, b) => a + b.score, 0) / scored.length * 10) / 10 : 0

    // 瀹屾垚鐜?    studentPerformance.completionRate = total > 0 ? Math.round(completed.length / total * 100) : 0

    // 鐝骇鎺掑悕锛氳绠楁墍鏈夊鐢熺殑骞冲潎鍒嗗苟鎺掑簭
    const studentScores = {}
    allData.filter(s => s.score > 0).forEach(s => {
      if (!studentScores[s.studentId]) studentScores[s.studentId] = []
      studentScores[s.studentId].push(s.score)
    })
    const rankings = Object.entries(studentScores)
      .map(([id, scores]) => ({ id, avg: scores.reduce((a, b) => a + b, 0) / scores.length }))
      .sort((a, b) => b.avg - a.avg)
    const rank = rankings.findIndex(r => String(r.id) === String(studentId))
    studentPerformance.classRank = rank >= 0 ? rank + 1 : '-'

    // 鍙婃椂鎬ц瘎鍒嗭紙鍩轰簬瀹屾垚鐜囷紝5鍒嗗埗锛?    studentPerformance.punctuality = Math.min(5, Math.round(studentPerformance.completionRate / 20 * 10) / 10)

    // 浠ｇ爜璐ㄩ噺璇勫垎锛堝熀浜庡钩鍧囧垎锛?鍒嗗埗锛?    studentPerformance.codeQuality = Math.min(5, Math.round(studentPerformance.averageScore / 20 * 10) / 10)

    // 鍙備笌搴︼紙鍩轰簬鎻愪氦鏁伴噺鍗犳€诲疄楠屾瘮渚嬶紝5鍒嗗埗锛?    studentPerformance.participation = Math.min(5, Math.round(studentSubs.length / Math.max(1, new Set(allData.map(s => s.experimentId)).size) * 5 * 10) / 10)

    // 鏇存柊鍥捐〃
    updatePerformanceCharts(studentSubs, allData)
  } catch (error) {
    console.error('鍔犺浇瀛︾敓琛ㄧ幇鏁版嵁澶辫触:', error)
  }
}

// 鐢ㄧ湡瀹炴暟鎹洿鏂板浘琛?const updatePerformanceCharts = (studentSubs, allData) => {
  const scored = studentSubs.filter(s => s.score > 0).sort((a, b) => {
    const nameA = a.experimentName || ''
    const nameB = b.experimentName || ''
    return nameA.localeCompare(nameB, 'zh')
  })

  // 璁＄畻鐝骇骞冲潎鍒?  const expAvgs = {}
  allData.filter(s => s.score > 0).forEach(s => {
    const name = s.experimentName || '鏈煡'
    if (!expAvgs[name]) expAvgs[name] = []
    expAvgs[name].push(s.score)
  })

  if (scoreChartContainer.value && scoreChart) {
    const labels = scored.map(s => s.experimentName || '瀹為獙')
    const scores = scored.map(s => s.score)
    const classAvg = labels.map(name => {
      const arr = expAvgs[name]
      return arr ? Math.round(arr.reduce((a, b) => a + b, 0) / arr.length) : 0
    })
    scoreChart.setOption({
      xAxis: { data: labels },
      series: [
        { name: '鎴愮哗', data: scores },
        { name: '鐝骇骞冲潎', data: classAvg }
      ]
    })
  }

  if (completionChartContainer.value && completionChart) {
    const completed = studentSubs.filter(s => s.status === 'completed').length
    const pending = studentSubs.length - completed
    const allExpCount = new Set(allData.map(s => s.experimentId)).size
    const notSubmitted = Math.max(0, allExpCount - studentSubs.length)
    completionChart.setOption({
      series: [{
        data: [
          { value: completed, name: '宸插畬鎴?, itemStyle: { color: '#67C23A' } },
          { value: pending, name: '杩涜涓?, itemStyle: { color: '#E6A23C' } },
          { value: notSubmitted, name: '鏈彁浜?, itemStyle: { color: '#F56C6C' } }
        ]
      }]
    })
  }
}

// 鍥捐〃鍒濆鍖?const initCharts = () => {

  // 鎴愮哗瓒嬪娍鍥?  if (scoreChartContainer.value) {
    scoreChart = echarts.init(scoreChartContainer.value)
    const scoreOption = {
      tooltip: {
        trigger: 'axis'
      },
      xAxis: {
        type: 'category',
        data: ['瀹為獙1', '瀹為獙2', '瀹為獙3', '褰撳墠瀹為獙', '瀹為獙5']
      },
      yAxis: {
        type: 'value',
        name: '鍒嗘暟',
        min: 0,
        max: 100
      },
      series: [
        {
          name: '鎴愮哗',
          type: 'line',
          data: [82, 88, 75, submission.value.score || 0, null],
          markPoint: {
            data: [
              { type: 'max', name: '鏈€楂樺垎' },
              { type: 'min', name: '鏈€浣庡垎' }
            ]
          }
        },
        {
          name: '鐝骇骞冲潎',
          type: 'line',
          data: [75, 78, 72, 80, null],
          lineStyle: {
            type: 'dashed'
          }
        }
      ]
    }
    scoreChart.setOption(scoreOption)
  }

  // 瀹屾垚鎯呭喌鍥?  if (completionChartContainer.value) {
    completionChart = echarts.init(completionChartContainer.value)
    const completionOption = {

      tooltip: {
        trigger: 'item',
        formatter: '{a} <br/>{b}: {c} ({d}%)'
      },
      legend: {
        orient: 'vertical',
        left: 'left',
        data: ['鎸夋椂瀹屾垚', '閫炬湡瀹屾垚', '鏈畬鎴?]
      },
      series: [
        {
          name: '瀹屾垚鎯呭喌',
          type: 'pie',
          radius: '70%',
          center: ['50%', '60%'],
          data: [
            { value: 4, name: '鎸夋椂瀹屾垚', itemStyle: { color: '#67C23A' } },
            { value: 1, name: '閫炬湡瀹屾垚', itemStyle: { color: '#E6A23C' } },
            { value: 0, name: '鏈畬鎴?, itemStyle: { color: '#F56C6C' } }
          ],
          emphasis: {
            itemStyle: {
              shadowBlur: 10,
              shadowOffsetX: 0,
              shadowColor: 'rgba(0, 0, 0, 0.5)'
            }
          }
        }
      ]
    }
    completionChart.setOption(completionOption)
  }
}

// 杩斿洖鎻愪氦鍒楄〃
const goBack = () => {
  router.go(-1) // 杩斿洖涓婁竴椤?}

// 鎵撳紑璇勫垎瀵硅瘽妗?const openGradeDialog = () => {
  gradeDialogVisible.value = true
}

// 鎻愪氦璇勫垎
const submitGrade = async () => {
  try {
    // 璋冪敤API鎻愪氦璇勫垎
    // await api.gradeSubmission(submissionId.value, gradeForm)

    // 鏇存柊鏈湴鏁版嵁
    submission.value = {
      ...submission.value,
      score: gradeForm.score,
      plagiarismRate: gradeForm.plagiarismRate,
      aiComment: gradeForm.aiComment,
      teacherComment: gradeForm.teacherComment,
      status: 'graded'
    }

    // 鐩存帴鏇存柊鎶ュ憡鏁版嵁涓殑鎴愮哗
    if (reportData.value) {
      reportData.value.score = gradeForm.score;
      console.log('璇勫垎鍚庢洿鏂版姤鍛婃暟鎹?', reportData.value);
    } else {
      // 濡傛灉鎶ュ憡鏁版嵁杩樻病鍑嗗濂斤紝鍒涘缓瀹?      prepareReportData();
      console.log('璇勫垎鍚庡垵濮嬪寲鎶ュ憡鏁版嵁:', reportData.value);
    }

    // 鏇存柊鎶ュ憡涓殑璇勮鍐呭
    updateReportWithComments();

    // 濡傛灉褰撳墠鍦ㄦ姤鍛婇瑙堥〉闈紝绔嬪嵆鍒锋柊鎶ュ憡瑙嗗浘
    if (activeTab.value === 'report' && reportGeneratorRef.value) {
      // 浣跨敤 nextTick 纭繚鍦―OM鏇存柊鍚庢墽琛?      nextTick(() => {
        console.log('灏濊瘯璋冪敤 updateReport 鏂规硶...');
        if (typeof reportGeneratorRef.value.updateReport === 'function') {
          console.log('璋冪敤 updateReport 鏂规硶鎴愬姛');
          reportGeneratorRef.value.updateReport();
        } else {
          console.warn('ReportGenerator 缁勪欢缂哄皯 updateReport 鏂规硶');
        }
      });
    }

    gradeDialogVisible.value = false
    ElMessage.success('璇勫垎鎴愬姛')
  } catch (error) {
    console.error('璇勫垎澶辫触:', error)
    ElMessage.error('璇勫垎澶辫触锛岃绋嶅悗閲嶈瘯')
  }
}

// 鎷掔粷鎻愪氦
// const rejectSubmission = () => {
//   ElMessageBox.confirm('纭畾瑕佹嫆缁濇娆℃彁浜ゅ悧锛熷鐢熷皢闇€瑕侀噸鏂版彁浜ゃ€?, '鎻愮ず', {
//     confirmButtonText: '纭畾',
//     cancelButtonText: '鍙栨秷',
//     type: 'warning'
//   }).then(async () => {
//     try {
//       // await api.rejectSubmission(submissionId.value)
//       submission.value.status = 'rejected'
//       ElMessage.success('宸叉嫆缁濇娆℃彁浜?)
//     } catch (error) {
//       console.error('鎿嶄綔澶辫触:', error)
//       ElMessage.error('鎿嶄綔澶辫触锛岃绋嶅悗閲嶈瘯')
//     }
//   }).catch(() => { })
// }

// 鐢熸垚AI璇勮
const generateAIComment = async () => {
  generatingComment.value = true
  try {
    const code = submission.value.code || ''
    const expName = submission.value.experimentName || '鏁版嵁缁撴瀯瀹為獙'
    const studentName = submission.value.studentName || ''

    // 璋冪敤鍚庣 DeepSeek chat API 鐢熸垚璇勮
    const prompt = `璇峰浠ヤ笅瀛︾敓鎻愪氦鐨?${expName}"瀹為獙浠ｇ爜杩涜绠€瑕佺偣璇勶紙150瀛椾互鍐咃級锛屽寘鎷紭鐐广€佷笉瓒冲拰鏀硅繘寤鸿锛歕n\n${code.substring(0, 3000)}`
    const response = await fetch(buildApiUrl('/api/chat'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify({ userInput: prompt })
    })

    if (!response.ok) throw new Error('AI鏈嶅姟璇锋眰澶辫触')

    // 璇诲彇娴佸紡鍝嶅簲
    const reader = response.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let aiComment = ''
    let done = false
    while (!done) {
      const result = await reader.read()
      done = result.done
      if (!done) {
        aiComment += decoder.decode(result.value, { stream: true })
      }
    }

    if (!aiComment.trim()) {
      throw new Error('AI鏈繑鍥炴湁鏁堣瘎璇?)
    }

    if (gradeDialogVisible.value) {
      gradeForm.aiComment = aiComment
    } else {
      submission.value.aiComment = aiComment
    }

    ElMessage.success('AI璇勮鐢熸垚鎴愬姛')
  } catch (error) {
    console.error('鐢熸垚AI璇勮澶辫触:', error)
    ElMessage.error('鐢熸垚AI璇勮澶辫触: ' + (error.message || '璇风◢鍚庨噸璇?))
  } finally {
    generatingComment.value = false
  }
}

// 淇敼AI璇勮
const editAIComment = () => {
  ElMessageBox.prompt('璇蜂慨鏀笰I璇勮', '淇敼璇勮', {
    confirmButtonText: '纭畾',
    cancelButtonText: '鍙栨秷',
    inputType: 'textarea',
    inputValue: submission.value.aiComment,
    inputPlaceholder: '璇疯緭鍏ヤ慨鏀瑰悗鐨凙I璇勮'
  }).then(({ value }) => {
    submission.value.aiComment = value
    ElMessage.success('AI璇勮宸蹭慨鏀?)
  }).catch(() => { })
}

// 閲嶆柊鐢熸垚AI璇勮
const regenerateAIComment = () => {
  ElMessageBox.confirm('纭畾瑕侀噸鏂扮敓鎴怉I璇勮鍚楋紵杩欏皢瑕嗙洊褰撳墠鐨勮瘎璇€?, '鎻愮ず', {
    confirmButtonText: '纭畾',
    cancelButtonText: '鍙栨秷',
    type: 'warning'
  }).then(() => {
    generateAIComment()
  }).catch(() => { })
}

// 淇敼鏁欏笀璇勮
const editTeacherComment = () => {
  editingTeacherComment.value = submission.value.teacherComment || ''
  isEditingComment.value = true
}

// 淇濆瓨鏁欏笀璇勮
const saveTeacherComment = async () => {
  try {
    // 杩欓噷搴旇璋冪敤API淇濆瓨璇勮
    // await api.saveTeacherComment(submissionId.value, editingTeacherComment.value)

    // 鏇存柊鏈湴鏁版嵁
    submission.value.teacherComment = editingTeacherComment.value

    isEditingComment.value = false
    ElMessage.success('璇勮淇濆瓨鎴愬姛')
  } catch (error) {
    console.error('淇濆瓨璇勮澶辫触:', error)
    ElMessage.error('淇濆瓨璇勮澶辫触锛岃绋嶅悗閲嶈瘯')
  }
}

// 鍙栨秷缂栬緫璇勮
const cancelEditComment = () => {
  isEditingComment.value = false
  editingTeacherComment.value = submission.value.teacherComment || ''
}

// 鍑嗗鎶ュ憡鏁版嵁
const prepareReportData = () => {
  if (!submission.value) return

  console.log('鍑嗗鎶ュ憡鏁版嵁锛屽綋鍓嶆垚缁?', submission.value.score) // 璋冭瘯鏃ュ織

  // 鍩虹淇℃伅
  reportData.value = {
    experimentName: submission.value.experimentName || '鏁版嵁缁撴瀯瀹為獙',
    studentName: submission.value.studentName || '鏈煡',
    studentId: submission.value.studentId || '鏈煡瀛﹀彿',
    className: submission.value.class || '鏈煡鐝骇',
    courseName: '鏁版嵁缁撴瀯',
    // 纭繚鎴愮哗姝ｇ‘浼犻€掞紝澶勭悊鍙兘鐨剈ndefined鎴杗ull鍊?    score: submission.value.score !== null && submission.value.score !== undefined
        ? Number(submission.value.score) : null,
    teacherName: '鎸囧鏁欏笀',
    labName: '璁＄畻鏈哄疄楠屽',
    labTime: new Date().toLocaleDateString(),
  }

  //鎻愬彇鍚勭珷鑺傚唴瀹癸紙濡傛灉鏈夋姤鍛婄殑鎯呭喌锛?  if (submission.value.report) {
    try {
      const report = submission.value.report

      // 鎻愬彇鍚勭珷鑺傚唴瀹?      const purposeMatch = report.match(/##?\s*瀹為獙鐩殑[^\n]*\n+([\s\S]+?)(?=##)/i)
      if (purposeMatch) reportData.value.purpose = purposeMatch[1].trim()

      const requirementsMatch = report.match(/##?\s*瀹為獙鐜[^\n]*\n+([\s\S]+?)(?=##)/i)
      if (requirementsMatch) reportData.value.requirements = requirementsMatch[1].trim()

      const tasksMatch = report.match(/##?\s*瀹為獙鍐呭[^\n]*\n+([\s\S]+?)(?=##)/i) ||
          report.match(/##?\s*瀹為獙浠诲姟[^\n]*\n+([\s\S]+?)(?=##)/i)
      if (tasksMatch) reportData.value.tasks = tasksMatch[1].trim()

      // 涓嶅啀浠嶮arkdown鎻愬彇steps锛岃€屾槸閫氳繃棰樼洰璇勮鐢熸垚

      const resultsMatch = report.match(/##?\s*瀹為獙缁撴灉[^\n]*\n+([\s\S]+?)(?=##)/i)
      if (resultsMatch) reportData.value.results = resultsMatch[1].trim()

      const summaryMatch = report.match(/##?\s*瀹為獙鎬荤粨[^\n]*\n+([\s\S]+?)(?=$)/i) ||
          report.match(/##?\s*蹇冨緱浣撲細[^\n]*\n+([\s\S]+?)(?=$)/i)
      if (summaryMatch) reportData.value.summary = summaryMatch[1].trim()
    } catch (e) {
      console.error('瑙ｆ瀽鎶ュ憡鍐呭澶辫触:', e)
    }
  }

  // 鏍规嵁瑙ｆ瀽鐨勯鐩敓鎴愬疄楠屾楠ゅ唴瀹?  if (parsedQuestions.value.length > 0) {
    updateReportWithComments()
  }

  console.log('鎶ュ憡鏁版嵁鍑嗗瀹屾垚锛屾垚缁╁€?', reportData.value.score) // 璋冭瘯鏃ュ織
}

// 澶勭悊鎶ュ憡鏁版嵁鏇存柊
const handleReportDataUpdate = (newData) => {
  reportData.value = newData
}

// 杩愯浠ｇ爜
// const runCode = async () => {
//   try {
//     // 杩欓噷搴旇璋冪敤API杩愯浠ｇ爜
//     // const result = await api.runStudentCode(submissionId.value)

//     // 妯℃嫙杩愯
//     await new Promise(resolve => setTimeout(resolve, 1000))

//     codeResult.value = {
//       success: true,
//       output: "缂栬瘧鎴愬姛!\n杩愯缁撴灉:\n1 -> 2 -> 3 -> NULL\n绋嬪簭鎵ц鏃堕棿: 0.002s"
//     }

//     ElMessage.success('浠ｇ爜杩愯鎴愬姛')
//   } catch (error) {
//     console.error('浠ｇ爜杩愯澶辫触:', error)
//     ElMessage.error('浠ｇ爜杩愯澶辫触')

//     codeResult.value = {
//       success: false,
//       output: "缂栬瘧閿欒:\nError: undefined reference to 'printLinkedList'\n浠ｇ爜缂栬瘧澶辫触锛岃妫€鏌ュ嚱鏁板０鏄庡拰瀹氫箟銆?
//     }
//   }
// }

// 澶嶅埗浠ｇ爜
const copyCode = () => {
  navigator.clipboard.writeText(submission.value.code)
      .then(() => {
        ElMessage.success('浠ｇ爜宸插鍒跺埌鍓创鏉?)
      })
      .catch(() => {
        ElMessage.error('澶嶅埗澶辫触锛岃鎵嬪姩澶嶅埗')
      })
}

// 涓嬭浇浠ｇ爜
const downloadCode = () => {
  const blob = new Blob([submission.value.code], { type: 'text/plain' })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = `${submission.value.experimentName}_${submission.value.studentName}.c`
  link.click()
  URL.revokeObjectURL(link.href)
}

// // 鏍煎紡鍖栦唬鐮?// const formatCode = () => {
//   ElMessage.info('浠ｇ爜鏍煎紡鍖栧姛鑳藉紑鍙戜腑')
// }

// // 鎵撳嵃鎶ュ憡
// const printReport = () => {
//   window.print()
// }

// // 涓嬭浇鎶ュ憡
// const downloadReport = () => {
//   if (!submission.value.report) {
//     ElMessage.warning('娌℃湁鎶ュ憡鍙笅杞?)
//     return
//   }

//   const blob = new Blob([submission.value.report], { type: 'text/markdown' })
//   const link = document.createElement('a')
//   link.href = URL.createObjectURL(blob)
//   link.download = `${submission.value.experimentName}_${submission.value.studentName}_鎶ュ憡.md`
//   link.click()
//   URL.revokeObjectURL(link.href)
// }

// 涓嬭浇 Word 鏂囨。
const downloadWordDoc = async () => {
  if (!reportData.value) {
    ElMessage.warning('娌℃湁鎶ュ憡鏁版嵁鍙笅杞?)
    return
  }

  try {
    // 鍒涘缓涓€涓柊瀵硅薄锛岄伩鍏嶅紩鐢ㄩ棶棰?    const exportData = { ...reportData.value }

    // 纭繚鎴愮哗姝ｇ‘
    if (submission.value.score !== undefined && submission.value.score !== null) {
      exportData.score = String(submission.value.score)
    }

    console.log('涓嬭浇Word鏂囨。鏃剁殑鎴愮哗:', exportData.score)

    // 濡傛灉鏈夋暀甯堣瘎璇紝灏嗗叾娣诲姞鍒版姤鍛婃暟鎹腑
    if (submission.value.teacherComment) {
      exportData.teacherComment = submission.value.teacherComment
    }

    // 纭繚璇勮宸叉洿鏂板埌steps
    updateReportWithComments()

    const docxGenerator = new DocxGenerator()
    const blob = await docxGenerator.generateStandardReport(exportData)

    // 涓嬭浇鏂囦欢鍚嶆牸寮? 瀛﹀彿_濮撳悕_瀹為獙鍚嶇О.docx
    const fileName = `${submission.value.studentId}_${submission.value.studentName}_${submission.value.experimentName}.docx`
    DocxGenerator.downloadReport(blob, fileName)

    ElMessage.success('Word鏂囨。涓嬭浇鎴愬姛')
  } catch (error) {
    console.error('鐢熸垚Word鏂囨。澶辫触:', error)
    ElMessage.error('鐢熸垚Word鏂囨。澶辫触锛岃绋嶅悗閲嶈瘯')
  }
}

// 鍓嶇鍙戦€佽姹傚埌鏈嶅姟鍣ㄧ
const downloadPDF = async () => {
  try {
    // 鏄剧ず鍔犺浇鎻愮ず
    const loadingInstance = ElLoading.service({
      lock: true,
      text: 'PDF鐢熸垚涓紝璇风◢鍊?..',
      background: 'rgba(0, 0, 0, 0.7)'
    });

    // 鍒涘缓涓€涓柊瀵硅薄锛岄伩鍏嶅紩鐢ㄩ棶棰?    const exportData = { ...reportData.value };

    // 鐩存帴浠巗ubmission涓幏鍙栨垚缁╋紝纭繚鎷垮埌鏈€鏂板€硷紝骞惰浆鎴愬瓧绗︿覆
    if (submission.value.score !== undefined && submission.value.score !== null) {
      exportData.score = String(submission.value.score);
    }

    console.log('涓嬭浇PDF鏂囨。鏃剁殑鎴愮哗:', exportData.score);

    // 濡傛灉鏈夋暀甯堣瘎璇紝灏嗗叾娣诲姞鍒版姤鍛婃暟鎹腑
    if (submission.value.teacherComment) {
      exportData.teacherComment = submission.value.teacherComment;
    }

    // 鍏堢敓鎴?Word 鏂囨。
    const docxGenerator = new DocxGenerator();
    const wordBlob = await docxGenerator.generateStandardReport(exportData);

    // 鍙戦€?Word 鏂囨。鍒版湇鍔″櫒杩涜杞崲
    const formData = new FormData();
    formData.append('wordFile', new Blob([wordBlob]), 'report.docx');

    const response = await axios.post('/api/api/convert-to-pdf', formData, {
      responseType: 'blob', // 閲嶈锛氭寚瀹氬搷搴旂被鍨嬩负blob
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    });

    // 鍏抽棴鍔犺浇鎻愮ず
    loadingInstance.close();

    // 涓嬭浇杩斿洖鐨凱DF
    const fileName = `${submission.value.studentId}_${submission.value.studentName}_${submission.value.experimentName}.pdf`;
    const url = window.URL.createObjectURL(new Blob([response.data]));
    const link = document.createElement('a');
    link.href = url;
    link.download = fileName;
    link.click();

    ElMessage.success('PDF鏂囨。涓嬭浇鎴愬姛');
  } catch (error) {
    console.error('鐢熸垚PDF鏂囨。澶辫触:', error);
    ElMessage.error('鐢熸垚PDF鏂囨。澶辫触锛岃绋嶅悗閲嶈瘯');

    // 鍏抽棴鍙兘瀛樺湪鐨勫姞杞芥彁绀?    const loadingInstance = ElLoading.service();
    loadingInstance.close();
  }
}

// 绐楀彛澶у皬鍙樺寲鏃堕噸缁樺浘琛?const handleResize = () => {
  scoreChart?.resize()
  completionChart?.resize()
}

onMounted(() => {
  loadSubmissionDetail()
  window.addEventListener('resize', handleResize)

  // 娣诲姞涓€涓垵濮嬪寲鏍囪锛岀敤浜庤拷韪粍浠舵槸鍚﹀凡鍒濆鍖?  let reportComponentInitialized = false;

  // 鐩戝惉鏍囩椤靛彉鍖栵紝褰撳垏鎹㈠埌鎶ュ憡鏍囩椤垫椂鍔犺浇鎶ュ憡鏁版嵁
  watch(() => activeTab.value, (newTab) => {
    if (newTab === 'report' && submission.value) {
      // 寮哄埗閲嶆柊鍑嗗鎶ュ憡鏁版嵁锛岀‘淇濆寘鍚渶鏂版垚缁╁拰璇勮
      prepareReportData();

      // 纭繚ReportGenerator缁勪欢鏇存柊
      nextTick(() => {
        if (reportGeneratorRef.value && typeof reportGeneratorRef.value.updateReport === 'function') {
          console.log('鍒囨崲鍒版姤鍛婃爣绛鹃〉锛屾洿鏂版姤鍛婏紝褰撳墠鎴愮哗:', reportData.value.score);
          reportGeneratorRef.value.updateReport();
          reportComponentInitialized = true;
        } else {
          console.warn('ReportGenerator缁勪欢缂哄皯updateReport鏂规硶鎴栫粍浠舵湭鎸傝浇');
          // 缁勪欢鏈氨缁紝璁剧疆寤惰繜閲嶈瘯
          setTimeout(() => {
            if (reportGeneratorRef.value && typeof reportGeneratorRef.value.updateReport === 'function') {
              reportGeneratorRef.value.updateReport();
              reportComponentInitialized = true;
            }
          }, 500);
        }
      });
    }
  }, { immediate: true }); // 娣诲姞immediate:true纭繚鍒濆鍔犺浇鏃朵篃鎵ц

  // 鐩戝惉鎴愮哗鍙樺寲锛岀‘淇濇姤鍛婃暟鎹悓姝ユ洿鏂?  watch(() => submission.value.score, (newScore) => {
    if (reportData.value) {
      console.log('鎴愮哗宸插彉鏇翠负:', newScore);
      reportData.value.score = newScore;
      // 濡傛灉褰撳墠鍦ㄦ姤鍛婇瑙堥〉闈紝鍒锋柊鎶ュ憡瑙嗗浘
      if (activeTab.value === 'report' && reportGeneratorRef.value) {
        nextTick(() => {
          if (typeof reportGeneratorRef.value.updateReport === 'function') {
            reportGeneratorRef.value.updateReport();
          }
        });
      }
    }
  });

  // 鐩戝惉 reportGeneratorRef 浠ュ鐞嗙粍浠跺悗鏈熸寕杞界殑鎯呭喌
  watch(() => reportGeneratorRef.value, (newRef) => {
    if (newRef && !reportComponentInitialized && activeTab.value === 'report' && reportData.value) {
      console.log('ReportGenerator缁勪欢宸叉寕杞斤紝鍒濆鍖栨姤鍛婃暟鎹?);
      nextTick(() => {
        if (typeof newRef.updateReport === 'function') {
          newRef.updateReport();
          reportComponentInitialized = true;
        }
      });
    }
  });

  // 鐩戝惉棰樼洰璇勮鍙樺寲锛屽悓姝ユ洿鏂版姤鍛婂唴瀹?  watch(() => parsedQuestions.value, () => {
    if (parsedQuestions.value.length > 0) {
      updateReportWithComments();
    }
  }, { deep: true });
})
</script>

<style scoped>
.submission-detail {
  height: 100%;
}

.my-page-header {
  padding: 20px;
}

.page-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.info-card {
  margin-bottom: 0;
}

.student-info {
  display: flex;
  align-items: center;
  gap: 20px;
}

.student-details {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.student-details h3 {
  margin: 0;
  font-size: 18px;
  line-height: 1.5;
}

.detail-item {
  font-size: 14px;
  color: #606266;
}

.label {
  font-weight: 500;
  margin-right: 5px;
}

.status-tag {
  margin-top: 10px;
  align-self: flex-start;
}

.submission-stats {
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.stat-item {
  text-align: center;
  padding: 10px;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin-bottom: 5px;
}

.stat-value {
  font-size: 24px;
  font-weight: 600;
  color: #303133;
}

.time-value {
  font-size: 18px;
}

.highlighted {
  color: #67C23A;
}

.low-plagiarism {
  color: #67C23A;
}

.medium-plagiarism {
  color: #E6A23C;
}

.high-plagiarism {
  color: #F56C6C;
}

.full-width-btn {
  margin-left: 0;
  width: 100%;
}


.code-header,
.report-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 15px;
  padding-right: 10px;
}

.code-header h3,
.report-header h3,
.history-header h3,
.performance-header h3 {
  margin: 0;
  font-size: 16px;
}

.code-display {
  background-color: #f5f7fa;
  border-radius: 4px;
  padding: 15px;
  font-family: 'Courier New', monospace;
  white-space: pre-wrap;
  font-size: 14px;
  line-height: 1.5;
  max-height: 500px;
  overflow-y: auto;
}

.code-result {
  margin-top: 20px;
  border-top: 1px solid #ebeef5;
  padding-top: 15px;
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.result-header h3 {
  margin: 0;
  font-size: 16px;
}

.result-output {
  background-color: #f5f7fa;
  border-radius: 4px;
  padding: 15px;
  font-family: 'Courier New', monospace;
  max-height: 200px;
  overflow-y: auto;
  white-space: pre-wrap;
  font-size: 14px;
  line-height: 1.5;
}

.markdown-content {
  padding: 10px;
  line-height: 1.6;
}

.markdown-content :deep(h1),
.markdown-content :deep(h2),
.markdown-content :deep(h3) {
  margin-top: 20px;
  margin-bottom: 10px;
}

.markdown-content :deep(p),
.markdown-content :deep(ul),
.markdown-content :deep(ol) {
  margin-bottom: 16px;
}

.markdown-content :deep(code) {
  background-color: #f5f7fa;
  padding: 2px 4px;
  border-radius: 3px;
  font-family: 'Courier New', monospace;
}

.markdown-content :deep(pre) {
  background-color: #f5f7fa;
  padding: 15px;
  border-radius: 4px;
  margin-bottom: 16px;
}

.markdown-content :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin-bottom: 16px;
}

.markdown-content :deep(th),
.markdown-content :deep(td) {
  padding: 8px;
  text-align: left;
  border-bottom: 1px solid #ebeef5;
}

.history-item {
  padding: 10px 0;
}

.history-title {
  font-weight: 500;
  margin-bottom: 5px;
}

.history-content {
  color: #606266;
  font-size: 14px;
}

.history-actions {
  margin-top: 5px;
}

.history-info {
  margin-bottom: 15px;
  padding-bottom: 15px;
  border-bottom: 1px solid #ebeef5;
}

.history-code-dialog .code-display {
  max-height: 400px;
}

.chart-card {
  height: 400px;
}

.chart-container {
  width: 30vw;
  height: 300px;
}

.chart-header {
  display: flex;
  align-items: center;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.performance-card {
  margin-top: 20px;
}

.performance-analysis {
  margin-top: 20px;
}

.performance-analysis h4,
.learning-recommendation h4 {
  margin-top: 0;
  margin-bottom: 10px;
}

.analysis-content {
  padding: 10px;
  background-color: #f5f7fa;
  border-radius: 4px;
  line-height: 1.6;
}

.recommendation-content {
  padding: 10px 0;
}

.resource-links {
  margin-top: 10px;
}

.resource-links h5 {
  margin: 5px 0;
}

.resource-links ul {
  padding-left: 20px;
}

.resource-links li {
  list-style-type: disc;
}

.ai-comment-card,
.teacher-comment-card {
  margin-bottom: 20px;
}

/* 棰樼洰閫夐」鍗℃牱寮?*/
.question-tabs {
  margin-bottom: 20px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
}

.question-container {
  padding: 15px;
}

.test-results {
  margin-top: 15px;
  padding: 10px;
  background-color: #f8f8f8;
  border-radius: 4px;
}

.test-results h4 {
  margin-top: 0;
  margin-bottom: 10px;
}

.ai-header,
.teacher-header {
  display: flex;
  align-items: center;
  gap: 10px;
}

.ai-comment-content,
.teacher-comment-content {
  padding: 10px;
  background-color: #f5f7fa;
  border-radius: 4px;
  line-height: 1.6;
}

.generate-comment {
  margin-top: 5px;
  text-align: right;
}

.unit {
  margin-left: 5px;
}

.plagiarism-result {
  line-height: 1.5;
}

.code-comparison {
  display: flex;
  margin-top: 10px;
  gap: 20px;
}

.comparison-left,
.comparison-right {
  flex: 1;
}

.comparison-divider {
  width: 1px;
  background-color: #dcdfe6;
}

.code-snippet {
  background-color: #f5f7fa;
  padding: 10px;
  border-radius: 4px;
  font-family: 'Courier New', monospace;
  white-space: pre-wrap;
  font-size: 13px;
  max-height: 300px;
  overflow-y: auto;
}

/* 璇勫垎鏍峰紡 */
.score-excellent {
  color: #67C23A;
  font-weight: bold;
}

.score-good {
  color: #409EFF;
  font-weight: bold;
}

.score-pass {
  color: #E6A23C;
}

.score-fail {
  color: #F56C6C;
  font-weight: bold;
}

/* 鏁欏笀璇勮缂栬緫鍖哄煙鏍峰紡 */
.comment-card {
  margin-top: 20px;
}

.comment-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.comment-header .title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.comment-edit {
  padding: 10px 0;
}

.comment-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 10px;
}

.comment-view {
  padding: 10px;
  min-height: 80px;
}

.comment-content {
  background-color: #f5f7fa;
  border-radius: 4px;
  padding: 15px;
  line-height: 1.6;
}

/* 宸︿晶鍖哄煙鏍峰紡浼樺寲 */
.info-card,
.stats-card,
.action-card,
.ai-comment-card,
.teacher-comment-card {
  margin-bottom: 15px;
}

.stats-card .stat-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 10px;
}

.stats-card .stat-item {
  background-color: #f7f9fc;
  border-radius: 8px;
  padding: 15px;
  transition: all 0.3s ease;
}

.stats-card .stat-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
}

.action-card .action-buttons {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.text-center {
  text-align: center;
}

/* 鍙充晶鍖哄煙鏍峰紡浼樺寲 */
.content-card {
  height: 80vh;
  margin-bottom: 50px;
}

.main-tabs {
  height: 70vh;

}

/* 淇敼鏍囩椤靛唴瀹瑰尯鍩熸牱寮?*/
.main-tabs :deep(.el-tabs__content) {
  height: calc(100% - 55px);
  position: relative;
  /* 娣诲姞鐩稿瀹氫綅 */
}

/* 涓烘瘡涓爣绛鹃〉闈㈡澘娣诲姞婊氬姩鍔熻兘 */
.main-tabs :deep(.el-tab-pane) {
  height: 100%;
  overflow-y: auto;
}

.code-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

/* 淇敼鎶ュ憡瀹瑰櫒鏍峰紡锛岀‘淇濆彲浠ユ粴鍔?*/
.report-container {
  padding-bottom: 100px;
  max-width: 95%;
  margin: 0 auto;
  font-size: 0.95em;
  overflow-y: auto;
  /* 娣诲姞鍨傜洿婊氬姩鏉?*/
  max-height: 70vh;
  /* 闄愬埗鏈€澶ч珮搴︼紝纭繚闇€瑕佹粴鍔?*/
}

/* 纭繚鎶ュ憡鐢熸垚鍣ㄧ粍浠跺彲浠ユ粴鍔?*/
.report-container :deep(.report-generator) {
  width: 100%;
  max-width: 100%;
  overflow-y: auto;
  /* 鏀逛负visible锛岃婊氬姩鐢辩埗瀹瑰櫒澶勭悊 */
}


/* 缇庡寲鎸夐挳鏍峰紡 */
.el-button.is-round {
  border-radius: 20px;
  padding-left: 15px;
  padding-right: 15px;
  transition: all 0.3s ease;
}

.el-button.is-round:hover {
  transform: translateY(-2px);
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
}

/* 缇庡寲鍗＄墖鏍峰紡 */
.el-card {
  transition: all 0.3s ease;
  border-radius: 8px;
  overflow: hidden;
}

/* 鎵撳嵃鏍峰紡浼樺寲 */
@media print {

  .my-page-header,
  .info-card,
  .stats-card,
  .action-card,
  .ai-comment-card,
  .teacher-comment-card,
  .el-tabs__header,
  .report-header,
  .comment-card {
    display: none !important;
  }

  .page-content {
    padding: 0 !important;
    margin: 0 !important;
  }

  .content-card {
    box-shadow: none !important;
    border: none !important;
  }

  .report-container {
    max-width: 100% !important;
    padding: 0 !important;
    margin: 0 !important;
  }
}

.performance {
  padding-bottom: 20px;
  max-width: 95%;
  margin: 0 auto;
  font-size: 0.95em;
  overflow-y: auto;
  /* 娣诲姞鍨傜洿婊氬姩鏉?*/
  max-height: 70vh;
  overflow: hidden;
}

.ai-comment-content :deep(h1),
.ai-comment-content :deep(h2),
.ai-comment-content :deep(h3) {
  margin-top: 1em;
  margin-bottom: 0.5em;
}

.ai-comment-content :deep(p) {
  margin-bottom: 0.8em;
}

.ai-comment-content :deep(ul),
.ai-comment-content :deep(ol) {
  padding-left: 2em;
  margin-bottom: 1em;
}

.ai-comment-content :deep(code) {
  background-color: #f5f7fa;
  padding: 0.2em 0.4em;
  border-radius: 3px;
  font-family: 'Courier New', monospace;
}

/* 娣诲姞璇勮鐩稿叧鏍峰紡 */
.teacher-comment-image {
  margin: 10px 0;
  max-width: 100%;
}

.teacher-comment-image img {
  max-width: 100%;
  border-radius: 4px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
}

.teacher-comment-preview {
  max-width: 600px;
  margin: 0 auto;
  font-family: 'Microsoft YaHei', sans-serif;
}
</style>


