import * as echarts from 'echarts/core'
import { BarChart, PieChart, LineChart, RadarChart } from 'echarts/charts'
import {
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent,
  DatasetComponent
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

// 注册所有需要的echarts组件
echarts.use([
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent,
  DatasetComponent,
  BarChart,
  PieChart,
  LineChart,
  RadarChart,
  CanvasRenderer
])

/**
 * 初始化图表
 * @param {HTMLElement} container - 图表容器DOM元素
 * @param {Object} options - 图表配置选项
 * @param {Object} existingChart - 已有的图表实例，如果有的话
 * @returns {Object} 图表实例
 */
export function initChart(container, options, existingChart = null) {
  if (!container) {
    console.error('图表容器未找到')
    return null
  }

  // 检查容器是否有宽高
  if (container.offsetHeight === 0 || container.offsetWidth === 0) {
    console.error('图表容器宽高为0，无法渲染图表')
    return null
  }

  try {
    // 如果已有图表实例，先销毁
    if (existingChart) {
      existingChart.dispose()
    }

    // 创建新图表实例
    const chart = echarts.init(container)
    
    // 设置配置项
    chart.setOption(options)
    
    return chart
  } catch (error) {
    console.error('初始化图表失败:', error)
    return null
  }
}

// 暴露echarts实例作为默认导出
export default echarts 