<template>
  <div class="submission-detail">
    <page-header class="my-page-header" title="学生提交详情" :description="`${studentName} 的实验提交`">
      <el-button @click="goBack" icon="Back">返回提交列表</el-button>
    </page-header>

    <div class="page-content" v-loading="loading">
      <el-row :gutter="20">
        <!-- 左侧区域：学生信息、统计数据及小卡片 -->
        <el-col :span="6">
          <!-- 学生基本信息卡片 -->
          <el-card class="info-card">
            <div class="student-info">
              <div class="avatar-container text-center">
                <el-avatar :size="80"
                           :src="submission.studentAvatar || 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png'" />
              </div>
              <div class="student-details">
                <h3 class="text-center">{{ submission.studentName }}</h3>
                <div class="detail-item">
                  <span class="label">学号：</span>
                  <span>{{ submission.studentId }}</span>
                </div>
                <div class="detail-item">
                  <span class="label">班级：</span>
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

          <!-- 统计数据卡片 -->
          <el-card class="stats-card">
            <div class="stat-grid">
              <div class="stat-item">
                <div class="stat-label">上机成绩评分</div>
                <div class="stat-value" :class="{ 'highlighted': submission.status === 'graded' }">
                  {{ submission.score !== null ? submission.score : '未评分' }}
                </div>
              </div>
              <div class="stat-item">
                <div class="stat-label">查重率</div>
                <div class="stat-value" :class="{
                  'low-plagiarism': submission.plagiarismRate < 15,
                  'medium-plagiarism': submission.plagiarismRate >= 15 && submission.plagiarismRate < 30,
                  'high-plagiarism': submission.plagiarismRate >= 30
                }">
                  {{ submission.plagiarismRate !== null ? `${submission.plagiarismRate}%` : '未检测' }}
                </div>
              </div>
              <div class="stat-item">
                <div class="stat-label">提交时间</div>
                <div class="stat-value time-value">{{ submission.date }}</div>
              </div>
            </div>
          </el-card>

          <!-- 操作按钮卡片 -->
          <el-card class="action-card">
            <div class="action-buttons">
              <el-button type="primary" @click="openGradeDialog" class="full-width-btn">
                <el-icon>
                  <Edit />
                </el-icon>{{ submission.status === 'graded' ? '重新评分' : '评分' }}
              </el-button>
              <!-- <el-button type="danger" @click="rejectSubmission" v-if="submission.status !== 'rejected'"
                class="full-width-btn">
                <el-icon>
                  <Close />
                </el-icon>拒绝提交
              </el-button> -->
              <!-- 其他按钮 -->

              <!-- <el-button 
                type="success"
                @click="generateAIComment"
                :loading="generatingComment"
                :disabled="generatingComment"
                class="full-width-btn"
              >
                <el-icon><Magic /></el-icon>AI辅助评测
              </el-button> -->

            </div>
          </el-card>

          <!-- AI评测结果卡片 -->
          <!-- <el-card class="ai-comment-card" v-if="submission.aiComment">
            <template #header>
              <div class="card-header">
                <div class="ai-header">
                  <el-icon>
                    <Magic />
                  </el-icon>
                  <span>AI点评</span>
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

        <!-- 右侧区域：代码和报告内容 -->
        <el-col :span="18">
          <!-- 提交内容标签页 -->
          <el-card class="content-card">
            <el-tabs v-model="activeTab" class="main-tabs">
              <el-tab-pane label="代码" name="code">
                <div class="code-header">
                  <h3>实验代码</h3>
                  <div class="code-actions">
                    <el-button type="info" @click="copyCode">
                      <el-icon>
                        <CopyDocument />
                      </el-icon>
                      复制代码
                    </el-button>
                    <el-dropdown>
                      <el-button type="success">
                        <el-icon>
                          <Document />
                        </el-icon>
                        文件操作
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
                            下载代码
                          </el-dropdown-item>
                          <!-- <el-dropdown-item @click="formatCode">
                            <el-icon>
                              <Operation />
                            </el-icon>
                            格式化代码
                          </el-dropdown-item> -->
                        </el-dropdown-menu>
                      </template>
                    </el-dropdown>
                  </div>
                </div>

                <!-- 题目列表显示 -->
                <el-tabs v-model="activeQuestionTab" tab-position="left" class="question-tabs"
                         v-if="parsedQuestions.length > 0">
                  <el-tab-pane v-for="(question, index) in parsedQuestions" :key="index" :label="`第${question.number}题`"
                               :name="String(index)">
                    <div class="question-container">
                      <pre class="code-display"><code>{{ question.code }}</code></pre>

                      <div class="test-results" v-if="question.testResults">
                        <h4>测试结果</h4>
                        <div v-html="formatTestResults(question.testResults)"></div>
                      </div>

                      <el-divider content-position="left">教师评语</el-divider>

                      <!-- 在评语编辑区添加ref引用 -->
                      <div class="comment-edit" ref="commentDivs">
                        <el-input v-model="question.comment" type="textarea" :rows="3" placeholder="请输入对本题的评语..."
                                  @input="updateQuestionComment(index, $event)" />
                        <div class="comment-actions">
                          <el-button type="primary" size="small" @click="saveQuestionComment(index)"
                                     :loading="question.saving">
                            保存评语
                          </el-button>
                        </div>
                      </div>

                    </div>
                  </el-tab-pane>
                </el-tabs>

                <!-- 如果没有解析出题目，则显示完整代码 -->
                <pre class="code-display" v-else><code>{{ submission.code }}</code></pre>

                <!-- 代码运行结果 -->
                <div class="code-result" v-if="codeResult">
                  <div class="result-header">
                    <h3>运行结果</h3>
                    <el-tag :type="codeResult.success ? 'success' : 'danger'">
                      {{ codeResult.success ? '运行成功' : '运行失败' }}
                    </el-tag>
                  </div>
                  <pre class="result-output">{{ codeResult.output }}</pre>
                </div>
              </el-tab-pane>

              <el-tab-pane label="实验报告" name="report">
                <div class="report-header">
                  <h3>实验报告</h3>
                  <div class="report-actions">
                    <!-- <el-button type="primary" round size="small" @click="printReport">
                      <el-icon>
                        <Printer />
                      </el-icon>
                      打印报告
                    </el-button> -->
                    <el-dropdown>
                      <el-button type="success">
                        <el-icon>
                          <Download />
                        </el-icon>
                        下载报告
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
                            下载 Word 文档
                          </el-dropdown-item>
                          <el-dropdown-item @click="downloadPDF">
                            <el-icon>
                              <Document />
                            </el-icon>
                            下载 PDF 文档
                          </el-dropdown-item>
                        </el-dropdown-menu>
                      </template>
                    </el-dropdown>
                  </div>
                </div>

                <div v-if="submission.report" class="report-container">
                  <!-- 使用标准报告组件 -->
                  <report-generator :report-data="reportData" @update:report-data="handleReportDataUpdate"
                                    ref="reportGeneratorRef" />

                </div>
                <el-empty v-else description="学生未提交实验报告"></el-empty>
              </el-tab-pane>

              <!-- <el-tab-pane label="学习历程" name="history">
                <div class="history-header">
                  <h3>学习历程与代码提交记录</h3>
                </div>

                <el-timeline>
                  <el-timeline-item v-for="(item, index) in submissionHistory" :key="index" :timestamp="item.time"
                    :color="getHistoryItemColor(item.type)">
                    <div class="history-item">
                      <div class="history-title">{{ item.title }}</div>
                      <div class="history-content">{{ item.content }}</div>
                      <div class="history-actions" v-if="item.code">
                        <el-button type="primary" round size="small" @click="viewHistoryCode(item)">
                          查看代码
                        </el-button>
                      </div>
                    </div>
                  </el-timeline-item>
                </el-timeline>
              </el-tab-pane> -->

              <el-tab-pane label="学生表现" name="performance" class="performance">
                <div class="performance-header">
                  <h3>学习表现分析</h3>
                </div>

                <el-row :gutter="20">
                  <el-col :span="12">
                    <el-card class="chart-card" shadow="hover">
                      <template #header>
                        <div class="chart-header">
                          <span>实验成绩趋势</span>
                        </div>
                      </template>
                      <div class="chart-container" ref="scoreChartContainer"></div>
                    </el-card>
                  </el-col>

                  <el-col :span="12">
                    <el-card class="chart-card" shadow="hover">
                      <template #header>
                        <div class="chart-header">
                          <span>实验完成情况</span>
                        </div>
                      </template>
                      <div class="chart-container" ref="completionChartContainer"></div>
                    </el-card>
                  </el-col>
                </el-row>

                <el-card class="performance-card" shadow="hover">
                  <template #header>
                    <div class="card-header">
                      <span>综合表现评估</span>
                      <!-- <el-button type="primary" round size="small" @click="generatePerformanceReport">
                        生成综合评估报告
                      </el-button> -->
                    </div>
                  </template>

                  <el-descriptions :column="3" border>
                    <el-descriptions-item label="平均成绩">
                      <span :class="getScoreClass(studentPerformance.averageScore)">
                        {{ studentPerformance.averageScore }}
                      </span>
                    </el-descriptions-item>
                    <el-descriptions-item label="实验完成率">
                      {{ studentPerformance.completionRate }}%
                    </el-descriptions-item>
                    <el-descriptions-item label="班级排名">
                      第 {{ studentPerformance.classRank }} 名
                    </el-descriptions-item>
                    <el-descriptions-item label="作业提交及时性">
                      <el-rate v-model="studentPerformance.punctuality" disabled show-score
                               :colors="['#F56C6C', '#E6A23C', '#67C23A']" />
                    </el-descriptions-item>
                    <el-descriptions-item label="代码质量评分">
                      <el-rate v-model="studentPerformance.codeQuality" disabled show-score
                               :colors="['#F56C6C', '#E6A23C', '#67C23A']" />
                    </el-descriptions-item>
                    <el-descriptions-item label="沟通参与度">
                      <el-rate v-model="studentPerformance.participation" disabled show-score
                               :colors="['#F56C6C', '#E6A23C', '#67C23A']" />
                    </el-descriptions-item>
                  </el-descriptions>

                  <div class="performance-analysis">
                    <h4>AI助教点评</h4>
                    <div class="ai-comment-content" v-html="renderMarkdown(submission.aiRemarks)"></div>
                  </div>

                  <el-divider />

                  <div class="learning-recommendation">
                    <h4>学习建议</h4>
                    <el-collapse v-if="learningRecommendations.length > 0">
                      <el-collapse-item v-for="(rec, index) in learningRecommendations" :key="index" :title="rec.title">
                        <div class="recommendation-content">
                          <p>{{ rec.content }}</p>
                          <div class="resource-links" v-if="rec.resources && rec.resources.length">
                            <h5>推荐资源：</h5>
                            <ul>
                              <li v-for="(resource, rIndex) in rec.resources" :key="rIndex">
                                <a :href="resource.url" target="_blank">{{ resource.name }}</a>
                              </li>
                            </ul>
                          </div>
                        </div>
                      </el-collapse-item>
                    </el-collapse>
                    <el-empty v-else description="暂无学习建议，请点击'生成综合评估报告'生成"></el-empty>
                  </div>
                </el-card>
              </el-tab-pane>
            </el-tabs>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <!-- 评分对话框 -->
    <el-dialog v-model="gradeDialogVisible" title="评分" width="500px">
      <el-form :model="gradeForm" label-width="100px">
        <el-form-item label="学生">
          <span>{{ submission.studentName }}</span>
        </el-form-item>

        <el-form-item label="实验名称">
          <span>{{ submission.experimentName }}</span>
        </el-form-item>

        <el-form-item label="上机成绩得分" prop="score">
          <el-input-number v-model="gradeForm.score" :min="0" :max="100" :precision="1" style="width: 150px;" />
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="gradeDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitGrade">提交评分</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 历史代码对话框 -->
    <el-dialog v-model="historyCodeVisible" :title="selectedHistory ? selectedHistory.title : '历史代码'" width="70%">
      <div v-if="selectedHistory" class="history-code-dialog">
        <div class="history-info">
          <div><strong>提交时间：</strong>{{ selectedHistory.time }}</div>
          <div><strong>描述：</strong>{{ selectedHistory.content }}</div>
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
  Operation, Printer, Magic, ChatLineRound, Check, Close, Edit
} from '@element-plus/icons-vue'
import ReportGenerator from '../../components/ReportGenerator.vue'
// 引入 DocxGenerator
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

