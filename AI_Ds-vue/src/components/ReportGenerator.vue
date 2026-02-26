<template>
  <div class="report-generator">
    <el-card class="report-card">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <span>报告{{ isEditMode ? '编辑' : '预览' }}</span>
            <el-button v-if="isEditMode" type="success" @click="saveEdits" style="margin-left: 15px;">
              <el-icon>
                <Check />
              </el-icon>保存修改
            </el-button>
          </div>
          <div class="header-right">
            <el-button v-if="!isEditMode" type="primary" @click="toggleEditMode">
              <el-icon>
                <Edit />
              </el-icon>编辑报告
            </el-button>
            <el-button v-if="isEditMode" type="info" @click="cancelEdits">
              <el-icon>
                <Close />
              </el-icon>取消编辑
            </el-button>
            <!-- <el-button type="primary" @click="generateWordDoc" :disabled="isEditMode">
              <el-icon><Download /></el-icon>下载Word文档
            </el-button> -->
          </div>
        </div>
      </template>

      <!-- 编辑模式 -->
      <div v-if="isEditMode" class="edit-mode">
        <el-form :model="editingData" label-position="top">
          <!-- 基本信息 -->
          <el-divider content-position="left">基本信息</el-divider>

          <el-row :gutter="20">
            <el-col :span="8">
              <el-form-item label="课程名称">
                <el-input v-model="editingData.courseName" placeholder="请输入课程名称" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="实验项目">
                <el-input v-model="editingData.experimentName" placeholder="请输入实验项目" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="机房名称">
                <el-input v-model="editingData.labName" placeholder="请输入机房名称" />
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="20">
            <el-col :span="8">
              <el-form-item label="上机时间">
                <el-date-picker v-model="editingData.labTime" type="date" placeholder="选择日期" format="YYYY/MM/DD"
                  value-format="YYYY/MM/DD" style="width: 100%;" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="指导老师">
                <el-input v-model="editingData.teacherName" placeholder="请输入指导老师姓名" />
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="20">
            <el-col :span="8">
              <el-form-item label="学生姓名">
                <el-input v-model="editingData.studentName" placeholder="请输入学生姓名" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="学号">
                <el-input v-model="editingData.studentId" placeholder="请输入学号" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="专业班级">
                <el-input v-model="editingData.className" placeholder="请输入专业班级" />
              </el-form-item>
            </el-col>
          </el-row>

          <!-- 报告内容 -->
          <el-divider content-position="left">报告内容</el-divider>

          <el-form-item label="一、上机操作目的和要求">
            <el-input v-model="editingData.purpose" type="textarea" :rows="4" placeholder="请输入操作目的和要求" />
          </el-form-item>

          <el-form-item label="二、上机操作需要的软、硬件">
            <el-input v-model="editingData.requirements" type="textarea" :rows="4" placeholder="请输入所需软硬件" />
          </el-form-item>

          <el-form-item label="三、上机操作内容（老师布置的具体任务）">
            <el-input v-model="editingData.tasks" type="textarea" :rows="6" placeholder="请输入操作内容，每行一个任务" />
          </el-form-item>

          <el-form-item label="四、上机操作的基本步骤(每个题目的关键代码及注释)">
            <el-input v-model="editingData.steps" type="textarea" :rows="10" placeholder="请输入操作步骤和代码" />
          </el-form-item>

          <el-form-item label="五、上机操作的结果截图及还存在的问题">
            <el-input v-model="editingData.results" type="textarea" :rows="6" placeholder="请输入操作结果和存在问题" />
          </el-form-item>

          <el-form-item label="六、上机操作的收获及心得">
            <el-input v-model="editingData.summary" type="textarea" :rows="8" placeholder="请输入收获和心得" />
          </el-form-item>
        </el-form>
      </div>

      <!-- 预览模式 -->
      <div v-else class="report-preview">
        <!-- 报告容器 -->
        <div class="report-container">
          <!-- 标题部分 - 与Word文档一致，拆分为两部分 -->
          <div class="report-title">
            <div class="university-name">……大学</div>
            <div class="report-type">上机实验报告（上机操作类）</div>
          </div>

          <!-- 基本信息表格 - 通过CSS实现加粗边框 -->
          <table class="info-table">
            <!-- 第一行：课程名称（1+2列）| 实验项目（1+2列） -->
            <tr class="info-row">
              <td class="label-cell">课程名称</td>
              <td class="value-cell" colspan="2">{{ experimentData.courseName || "数据结构" }}</td>
              <td class="label-cell">实验项目</td>
              <td class="value-cell" colspan="2">{{ experimentData.experimentName || "计科23数据结构例题" }}</td>
            </tr>
            <!-- 第二行：机房名称（1+2列）| 上机时间（1+2列） -->
            <tr class="info-row">
              <td class="label-cell">机房名称</td>
              <td class="value-cell" colspan="2">{{ experimentData.labName || "I301" }}</td>
              <td class="label-cell">上机时间</td>
              <td class="value-cell" colspan="2">{{ experimentData.labTime || new Date().toLocaleDateString() }}</td>
            </tr>
            <!-- 第三行：指导老师（1+2列）| 上机成绩（1+2列） -->
            <tr class="info-row">
              <td class="label-cell">指导老师</td>
              <td class="value-cell" colspan="2">{{ experimentData.teacherName || "张老师" }}</td>
              <td class="label-cell">上机成绩</td>
              <td class="value-cell" colspan="2">{{ experimentData.score !== null && experimentData.score !== undefined
                ?
                experimentData.score : "" }}</td>
            </tr>
            <!-- 第四行：学生姓名（1+1列）| 学号（1+1列）| 专业班级（1+1列） -->
            <tr class="info-row">
              <td class="label-cell">学生姓名</td>
              <td class="value-cell">{{ experimentData.studentName || "易星贵" }}</td>
              <td class="label-cell">学号</td>
              <td class="value-cell">{{ experimentData.studentId || "2019443672" }}</td>
              <td class="label-cell">专业班级</td>
              <td class="value-cell">{{ experimentData.className || "计算机科学1班" }}</td>
            </tr>
          </table>

          <!-- 报告内容部分 - 每个部分都有独立的边框 -->
          <div class="report-content-sections">
            <!-- 一、上机操作目的和要求 -->
            <div class="content-section">
              <div class="section-title">一、上机操作目的和要求</div>
              <div class="section-content">{{ experimentData.purpose || "实现顺序表的基本操作\n实现链表的基本操作\n完成示例应用程序\n撰写实验报告分析性能" }}</div>
            </div>

            <!-- 二、上机操作需要的软、硬件 -->
            <div class="content-section">
              <div class="section-title">二、上机操作需要的软、硬件</div>
              <div class="section-content white-space-pre">{{ experimentData.requirements || "Windows11,Visual Studio 2022" }}</div>
            </div>

            <!-- 三、上机操作内容（老师布置的具体任务） -->
            <div class="content-section">
              <div class="section-title">三、上机操作内容（老师布置的具体任务）</div>
              <div class="section-content white-space-pre">{{ experimentData.tasks || "实验内容：线性表基础操作，包括顺序表的初始化、插入、删除、查找和遍历实现" }}</div>
            </div>

            <!-- 四、上机操作的基本步骤(每个题目的关键代码及注释) -->
            <div class="content-section">
              <div class="section-title">四、上机操作的基本步骤(每个题目的关键代码及注释)</div>
              <div class="section-content code-content">
                <div v-html="processContent(experimentData.steps)" class="markdown-content"></div>
              </div>
            </div>

            <!-- 五、上机操作的结果截图及还存在的问题 -->
            <div class="content-section">
              <div class="section-title">五、上机操作的结果截图及还存在的问题</div>
              <div class="section-content white-space-pre">{{ experimentData.results || "成功实现了线性表的各项功能，测试通过。" }}</div>
            </div>

            <!-- 六、上机操作的收获及心得 -->
            <div class="content-section">
              <div class="section-title">六、上机操作的收获及心得</div>
              <div class="section-content white-space-pre">{{ experimentData.summary || "通过本次实验，我深入理解了线性表的工作原理和实现方法。" }}</div>
            </div>
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { Download, Edit, Check, Close } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { marked } from 'marked'
import DOMPurify from 'dompurify'

