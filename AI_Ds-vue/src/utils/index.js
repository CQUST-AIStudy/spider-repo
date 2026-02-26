// 导出各工具类函数
import * as chartUtils from './chartUtils';
import * as chartHelpers from './chartHelpers'; 
import * as resizeUtils from './resizeUtils';
import * as dateUtils from './dateUtils';
import * as validationUtils from './validationUtils';
import * as serviceUtils from './serviceUtils';

// 导出所有工具函数，平铺展开
export const {
  initChart,
  default: echarts
} = chartUtils;

export const {
  safeInitCharts,
  createBarChartOptions,
  createPieChartOptions
} = chartHelpers;

export const {
  resizeCharts,
  resizeChartsWithDebounce
} = resizeUtils;

export const {
  formatDate,
  getRelativeTime
} = dateUtils;

export const {
  isValidEmail,
  isValidPhone,
  checkPasswordStrength,
  requiredValidator,
  lengthValidator
} = validationUtils;

export const {
  withLoading,
  handleApiResponse,
  handleError
} = serviceUtils;

// 默认导出
export default {
  chart: {
    ...chartUtils,
    ...chartHelpers,
    ...resizeUtils
  },
  date: dateUtils,
  validation: validationUtils,
  service: serviceUtils
}; 