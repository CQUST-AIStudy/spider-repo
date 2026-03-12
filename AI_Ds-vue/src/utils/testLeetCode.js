// LeetCode功能测试工具
import api from '@/api'

/**
 * 测试LeetCode功能
 */
export async function testLeetCodeFeatures() {
  console.log('🧪 开始测试LeetCode功能...')
  
  try {
    // 1. 测试获取题目列表
    console.log('📋 测试获取题目列表...')
    const problemsResponse = await api.get('/api/leetcode/test/problems')
    console.log('题目列表:', problemsResponse)
    
    // 2. 测试获取单个题目
    if (problemsResponse.data && problemsResponse.data.length > 0) {
      const firstProblem = problemsResponse.data[0]
      console.log('📖 测试获取题目详情...')
      const problemResponse = await api.getLeetCodeProblem(firstProblem.id)
      console.log('题目详情:', problemResponse)
      
      // 3. 测试运行代码
      console.log('▶️ 测试运行代码...')
      const runResponse = await api.runLeetCodeSolution({
        problemId: firstProblem.id,
        code: 'class Solution {\n    public int[] twoSum(int[] nums, int target) {\n        return new int[]{0, 1};\n    }\n}',
        language: 'java',
        testInput: '[2,7,11,15]\n9'
      })
      console.log('运行结果:', runResponse)
      
      // 4. 测试提交代码
      console.log('📤 测试提交代码...')
      const submitResponse = await api.submitLeetCodeSolution({
        problemId: firstProblem.id,
        code: 'class Solution {\n    public int[] twoSum(int[] nums, int target) {\n        for (int i = 0; i < nums.length; i++) {\n            for (int j = i + 1; j < nums.length; j++) {\n                if (nums[i] + nums[j] == target) {\n                    return new int[]{i, j};\n                }\n            }\n        }\n        return new int[]{};\n    }\n}',
        language: 'java'
      })
      console.log('提交结果:', submitResponse)
      
      if (submitResponse.data && submitResponse.data.aiFeedback) {
        console.log('🤖 AI批改反馈:', submitResponse.data.aiFeedback)
      }
    }
    
    console.log('✅ LeetCode功能测试完成!')
    return true
    
  } catch (error) {
    console.error('❌ LeetCode功能测试失败:', error)
    return false
  }
}

/**
 * 测试AI批改功能
 */
export function testAIFeedback() {
  const mockSubmitResult = {
    accepted: true,
    score: 85,
    aiFeedback: `## 🤖 AI代码评测报告

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
- 学习更高效的算法和数据结构`,
    details: {
      passedCases: 3,
      totalCases: 3,
      runtime: '120ms',
      memory: 'N/A'
    },
    skillSuggestions: ['数组操作', '算法优化', '哈希表应用']
  }
  
  console.log('🤖 AI批改反馈示例:', mockSubmitResult)
  return mockSubmitResult
}
