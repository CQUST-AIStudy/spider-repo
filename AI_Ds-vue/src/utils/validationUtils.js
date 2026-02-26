/**
 * 验证邮箱格式
 * @param {String} email - 邮箱地址
 * @returns {Boolean} 是否为有效邮箱
 */
export function isValidEmail(email) {
  const emailRegex = /^[\w-]+(\.[\w-]+)*@[\w-]+(\.[\w-]+)+$/;
  return emailRegex.test(email);
}

/**
 * 验证手机号格式（中国大陆手机号）
 * @param {String} phone - 手机号码
 * @returns {Boolean} 是否为有效手机号
 */
export function isValidPhone(phone) {
  const phoneRegex = /^1[3-9]\d{9}$/;
  return phoneRegex.test(phone);
}

/**
 * 验证密码强度
 * @param {String} password - 密码
 * @returns {Object} 包含强度等级和提示信息
 */
export function checkPasswordStrength(password) {
  if (!password || password.length < 6) {
    return {
      level: 0,
      message: '密码至少需要6个字符'
    };
  }
  
  let score = 0;
  
  // 长度检查
  if (password.length >= 8) score += 1;
  if (password.length >= 12) score += 1;
  
  // 包含小写字母
  if (/[a-z]/.test(password)) score += 1;
  
  // 包含大写字母
  if (/[A-Z]/.test(password)) score += 1;
  
  // 包含数字
  if (/\d/.test(password)) score += 1;
  
  // 包含特殊字符
  if (/[\W_]/.test(password)) score += 1;
  
  // 评估强度等级
  let level = 0;
  let message = '';
  
  if (score < 3) {
    level = 1;
    message = '弱密码';
  } else if (score < 5) {
    level = 2;
    message = '中等强度密码';
  } else {
    level = 3;
    message = '强密码';
  }
  
  return { level, message };
}

/**
 * 表单项必填验证
 * @returns {Function} Element Plus表单验证函数
 */
export function requiredValidator(message = '此项为必填项') {
  return (rule, value, callback) => {
    if (value === undefined || value === null || value === '') {
      callback(new Error(message));
    } else {
      callback();
    }
  };
}

/**
 * 创建一个长度验证器
 * @param {Number} min - 最小长度
 * @param {Number} max - 最大长度
 * @param {String} message - 错误信息模板
 * @returns {Function} Element Plus表单验证函数
 */
export function lengthValidator(min, max, message) {
  return (rule, value, callback) => {
    if (value === undefined || value === null || value === '') {
      callback();
      return;
    }
    
    const len = String(value).length;
    const defaultMessage = `长度应为${min}到${max}个字符之间`;
    
    if (len < min || len > max) {
      callback(new Error(message || defaultMessage));
    } else {
      callback();
    }
  };
} 