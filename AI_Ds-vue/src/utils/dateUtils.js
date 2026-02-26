/**
 * 格式化日期
 * @param {Date|String|Number} date - 日期对象、日期字符串或时间戳
 * @param {String} format - 格式化模式，例如 'YYYY-MM-DD HH:mm:ss'
 * @returns {String} 格式化后的日期字符串
 */
export function formatDate(date, format = 'YYYY-MM-DD') {
  if (!date) return '';
  
  const d = date instanceof Date ? date : new Date(date);
  
  if (isNaN(d.getTime())) {
    console.error('无效的日期:', date);
    return '';
  }
  
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  const hours = String(d.getHours()).padStart(2, '0');
  const minutes = String(d.getMinutes()).padStart(2, '0');
  const seconds = String(d.getSeconds()).padStart(2, '0');
  
  return format
    .replace('YYYY', year)
    .replace('MM', month)
    .replace('DD', day)
    .replace('HH', hours)
    .replace('mm', minutes)
    .replace('ss', seconds);
}

/**
 * 获取相对于当前时间的友好显示
 * @param {Date|String|Number} date - 日期对象、日期字符串或时间戳
 * @returns {String} 友好的时间显示
 */
export function getRelativeTime(date) {
  if (!date) return '';
  
  const d = date instanceof Date ? date : new Date(date);
  
  if (isNaN(d.getTime())) {
    console.error('无效的日期:', date);
    return '';
  }
  
  const now = new Date();
  const diff = now.getTime() - d.getTime();
  
  // 转换为秒
  const diffInSeconds = Math.floor(diff / 1000);
  
  if (diffInSeconds < 60) {
    return '刚刚';
  }
  
  // 转换为分钟
  const diffInMinutes = Math.floor(diffInSeconds / 60);
  
  if (diffInMinutes < 60) {
    return `${diffInMinutes}分钟前`;
  }
  
  // 转换为小时
  const diffInHours = Math.floor(diffInMinutes / 60);
  
  if (diffInHours < 24) {
    return `${diffInHours}小时前`;
  }
  
  // 转换为天
  const diffInDays = Math.floor(diffInHours / 24);
  
  if (diffInDays < 30) {
    return `${diffInDays}天前`;
  }
  
  // 转换为月
  const diffInMonths = Math.floor(diffInDays / 30);
  
  if (diffInMonths < 12) {
    return `${diffInMonths}个月前`;
  }
  
  // 转换为年
  const diffInYears = Math.floor(diffInMonths / 12);
  
  return `${diffInYears}年前`;
} 