// 题目解析相关
const activeQuestionTab = ref('0') // 默认选中第一题
const parsedQuestions = ref([]) // 解析后的题目列表

// 评分相关
const gradeDialogVisible = ref(false)
const gradeForm = reactive({
  score: 0,
  plagiarismRate: 0,
  aiComment: '',
  teacherComment: ''
})
const generatingComment = ref(false)

// 历史记录相关
const submissionHistory = ref([])
const historyCodeVisible = ref(false)
const selectedHistory = ref(null)

// 学生表现（从真实数据计算）
const studentPerformance = reactive({
  averageScore: 0,
  completionRate: 0,
  classRank: '-',
  punctuality: 0,
  codeQuality: 0,
  participation: 0,
  aiAnalysis: ''
})

// 学习建议
const learningRecommendations = ref([])

// 新增报告相关变量
const reportData = ref({})
const reportGeneratorRef = ref(null)
const isEditingComment = ref(false)
const editingTeacherComment = ref('')





// 解析提交代码，按题目分割
const parseQuestionCode = () => {
  if (!submission.value || !submission.value.code) return

  // 使用正则表达式匹配所有题目
  const regex = /第\s*(\d+)\s*题如下:([\s\S]*?)(?=第\s*\d+\s*题如下:|$)/g
  const code = submission.value.code
  const questions = []

  let match
  while ((match = regex.exec(code)) !== null) {
    const questionNumber = match[1]
    let questionCode = match[2].trim()

    // 提取测试结果表格（如果有）
    const testResultsRegex = /([\s\S]*?)((?:\|\s*测试点[\s\S]*?)+$)/
    const resultMatch = questionCode.match(testResultsRegex)

    let testResults = null
    if (resultMatch) {
      questionCode = resultMatch[1].trim()
      testResults = resultMatch[2].trim()
    }
    // 添加到题目列表，包括代码、测试结果、评语和保存状态
    questions.push({
      number: parseInt(questionNumber),
      code: questionCode,
      testResults,
      comment: '', // 初始评语为空
      saving: false // 保存状态，用于控制按钮loading
    })
  }

  parsedQuestions.value = questions

  // 如果没有解析出题目，将整个代码作为一个题目
  if (questions.length === 0 && code) {
    parsedQuestions.value = [{
      number: 1,
      code: code,
      testResults: null,
      comment: '',
      saving: false
    }]
  }
}

