import { ElMessage, ElLoading } from 'element-plus';

/**
 * 使用Promise包装的全局loading服务
 * @param {Function} promiseFn - 返回Promise的函数
 * @param {Object} options - loading配置选项
 * @returns {Promise} 原始Promise的结果
 */
export async function withLoading(promiseFn, options = {}) {
  const defaultOptions = {
    text: '加载中...',
    background: 'rgba(0, 0, 0, 0.7)'
  };
  
  const loadingInstance = ElLoading.service({
    ...defaultOptions,
    ...options
  });
  
  try {
    const result = await promiseFn();
    return result;
  } finally {
    loadingInstance.close();
  }
}

/**
 * 处理API响应的通用方法
 * @param {Object} response - API响应对象
 * @param {Object} options - 配置选项
 * @returns {Object} 处理后的响应
 */
export function handleApiResponse(response, options = {}) {
  const {
    showSuccessMessage = false,
    showErrorMessage = true,
    successMessage = '操作成功',
    onSuccess = null,
    onError = null
  } = options;
  
  if (response && response.success) {
    if (showSuccessMessage) {
      ElMessage.success(response.message || successMessage);
    }
    
    if (onSuccess && typeof onSuccess === 'function') {
      onSuccess(response);
    }
    
    return response;
  } else {
    const errorMsg = (response && response.message) || '操作失败';
    
    if (showErrorMessage) {
      ElMessage.error(errorMsg);
    }
    
    if (onError && typeof onError === 'function') {
      onError(response);
    }
    
    return response;
  }
}

/**
 * 错误处理工具
 * @param {Error} error - 错误对象 
 * @param {String} fallbackMessage - 默认错误消息
 * @param {Function} callback - 错误处理后的回调
 */
export function handleError(error, fallbackMessage = '发生错误', callback = null) {
  let errorMessage = fallbackMessage;
  
  if (error) {
    if (error.response && error.response.data) {
      // axios错误
      const { data } = error.response;
      errorMessage = data.message || data.error || String(data);
    } else if (error.message) {
      // 一般JavaScript错误
      errorMessage = error.message;
    } else if (typeof error === 'string') {
      // 字符串错误
      errorMessage = error;
    }
  }
  
  // 显示错误提示
  ElMessage.error(errorMessage);
  
  if (process.env.NODE_ENV === 'development') {
    console.error('Error details:', error);
  }
  
  // 执行回调
  if (callback && typeof callback === 'function') {
    callback(errorMessage, error);
  }
  
  return { success: false, message: errorMessage };
}