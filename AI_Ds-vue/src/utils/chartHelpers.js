/**
 * 安全地初始化echarts，包含错误处理和DOM检查
 * @param {Array} chartRefs - 包含图表容器refs的数组
 * @param {Function} initFunction - 图表初始化函数
 * @param {Number} delay - 可选的延迟时间
 */
export function safeInitCharts(chartRefs, initFunction, delay = 300) {
  if (!Array.isArray(chartRefs)) {
    console.error('chartRefs必须是数组')
    return Promise.resolve(false)
  }

  return new Promise((resolve) => {
    // 确保所有chart容器都存在并且都已经渲染出来
    const allRefsValid = chartRefs.every(ref => ref && ref.offsetWidth > 0 && ref.offsetHeight > 0)
    
    if (!allRefsValid) {
      if (process.env.NODE_ENV === 'development') {
        console.log('容器尚未完全渲染，增加延迟等待...', delay)
      }
      delay += 100 // 增加延迟时间
      
      if (delay > 2000) { // 设置最大延迟时间，避免无限等待
        console.error('等待DOM渲染超时，尝试继续初始化')
        setTimeout(() => {
          try {
            initFunction()
            resolve(true)
          } catch (error) {
            console.error('图表初始化失败:', error)
            resolve(false)
          }
        }, 100)
        return
      }
      
      // 递归调用，增加延迟
      setTimeout(() => {
        safeInitCharts(chartRefs, initFunction, delay).then(resolve)
      }, 100)
      return
    }
    
    // 所有容器都已准备好，执行初始化
    setTimeout(() => {
      try {
        initFunction()
        resolve(true)
      } catch (error) {
        console.error('图表初始化失败:', error)
        resolve(false)
      }
    }, delay)
  })
}
/**
 * 创建基本柱状图配置
 * @param {String} title - 图表标题
 * @param {Array} categories - X轴类别
 * @param {Array} data - 图表数据
 * @param {String} color - 图表颜色
 * @returns {Object} 图表配置
 */
export function createBarChartOptions(title, categories, data, color = '#409EFF') {
  return {
    title: {
      text: title,
      left: 'center'
    },
    tooltip: {
      trigger: 'axis'
    },
    xAxis: {
      type: 'category',
      data: categories
    },
    yAxis: {
      type: 'value'
    },
    series: [
      {
        data,
        type: 'bar',
        itemStyle: {
          color
        }
      }
    ]
  }
}

/**
 * 创建基本饼图配置
 * @param {String} title - 图表标题
 * @param {Array} data - 图表数据 [{name, value}]
 * @returns {Object} 图表配置
 */
export function createPieChartOptions(title, data) {
  return {
    title: {
      text: title,
      left: 'center'
    },
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} ({d}%)'
    },
    legend: {
      orient: 'vertical',
      left: 'left'
    },
    series: [
      {
        type: 'pie',
        radius: '60%',
        data,
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
} 