// 格式化测试结果为HTML
const formatTestResults = (resultsText) => {
  if (!resultsText) return ''
  // 简单替换保持表格格式
  return resultsText.replace(/\|/g, '|')
      .replace(/\n/g, '<br>')
      .replace(/\s/g, '&nbsp;')
}

// 更新题目评语
const updateQuestionComment = (index, comment) => {
  parsedQuestions.value[index].comment = comment
  updateReportWithComments()
}

// 渲染Markdown格式文本为HTML
const renderMarkdown = (text) => {
  if (!text) return ''
  const rawHtml = marked.parse(text)
  return DOMPurify.sanitize(rawHtml)
}

const splitAiRemarksToQuestions = () => {
  const aiRemarks = submission.value.aiRemarks || '';
  // 假设格式：第1题评语: ... 第2题评语: ... 总评语: ...
  const questionRegex = /题目\s*([123456789\d]+)[:：]([\s\S]*?)(?=题目[123456789\d]+[:：]|总体评估|$)/g;
  const summaryRegex = /总体评估([\s\S]*)$/;
  let match;
  let questionComments = [];
  while ((match = questionRegex.exec(aiRemarks)) !== null) {
    questionComments.push(match[2].trim());
  }
  // 只保留总评语
  const summaryMatch = aiRemarks.match(summaryRegex);
  if (summaryMatch) {
    submission.value.aiRemarks = summaryMatch[1].trim();
  } else {
    submission.value.aiRemarks = '';
  }
  // 设置每题的默认评语
  parsedQuestions.value.forEach((q, i) => {
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
        教师评语：
      </h3>
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



// 保存题目评语
const saveQuestionComment = async (index) => {
  const question = parsedQuestions.value[index];

  if (!question.saving) {
    question.saving = true;
  }

  try {
    await api.saveQuestionComment(submissionId.value, index, question.comment);

    // 计算合适的图片宽度（基于当前视图宽度）
    const viewportWidth = window.innerWidth;
    // 在小屏幕上减小宽度，大屏幕保持合理大小
    const imageWidth = Math.min(viewportWidth * 0.1, 500);

    // 创建评语容器
    const commentContainer = document.createElement('div');
    commentContainer.className = 'teacher-comment-preview';
    commentContainer.style.width = `${imageWidth}px`;
    commentContainer.style.backgroundColor = '#ffffff';
    commentContainer.style.position = 'absolute';
    commentContainer.style.left = '-9999px';
    commentContainer.style.top = '-9999px';

    // 修改评语容器HTML部分
    commentContainer.innerHTML = `
  <div style="padding:15px; border:1px solid #ddd; background:#f9f9f9; width:100%; box-sizing:border-box;">
    <h3 style="margin-top:0; color:#333; font-size:16px; border-bottom:1px solid #eee; padding-bottom:8px; color: red" class="handwritten-title">
      教师评语：
    </h3>
    <div style="color:#333; line-height:1.5; font-size:14px; white-space:pre-wrap; color: red" class="handwritten-romantic">
      ${question.comment.replace(/\n/g, '<br>')}
    </div>
  </div>
`;

    // 添加字体加载检查
    await ensureFontsLoaded();

    document.body.appendChild(commentContainer);

    // 使用html2canvas生成图片
    const canvas = await html2canvas(commentContainer, {
      backgroundColor: '#ffffff',
      scale: 2, // 提高清晰度
      logging: true,
      useCORS: true,
      width: imageWidth,
      // 增加一个小延迟以确保字体完全应用
      timeout: 1000
    });

    document.body.removeChild(commentContainer);

    // 指定PNG格式
    const imageDataUrl = canvas.toDataURL('image/png', 1.0);
    question.commentImage = imageDataUrl;

    // 存储生成的图片尺寸，便于后续处理
    question.commentImageWidth = imageWidth;

    updateReportWithComments();
    ElMessage.success(`第${question.number}题评语保存成功`);
  } catch (error) {
    console.error('保存评语失败:', error);
    ElMessage.error(`第${question.number}题评语保存失败: ${error.message}`);
  } finally {
    question.saving = false;
  }
};

// 辅助函数：确保字体已加载
const ensureFontsLoaded = () => {
  return new Promise((resolve) => {
    // 使用FontFaceObserver库检查字体加载
    // 如果不使用该库，可以使用简单的超时方法
    setTimeout(resolve, 500); // 给字体加载预留500ms时间

    // 如果使用FontFaceObserver库，代码会是：
    // const zitangKai = new FontFaceObserver('ZitangKai');
    // const maoShanCat = new FontFaceObserver('MaoShanCat');
    // Promise.all([zitangKai.load(), maoShanCat.load()]).then(resolve);
  });
};

// 更新报告数据中的实验步骤，包含代码和评语
const updateReportWithComments = () => {
  if (!reportData.value) {
    prepareReportData();
  }

  // 构建steps内容，包含题目、代码和评语图片
  let stepsContent = '';
  parsedQuestions.value.forEach((question) => {
    stepsContent += `### 第${question.number}题\n\n`;
    stepsContent += '```c\n' + question.code + '\n```\n\n';

    // 如果有评语图片，添加评语图片标记
    if (question.commentImage) {
      stepsContent += `<div class="comment-image-container" data-image="${question.commentImage}"></div>\n\n`;
    }
    // 如果只有文字评语但没有图片，保留文字评语作为备选
    else if (question.comment) {
      stepsContent += `**教师评语**：${question.comment}\n\n`;
    }
  });

  // 更新报告数据
  reportData.value.steps = stepsContent;

  // 如果当前在报告预览页面，刷新视图
  if (activeTab.value === 'report' && reportGeneratorRef.value &&
      typeof reportGeneratorRef.value.updateReport === 'function') {
    nextTick(() => {
      reportGeneratorRef.value.updateReport();
    });
  }
};

// 成绩样式
const getScoreClass = (score) => {
  if (!score) return ''
  if (score >= 90) return 'score-excellent'
  if (score >= 80) return 'score-good'
  if (score >= 60) return 'score-pass'
  return 'score-fail'
}


// 获取提交详情
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

    // 1. 分割AI评语并赋值为每题初始评语
    splitAiRemarksToQuestions();

    // 2. 自动生成每题评语图片
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
    // ...existing code...
  } finally {
    loading.value = false
  }
}

// 加载提交历史 - 从真实API获取该学生的所有提交记录
const loadSubmissionHistory = async () => {
  try {
    const allData = await api.getAllStudentExperiments()
    const studentId = submission.value.studentId
    if (!allData || !studentId) {
      submissionHistory.value = []
      return
    }
    // 筛选该学生的所有提交，按时间排序
    const studentSubs = allData
      .filter(s => String(s.studentId) === String(studentId))
      .sort((a, b) => new Date(a.submitTime || a.date || 0) - new Date(b.submitTime || b.date || 0))

    submissionHistory.value = studentSubs.map((s, idx) => ({
      time: s.submitTime || s.date || '未知时间',
      type: s.status === 'completed' ? 'submit' : 'edit',
      title: s.experimentName || `实验${idx + 1}`,
      content: `得分: ${s.score || '未评分'} | 状态: ${s.status === 'completed' ? '已完成' : '进行中'}`,
      code: null
    }))
  } catch (error) {
    console.error('加载提交历史失败:', error)
    submissionHistory.value = []
  }
}

// 加载学习建议
const loadLearningRecommendations = async () => {
  try {
    // 基于学生真实提交数据生成学习建议
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
        title: '薄弱实验需要加强',
        content: `以下实验得分较低，建议重点复习：${lowScoreExps.map(s => s.experimentName + '(' + s.score + '分)').join('、')}`,
        resources: []
      })
    }
    if (avgScore < 80 && avgScore > 0) {
      recs.push({
        title: '提升整体成绩',
        content: `当前平均成绩为${Math.round(avgScore * 10) / 10}分，建议多做练习题巩固基础知识，争取将平均分提升到80分以上。`,
        resources: []
      })
    }
    const completed = studentSubs.filter(s => s.status === 'completed').length
    const total = studentSubs.length
    if (total > 0 && completed / total < 0.8) {
      recs.push({
        title: '提高实验完成率',
        content: `目前完成了${completed}/${total}个实验（${Math.round(completed / total * 100)}%），建议尽快完成未提交的实验。`,
        resources: []
      })
    }
    if (recs.length === 0) {
      recs.push({
        title: '表现优秀，继续保持',
        content: `该学生各项实验完成情况良好，平均成绩${Math.round(avgScore * 10) / 10}分，建议继续保持并挑战更高难度的题目。`,
        resources: []
      })
    }
    learningRecommendations.value = recs
  } catch (error) {
    console.error('加载学习建议失败:', error)
    learningRecommendations.value = []
  }
}