// 接收父组件传递的数据
const props = defineProps({
  reportData: {
    type: Object,
    default: () => ({})
  }
})

// 定义要向父组件发送的事件
const emit = defineEmits(['update:reportData', 'report-saved'])

// 实验数据
const experimentData = ref({})
// 编辑模式下的数据副本
const editingData = ref({})
// 编辑模式标志
const isEditMode = ref(false)

// 处理Markdown和评语图片的方法
const processContent = (content) => {
  if (!content) return '';

  // 处理评语图片标记
  const processedContent = content.replace(
    /<div class="comment-image-container" data-image="(.*?)"><\/div>/g,
    (match, imageDataUrl) => {
      return `<div class="teacher-comment-image"><img src="${imageDataUrl}" alt="教师评语:" /></div>`;
    }
  );

  // 使用marked处理Markdown内容
  const html = marked.parse(processedContent);
  return DOMPurify.sanitize(html);
};

// 侦听父组件传来的数据变化
watch(() => props.reportData, (newData) => {
  if (newData && Object.keys(newData).length > 0) {
    experimentData.value = { ...newData }
    console.log('expeimentData数据:', experimentData.value)
  }
}, { deep: true, immediate: true })

// 供父组件调用，手动更新报告数据
const updateReport = () => {
  console.log('updateReport被调用, 成绩:', props.reportData?.score)
  if (props.reportData && Object.keys(props.reportData).length > 0) {
    experimentData.value = { ...props.reportData }
  }
}



// 暴露方法给父组件调用
defineExpose({
  updateReport
})

// 切换到编辑模式
const toggleEditMode = () => {
  // 创建一个深拷贝，避免直接修改原始数据
  editingData.value = JSON.parse(JSON.stringify(experimentData.value))
  isEditMode.value = true
}




