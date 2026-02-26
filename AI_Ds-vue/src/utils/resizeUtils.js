/**
 * 调整图表大小
 * @param {Array} charts - 图表实例数组
 */
export function resizeCharts(charts) {
  if (!charts || !Array.isArray(charts)) {
    console.error('charts参数必须是数组')
    return
  }
  
  // 移除可能存在的旧事件监听器
  window.removeEventListener('resize', handleResize)
  
  function handleResize() {
    charts.forEach(chart => {
      if (chart) {
        try {
          chart.resize()
        } catch (error) {
          console.error('调整图表大小失败:', error)
        }
      }
    })
  }
  
  window.addEventListener('resize', handleResize)
  
  // 返回清理函数，用于在组件卸载时移除事件监听
  return () => {
    window.removeEventListener('resize', handleResize)
  }
}

/**
 * 使用防抖的图表大小调整 
 * @param {Array} charts - 图表实例数组
 * @param {Number} delay - 防抖延迟时间，默认200ms
 */
export function resizeChartsWithDebounce(charts, delay = 200) {
  if (!charts || !Array.isArray(charts)) {
    console.error('charts参数必须是数组')
    return
  }
  
  let timer = null
  
  // 移除可能存在的旧事件监听器
  window.removeEventListener('resize', debouncedResize)
  
  function debouncedResize() {
    if (timer) {
      clearTimeout(timer)
    }
    
    timer = setTimeout(() => {
      charts.forEach(chart => {
        if (chart) {
          try {
            chart.resize()
          } catch (error) {
            console.error('调整图表大小失败:', error)
          }
        }
      })
    }, delay)
  }
  
  window.addEventListener('resize', debouncedResize)
  
  // 返回清理函数，用于在组件卸载时移除事件监听
  return () => {
    if (timer) {
      clearTimeout(timer)
    }
    window.removeEventListener('resize', debouncedResize)
  }
} 