// 从真实数据计算学生表现
const loadStudentPerformance = async () => {
  try {
    const allData = await api.getAllStudentExperiments()
    const studentId = submission.value.studentId
    if (!allData || !studentId) return

    const studentSubs = allData.filter(s => String(s.studentId) === String(studentId))
    const scored = studentSubs.filter(s => s.score > 0)
    const completed = studentSubs.filter(s => s.status === 'completed')
    const total = studentSubs.length

    // 平均成绩
    studentPerformance.averageScore = scored.length > 0
      ? Math.round(scored.reduce((a, b) => a + b.score, 0) / scored.length * 10) / 10 : 0

    // 完成率
    studentPerformance.completionRate = total > 0 ? Math.round(completed.length / total * 100) : 0

    // 班级排名：计算所有学生的平均分并排序
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

    // 及时性评分（基于完成率，5分制）
    studentPerformance.punctuality = Math.min(5, Math.round(studentPerformance.completionRate / 20 * 10) / 10)

    // 代码质量评分（基于平均分，5分制）
    studentPerformance.codeQuality = Math.min(5, Math.round(studentPerformance.averageScore / 20 * 10) / 10)

    // 参与度（基于提交数量占总实验比例，5分制）
    studentPerformance.participation = Math.min(5, Math.round(studentSubs.length / Math.max(1, new Set(allData.map(s => s.experimentId)).size) * 5 * 10) / 10)

    // 更新图表
    updatePerformanceCharts(studentSubs, allData)
  } catch (error) {
    console.error('加载学生表现数据失败:', error)
  }
}

