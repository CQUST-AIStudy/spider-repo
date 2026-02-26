// 更新分析数据函数
export const updateAnalysisData = (analysisData, experimentStore) => {
  if (!analysisData || !analysisData.value || !analysisData.value.overall) {
    console.warn('分析数据不存在，无法更新');
    return;
  }
  
  // 获取实验列表
  const allExperiments = experimentStore.experimentList || [];
  
  if (!allExperiments || allExperiments.length === 0) {
    console.warn('实验列表为空，无法更新分析数据');
    return;
  }
  
  // 完成的实验
  const completedExperiments = allExperiments.filter(exp => exp.status === 'completed');
  
  // 计算平均成绩 - 只考虑已完成的实验
  let avgScore = 0;
  if (completedExperiments.length > 0) {
    const scoreSum = completedExperiments.reduce((sum, exp) => {
      return sum + (exp.score || 0);
    }, 0);
    avgScore = Math.round(scoreSum / completedExperiments.length);
  }
  
  // 计算完成率 - 已完成实验数 / 总实验数
  const completionRate = allExperiments.length > 0 
    ? Math.round((completedExperiments.length / allExperiments.length) * 100) 
    : 0;
  
  console.log(`实时计算分析数据 - 平均成绩: ${avgScore}, 实验完成率: ${completionRate}%`);
  
  // 更新分析数据
  analysisData.value.overall.averageScore = avgScore;
  analysisData.value.overall.completionRate = completionRate;
};