// 保存编辑
const saveEdits = () => {
  try {
    // 更新主数据对象
    experimentData.value = JSON.parse(JSON.stringify(editingData.value))

    // 向父组件发送更新事件
    emit('update:reportData', experimentData.value)
    emit('report-saved', experimentData.value)

    // 关闭编辑模式
    isEditMode.value = false

    ElMessage.success('报告数据保存成功！')
  } catch (error) {
    console.error('保存数据出错:', error)
    ElMessage.error('保存数据失败，请重试')
  }
}

// 取消编辑
const cancelEdits = () => {
  ElMessageBox.confirm('确定取消编辑？未保存的修改将会丢失', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '返回编辑',
    type: 'warning',
  })
    .then(() => {
      isEditMode.value = false
      ElMessage.info('已取消编辑')
    })
    .catch(() => {
      // 用户点击了取消，继续编辑
    })
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

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-left,
.header-right {
  display: flex;
  align-items: center;
}

.edit-mode {
  padding: 20px;
}

.report-preview {
  padding: 20px;
  background-color: #fff;
}

.report-container {
  width: 100%;
  margin: 0 auto;
  padding: 20px;
  background-color: #fff;
}

.report-title {
  text-align: center;
  margin-bottom: 30px;
}

.university-name {
  font-family: '黑体', sans-serif;
  font-size: 32px;
  font-weight: bold;
  margin-bottom: 10px;
}

.report-type {
  font-family: '黑体', sans-serif;
  font-size: 32px;
  font-weight: bold;
  margin-top: 10px;
}

/* 信息表格样式 */
.info-table {
  width: 100%;
  border-collapse: collapse;
  /* 外部边框加粗 */
  border: 2px solid #000;
}

.info-row {
  height: 50px;
  /* 固定行高 */
}

.label-cell {
  width: 10%;
  font-family: '宋体', serif;
  font-size: 16px;
  font-weight: normal;
  padding: 12px 8px;
  text-align: center;
  vertical-align: middle;
  border: 1px solid #000;
}

.value-cell {
  font-family: '宋体', serif;
  font-size: 16px;
  padding: 12px 8px;
  text-align: center;
  vertical-align: middle;
  border: 1px solid #000;
}

/* 报告内容部分样式 */
.report-content-sections {
  width: 100%;
}

/* 每个内容部分独立的边框 */
.content-section {
  width: 100%;
  border-left: 2px solid #000;
  /* 外部边框加粗 */
  border-right: 2px solid #000;
  border-bottom: 1px solid #000;
  padding: 20px 30px;
  box-sizing: border-box;
}



.content-section:last-child {
  border-bottom: 2px solid #000;
}

/* 区块标题 */
.section-title {
  font-family: '宋体', serif;
  font-size: 16px;
  font-weight: normal;
  margin-bottom: 15px;
  line-height: 1.5;
}

/* 区块内容 */
.section-content {
  font-family: '宋体', serif;
  font-size: 16px;
  line-height: 1.5;
  margin-bottom: 15px;
}

.white-space-pre {
  white-space: pre-line;
}

/* 代码块样式 */
.code-content {
  background-color: #f9f9f9;
  border-radius: 4px;
  padding: 10px;
  margin-top: 10px;
}

.code-block {
  white-space: pre-wrap;
  font-family: 'Courier New', Courier, monospace;
  line-height: 1.5;
  font-size: 14px;
  text-indent: 0;
  /* 代码不需要首行缩进 */
}

@media print {
  .report-container {
    box-shadow: none;
    padding: 0;
  }

  .report-card {
    box-shadow: none;
    border: none;
  }

  .card-header {
    display: none;
  }
}

/* 教师评语图片样式 */
.teacher-comment-image {
  margin: 15px 0;
  width: 100%;
  display: block;
}

.teacher-comment-image img {
   max-width: 100%;
  border-radius: 4px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
  /* 添加边框样式 */
  border: 1px solid #dcdfe6;
  padding: 10px;
  background-color: #f8f9fa;
}

/* 确保代码块样式应用于v-html内容 */
.code-block {
  white-space: pre-wrap;
  font-family: 'Courier New', Courier, monospace;
  line-height: 1.5;
  font-size: 14px;
  text-indent: 0;
}

/* 代码高亮样式 */
.code-block pre {
  background-color: #f5f5f5;
  padding: 10px;
  border-radius: 4px;
  margin: 10px 0;
}

.code-block code {
  white-space: pre-wrap;
}

/* Markdown样式 */
.code-block h3 {
  font-size: 18px;
  font-weight: bold;
  margin-top: 15px;
  margin-bottom: 10px;
}

.code-block p {
  margin: 10px 0;
}

.teacher-comment-image {
  margin: 15px 0;
  width: 100%;
  max-width: 500px; /* 限制最大宽度 */
  display: block;
}

.teacher-comment-image img {
  width: 100%; /* 确保图片宽度100%适应容器 */
  max-width: 100%;
  border-radius: 4px;
  border: 1px solid #dcdfe6;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
}
</style>