// 用真实数据更新图表
const updatePerformanceCharts = (studentSubs, allData) => {
  const scored = studentSubs.filter(s => s.score > 0).sort((a, b) => {
    const nameA = a.experimentName || ''
    const nameB = b.experimentName || ''
    return nameA.localeCompare(nameB, 'zh')
  })

  // 计算班级平均分
  const expAvgs = {}
  allData.filter(s => s.score > 0).forEach(s => {
    const name = s.experimentName || '未知'
    if (!expAvgs[name]) expAvgs[name] = []
    expAvgs[name].push(s.score)
  })

  if (scoreChartContainer.value && scoreChart) {
    const labels = scored.map(s => s.experimentName || '实验')
    const scores = scored.map(s => s.score)
    const classAvg = labels.map(name => {
      const arr = expAvgs[name]
      return arr ? Math.round(arr.reduce((a, b) => a + b, 0) / arr.length) : 0
    })
    scoreChart.setOption({
      xAxis: { data: labels },
      series: [
        { name: '成绩', data: scores },
        { name: '班级平均', data: classAvg }
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
          { value: completed, name: '已完成', itemStyle: { color: '#67C23A' } },
          { value: pending, name: '进行中', itemStyle: { color: '#E6A23C' } },
          { value: notSubmitted, name: '未提交', itemStyle: { color: '#F56C6C' } }
        ]
      }]
    })
  }
}

