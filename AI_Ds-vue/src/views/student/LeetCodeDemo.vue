<template>
  <div class="leetcode-demo">
    <page-header title="LeetCode功能演示" description="测试代码提交和AI批改功能" />
    
    <el-card class="demo-card">
      <h3>🧪 功能测试</h3>
      
      <el-space direction="vertical" size="large" style="width: 100%">
        <el-button @click="testAIFeedback" type="primary" size="large">
          测试AI批改反馈
        </el-button>
        
        <el-button @click="testSubmitCode" type="success" size="large" :loading="testing">
          测试代码提交
        </el-button>
      </el-space>
    </el-card>

    <!-- AI反馈演示 -->
    <el-card v-if="showDemo" class="feedback-demo">
      <h3>🤖 AI批改反馈演示</h3>
      
      <div class="demo-result">
        <div class="result-header">
          <div class="status accepted">
            <el-icon><Check /></el-icon>
            通过
          </div>
          <div class="score">
            得分: 85/100
          </div>
        </div>

        <!-- AI评测结果 -->
        <div class="ai-feedback">
          <h4>AI 评测反馈</h4>
          <div class="feedback-content" v-html="renderedFeedback"></div>
        </div>

        <!-- 执行详情 -->
        <div class="execution-details">
          <el-descriptions title="执行详情" :column="2" border>
            <el-descriptions-item label="执行时间">120ms</el-descriptions-item>
            <el-descriptions-item label="内存消耗">N/A</el-descriptions-item>
            <el-descriptions-item label="通过用例">3 / 3</el-descriptions-item>
          </el-descriptions>
        </div>

        <!-- 技能提升建议 -->
        <div class="skill-suggestions">
          <h4>技能提升建议</h4>
          <el-tag
            v-for="suggestion in skillSuggestions"
            :key="suggestion"
            class="suggestion-tag"
            type="info"
          >
            {{ suggestion }}
          </el-tag>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Check } from '@element-plus/icons-vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import PageHeader from '@/components/PageHeader.vue'
import { testAIFeedback as getTestFeedback } from '@/utils/testLeetCode'

const showDemo = ref(false)
const testing = ref(false)

const mockFeedback = `## 🤖 AI代码评测报告

### 🎉 恭喜通过！
你的解答**完全正确**，所有测试用例都通过了！

### 📊 代码质量分析
- ✅ **正确性**: 完美 (3/3 测试用例通过)
- 🚀 **执行效率**: 良好 (平均 120ms)
- 📝 **代码风格**: 良好 ⭐⭐
- 🧠 **算法复杂度**: O(n²) - 嵌套循环

### 💡 个性化建议
🌟 **进阶挑战**:
- 尝试优化算法的时间复杂度
- 考虑使用HashMap来实现O(n)时间复杂度
- 添加注释提高代码可读性

### 📚 推荐学习
继续挑战相关题目:
- 尝试同类型的中等难度题目
- 学习更高效的算法和数据结构`

const skillSuggestions = ref(['数组操作', '算法优化', '哈希表应用'])

const renderedFeedback = computed(() => {
  return DOMPurify.sanitize(marked(mockFeedback))
})

function testAIFeedback() {
  showDemo.value = true
  ElMessage.success('AI批改反馈演示已显示')
  
  // 调用测试工具
  const result = getTestFeedback()
  console.log('测试结果:', result)
}

async function testSubmitCode() {
  testing.value = true
  
  try {
    // 模拟提交过程
    ElMessage.info('正在提交代码...')
    
    await new Promise(resolve => setTimeout(resolve, 2000))
    
    // 模拟AI批改过程
    ElMessage.info('AI正在批改中...')
    
    await new Promise(resolve => setTimeout(resolve, 3000))
    
    // 显示结果
    showDemo.value = true
    ElMessage.success('代码提交成功，AI批改完成！')
    
  } catch (error) {
    ElMessage.error('测试失败: ' + error.message)
  } finally {
    testing.value = false
  }
}
</script>

<style scoped>
.leetcode-demo {
  padding: 20px;
}

.demo-card {
  margin-bottom: 20px;
}

.demo-card h3 {
  margin-bottom: 20px;
  color: #333;
}

.feedback-demo {
  margin-top: 20px;
}

.feedback-demo h3 {
  margin-bottom: 20px;
  color: #333;
}

.demo-result {
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

.score {
  font-size: 16px;
  font-weight: bold;
  color: #409eff;
}

.ai-feedback {
  margin: 20px 0;
}

.ai-feedback h4 {
  margin-bottom: 12px;
  color: #333;
}

.feedback-content {
  background: #f8f9fa;
  padding: 16px;
  border-radius: 8px;
  border-left: 4px solid #409eff;
}

.feedback-content :deep(h2) {
  color: #409eff;
  margin-top: 0;
}

.feedback-content :deep(h3) {
  color: #333;
  margin: 16px 0 8px 0;
}

.feedback-content :deep(ul) {
  margin: 8px 0;
  padding-left: 20px;
}

.feedback-content :deep(li) {
  margin: 4px 0;
}

.execution-details {
  margin: 20px 0;
}

.skill-suggestions {
  margin: 20px 0;
}

.skill-suggestions h4 {
  margin-bottom: 12px;
  color: #333;
}

.suggestion-tag {
  margin: 4px 8px 4px 0;
}
</style>