// 图表初始化
const initCharts = () => {

  // 成绩趋势图
  if (scoreChartContainer.value) {
    scoreChart = echarts.init(scoreChartContainer.value)
    const scoreOption = {
      tooltip: {
        trigger: 'axis'
      },
      xAxis: {
        type: 'category',
        data: ['实验1', '实验2', '实验3', '当前实验', '实验5']
      },
      yAxis: {
        type: 'value',
        name: '分数',
        min: 0,
        max: 100
      },
      series: [
        {
          name: '成绩',
          type: 'line',
          data: [82, 88, 75, submission.value.score || 0, null],
          markPoint: {
            data: [
              { type: 'max', name: '最高分' },
              { type: 'min', name: '最低分' }
            ]
          }
        },
        {
          name: '班级平均',
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

  // 完成情况图
  if (completionChartContainer.value) {
    completionChart = echarts.init(completionChartContainer.value)
    const completionOption = {

      tooltip: {
        trigger: 'item',
        formatter: '{a} <br/>{b}: {c} ({d}%)'
      },
      legend: {
        orient: 'vertical',
        left: 'left',
        data: ['按时完成', '逾期完成', '未完成']
      },
      series: [
        {
          name: '完成情况',
          type: 'pie',
          radius: '70%',
          center: ['50%', '60%'],
          data: [
            { value: 4, name: '按时完成', itemStyle: { color: '#67C23A' } },
            { value: 1, name: '逾期完成', itemStyle: { color: '#E6A23C' } },
            { value: 0, name: '未完成', itemStyle: { color: '#F56C6C' } }
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

// 返回提交列表
const goBack = () => {
  router.go(-1) // 返回上一页
}

// 打开评分对话框
const openGradeDialog = () => {
  gradeDialogVisible.value = true
}

// 提交评分
const submitGrade = async () => {
  try {
    // 调用API提交评分
    // await api.gradeSubmission(submissionId.value, gradeForm)

    // 更新本地数据
    submission.value = {
      ...submission.value,
      score: gradeForm.score,
      plagiarismRate: gradeForm.plagiarismRate,
      aiComment: gradeForm.aiComment,
      teacherComment: gradeForm.teacherComment,
      status: 'graded'
    }

    // 直接更新报告数据中的成绩
    if (reportData.value) {
      reportData.value.score = gradeForm.score;
      console.log('评分后更新报告数据:', reportData.value);
    } else {
      // 如果报告数据还没准备好，创建它
      prepareReportData();
      console.log('评分后初始化报告数据:', reportData.value);
    }

    // 更新报告中的评语内容
    updateReportWithComments();

    // 如果当前在报告预览页面，立即刷新报告视图
    if (activeTab.value === 'report' && reportGeneratorRef.value) {
      // 使用 nextTick 确保在DOM更新后执行
      nextTick(() => {
        console.log('尝试调用 updateReport 方法...');
        if (typeof reportGeneratorRef.value.updateReport === 'function') {
          console.log('调用 updateReport 方法成功');
          reportGeneratorRef.value.updateReport();
        } else {
          console.warn('ReportGenerator 组件缺少 updateReport 方法');
        }
      });
    }

    gradeDialogVisible.value = false
    ElMessage.success('评分成功')
  } catch (error) {
    console.error('评分失败:', error)
    ElMessage.error('评分失败，请稍后重试')
  }
}

// 拒绝提交
// const rejectSubmission = () => {
//   ElMessageBox.confirm('确定要拒绝此次提交吗？学生将需要重新提交。', '提示', {
//     confirmButtonText: '确定',
//     cancelButtonText: '取消',
//     type: 'warning'
//   }).then(async () => {
//     try {
//       // await api.rejectSubmission(submissionId.value)
//       submission.value.status = 'rejected'
//       ElMessage.success('已拒绝此次提交')
//     } catch (error) {
//       console.error('操作失败:', error)
//       ElMessage.error('操作失败，请稍后重试')
//     }
//   }).catch(() => { })
// }

// 生成AI评语
const generateAIComment = async () => {
  generatingComment.value = true
  try {
    // 这里应该调用API生成AI评语
    // const result = await api.generateAIComment(submissionId.value)

    // 模拟生成
    await new Promise(resolve => setTimeout(resolve, 1000))

    const aiComment = '代码实现了基本的链表功能，包括创建、插入和遍历操作。优点是结构清晰，函数命名规范；不足之处是缺少必要的错误处理，例如内存分配失败的情况没有处理。' +
        '建议优化内存管理，添加链表删除节点的功能，并完善错误处理机制。总体来说，这是一个良好的实现，展示了对链表基本概念的理解。'

    if (gradeDialogVisible.value) {
      gradeForm.aiComment = aiComment
    } else {
      submission.value.aiComment = aiComment
    }

    ElMessage.success('AI评语生成成功')
  } catch (error) {
    console.error('生成AI评语失败:', error)
    ElMessage.error('生成AI评语失败，请稍后重试')
  } finally {
    generatingComment.value = false
  }
}

// 修改AI评语
const editAIComment = () => {
  ElMessageBox.prompt('请修改AI评语', '修改评语', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputType: 'textarea',
    inputValue: submission.value.aiComment,
    inputPlaceholder: '请输入修改后的AI评语'
  }).then(({ value }) => {
    submission.value.aiComment = value
    ElMessage.success('AI评语已修改')
  }).catch(() => { })
}

// 重新生成AI评语
const regenerateAIComment = () => {
  ElMessageBox.confirm('确定要重新生成AI评语吗？这将覆盖当前的评语。', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    generateAIComment()
  }).catch(() => { })
}

// 修改教师评语
const editTeacherComment = () => {
  editingTeacherComment.value = submission.value.teacherComment || ''
  isEditingComment.value = true
}

// 保存教师评语
const saveTeacherComment = async () => {
  try {
    // 这里应该调用API保存评语
    // await api.saveTeacherComment(submissionId.value, editingTeacherComment.value)

    // 更新本地数据
    submission.value.teacherComment = editingTeacherComment.value

    isEditingComment.value = false
    ElMessage.success('评语保存成功')
  } catch (error) {
    console.error('保存评语失败:', error)
    ElMessage.error('保存评语失败，请稍后重试')
  }
}

// 取消编辑评语
const cancelEditComment = () => {
  isEditingComment.value = false
  editingTeacherComment.value = submission.value.teacherComment || ''
}

// 准备报告数据
const prepareReportData = () => {
  if (!submission.value) return

  console.log('准备报告数据，当前成绩:', submission.value.score) // 调试日志

  // 基础信息
  reportData.value = {
    experimentName: submission.value.experimentName || '数据结构实验',
    studentName: submission.value.studentName || '未知',
    studentId: submission.value.studentId || '未知学号',
    className: submission.value.class || '未知班级',
    courseName: '数据结构',
    // 确保成绩正确传递，处理可能的undefined或null值
    score: submission.value.score !== null && submission.value.score !== undefined
        ? Number(submission.value.score) : null,
    teacherName: '指导教师',
    labName: '计算机实验室',
    labTime: new Date().toLocaleDateString(),
  }

  //提取各章节内容（如果有报告的情况）
  if (submission.value.report) {
    try {
      const report = submission.value.report

      // 提取各章节内容
      const purposeMatch = report.match(/##?\s*实验目的[^\n]*\n+([\s\S]+?)(?=##)/i)
      if (purposeMatch) reportData.value.purpose = purposeMatch[1].trim()

      const requirementsMatch = report.match(/##?\s*实验环境[^\n]*\n+([\s\S]+?)(?=##)/i)
      if (requirementsMatch) reportData.value.requirements = requirementsMatch[1].trim()

      const tasksMatch = report.match(/##?\s*实验内容[^\n]*\n+([\s\S]+?)(?=##)/i) ||
          report.match(/##?\s*实验任务[^\n]*\n+([\s\S]+?)(?=##)/i)
      if (tasksMatch) reportData.value.tasks = tasksMatch[1].trim()

      // 不再从Markdown提取steps，而是通过题目评语生成

      const resultsMatch = report.match(/##?\s*实验结果[^\n]*\n+([\s\S]+?)(?=##)/i)
      if (resultsMatch) reportData.value.results = resultsMatch[1].trim()

      const summaryMatch = report.match(/##?\s*实验总结[^\n]*\n+([\s\S]+?)(?=$)/i) ||
          report.match(/##?\s*心得体会[^\n]*\n+([\s\S]+?)(?=$)/i)
      if (summaryMatch) reportData.value.summary = summaryMatch[1].trim()
    } catch (e) {
      console.error('解析报告内容失败:', e)
    }
  }

  // 根据解析的题目生成实验步骤内容
  if (parsedQuestions.value.length > 0) {
    updateReportWithComments()
  }

  console.log('报告数据准备完成，成绩值:', reportData.value.score) // 调试日志
}

// 处理报告数据更新
const handleReportDataUpdate = (newData) => {
  reportData.value = newData
}

// 运行代码
// const runCode = async () => {
//   try {
//     // 这里应该调用API运行代码
//     // const result = await api.runStudentCode(submissionId.value)

//     // 模拟运行
//     await new Promise(resolve => setTimeout(resolve, 1000))

//     codeResult.value = {
//       success: true,
//       output: "编译成功!\n运行结果:\n1 -> 2 -> 3 -> NULL\n程序执行时间: 0.002s"
//     }

//     ElMessage.success('代码运行成功')
//   } catch (error) {
//     console.error('代码运行失败:', error)
//     ElMessage.error('代码运行失败')

//     codeResult.value = {
//       success: false,
//       output: "编译错误:\nError: undefined reference to 'printLinkedList'\n代码编译失败，请检查函数声明和定义。"
//     }
//   }
// }

// 复制代码
const copyCode = () => {
  navigator.clipboard.writeText(submission.value.code)
      .then(() => {
        ElMessage.success('代码已复制到剪贴板')
      })
      .catch(() => {
        ElMessage.error('复制失败，请手动复制')
      })
}

// 下载代码
const downloadCode = () => {
  const blob = new Blob([submission.value.code], { type: 'text/plain' })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = `${submission.value.experimentName}_${submission.value.studentName}.c`
  link.click()
  URL.revokeObjectURL(link.href)
}

// // 格式化代码
// const formatCode = () => {
//   ElMessage.info('代码格式化功能开发中')
// }

// // 打印报告
// const printReport = () => {
//   window.print()
// }

// // 下载报告
// const downloadReport = () => {
//   if (!submission.value.report) {
//     ElMessage.warning('没有报告可下载')
//     return
//   }

//   const blob = new Blob([submission.value.report], { type: 'text/markdown' })
//   const link = document.createElement('a')
//   link.href = URL.createObjectURL(blob)
//   link.download = `${submission.value.experimentName}_${submission.value.studentName}_报告.md`
//   link.click()
//   URL.revokeObjectURL(link.href)
// }

// 下载 Word 文档
const downloadWordDoc = async () => {
  if (!reportData.value) {
    ElMessage.warning('没有报告数据可下载')
    return
  }

  try {
    // 创建一个新对象，避免引用问题
    const exportData = { ...reportData.value }

    // 确保成绩正确
    if (submission.value.score !== undefined && submission.value.score !== null) {
      exportData.score = String(submission.value.score)
    }

    console.log('下载Word文档时的成绩:', exportData.score)

    // 如果有教师评语，将其添加到报告数据中
    if (submission.value.teacherComment) {
      exportData.teacherComment = submission.value.teacherComment
    }

    // 确保评语已更新到steps
    updateReportWithComments()

    const docxGenerator = new DocxGenerator()
    const blob = await docxGenerator.generateStandardReport(exportData)

    // 下载文件名格式: 学号_姓名_实验名称.docx
    const fileName = `${submission.value.studentId}_${submission.value.studentName}_${submission.value.experimentName}.docx`
    DocxGenerator.downloadReport(blob, fileName)

    ElMessage.success('Word文档下载成功')
  } catch (error) {
    console.error('生成Word文档失败:', error)
    ElMessage.error('生成Word文档失败，请稍后重试')
  }
}

// 前端发送请求到服务器端
const downloadPDF = async () => {
  try {
    // 显示加载提示
    const loadingInstance = ElLoading.service({
      lock: true,
      text: 'PDF生成中，请稍候...',
      background: 'rgba(0, 0, 0, 0.7)'
    });

    // 创建一个新对象，避免引用问题
    const exportData = { ...reportData.value };

    // 直接从submission中获取成绩，确保拿到最新值，并转成字符串
    if (submission.value.score !== undefined && submission.value.score !== null) {
      exportData.score = String(submission.value.score);
    }

    console.log('下载PDF文档时的成绩:', exportData.score);

    // 如果有教师评语，将其添加到报告数据中
    if (submission.value.teacherComment) {
      exportData.teacherComment = submission.value.teacherComment;
    }

    // 先生成 Word 文档
    const docxGenerator = new DocxGenerator();
    const wordBlob = await docxGenerator.generateStandardReport(exportData);

    // 发送 Word 文档到服务器进行转换
    const formData = new FormData();
    formData.append('wordFile', new Blob([wordBlob]), 'report.docx');

    const response = await axios.post('/api/api/convert-to-pdf', formData, {
      responseType: 'blob', // 重要：指定响应类型为blob
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    });

    // 关闭加载提示
    loadingInstance.close();

    // 下载返回的PDF
    const fileName = `${submission.value.studentId}_${submission.value.studentName}_${submission.value.experimentName}.pdf`;
    const url = window.URL.createObjectURL(new Blob([response.data]));
    const link = document.createElement('a');
    link.href = url;
    link.download = fileName;
    link.click();

    ElMessage.success('PDF文档下载成功');
  } catch (error) {
    console.error('生成PDF文档失败:', error);
    ElMessage.error('生成PDF文档失败，请稍后重试');

    // 关闭可能存在的加载提示
    const loadingInstance = ElLoading.service();
    loadingInstance.close();
  }
}

// 窗口大小变化时重绘图表
const handleResize = () => {
  scoreChart?.resize()
  completionChart?.resize()
}

onMounted(() => {
  loadSubmissionDetail()
  window.addEventListener('resize', handleResize)

  // 添加一个初始化标记，用于追踪组件是否已初始化
  let reportComponentInitialized = false;

  // 监听标签页变化，当切换到报告标签页时加载报告数据
  watch(() => activeTab.value, (newTab) => {
    if (newTab === 'report' && submission.value) {
      // 强制重新准备报告数据，确保包含最新成绩和评语
      prepareReportData();

      // 确保ReportGenerator组件更新
      nextTick(() => {
        if (reportGeneratorRef.value && typeof reportGeneratorRef.value.updateReport === 'function') {
          console.log('切换到报告标签页，更新报告，当前成绩:', reportData.value.score);
          reportGeneratorRef.value.updateReport();
          reportComponentInitialized = true;
        } else {
          console.warn('ReportGenerator组件缺少updateReport方法或组件未挂载');
          // 组件未就绪，设置延迟重试
          setTimeout(() => {
            if (reportGeneratorRef.value && typeof reportGeneratorRef.value.updateReport === 'function') {
              reportGeneratorRef.value.updateReport();
              reportComponentInitialized = true;
            }
          }, 500);
        }
      });
    }
  }, { immediate: true }); // 添加immediate:true确保初始加载时也执行

  // 监听成绩变化，确保报告数据同步更新
  watch(() => submission.value.score, (newScore) => {
    if (reportData.value) {
      console.log('成绩已变更为:', newScore);
      reportData.value.score = newScore;
      // 如果当前在报告预览页面，刷新报告视图
      if (activeTab.value === 'report' && reportGeneratorRef.value) {
        nextTick(() => {
          if (typeof reportGeneratorRef.value.updateReport === 'function') {
            reportGeneratorRef.value.updateReport();
          }
        });
      }
    }
  });

  // 监听 reportGeneratorRef 以处理组件后期挂载的情况
  watch(() => reportGeneratorRef.value, (newRef) => {
    if (newRef && !reportComponentInitialized && activeTab.value === 'report' && reportData.value) {
      console.log('ReportGenerator组件已挂载，初始化报告数据');
      nextTick(() => {
        if (typeof newRef.updateReport === 'function') {
          newRef.updateReport();
          reportComponentInitialized = true;
        }
      });
    }
  });

  // 监听题目评语变化，同步更新报告内容
  watch(() => parsedQuestions.value, () => {
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

/* 题目选项卡样式 */
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

/* 评分样式 */
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

/* 教师评语编辑区域样式 */
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

/* 左侧区域样式优化 */
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

/* 右侧区域样式优化 */
.content-card {
  height: 80vh;
  margin-bottom: 50px;
}

.main-tabs {
  height: 70vh;

}

/* 修改标签页内容区域样式 */
.main-tabs :deep(.el-tabs__content) {
  height: calc(100% - 55px);
  position: relative;
  /* 添加相对定位 */
}

/* 为每个标签页面板添加滚动功能 */
.main-tabs :deep(.el-tab-pane) {
  height: 100%;
  overflow-y: auto;
}

.code-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

/* 修改报告容器样式，确保可以滚动 */
.report-container {
  padding-bottom: 100px;
  max-width: 95%;
  margin: 0 auto;
  font-size: 0.95em;
  overflow-y: auto;
  /* 添加垂直滚动条 */
  max-height: 70vh;
  /* 限制最大高度，确保需要滚动 */
}

/* 确保报告生成器组件可以滚动 */
.report-container :deep(.report-generator) {
  width: 100%;
  max-width: 100%;
  overflow-y: auto;
  /* 改为visible，让滚动由父容器处理 */
}


/* 美化按钮样式 */
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

/* 美化卡片样式 */
.el-card {
  transition: all 0.3s ease;
  border-radius: 8px;
  overflow: hidden;
}

/* 打印样式优化 */
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
  /* 添加垂直滚动条 */
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

/* 添加评语相关样式 */
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