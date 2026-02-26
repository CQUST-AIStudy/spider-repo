/**
 * 将教师评语文本转换为图片
 * @param {string} comment 教师评语文本
 * @param {object} options 配置项
 * @returns {Promise<string>} 返回图片的 base64 编码
 */
export const commentToImage = async (comment, options = {}) => {
  if (!comment || !comment.trim()) {
    return null;
  }

  const {
    width = 800,
    fontSize = 16,
    lineHeight = 1.5,
    fontFamily = '宋体',
    padding = 20,
    textColor = '#000000',
    backgroundColor = '#FFFFFF',
    watermark = '教师评语', // 水印文本
    watermarkColor = 'rgba(200, 200, 200, 0.3)', // 水印颜色
  } = options;

  // 创建 Canvas 元素
  const canvas = document.createElement('canvas');
  const ctx = canvas.getContext('2d');

  // 设置字体样式
  ctx.font = `${fontSize}px ${fontFamily}`;
  
  // 分割文本为多行
  const lines = comment.split('\n');
  
  // 计算每行文本的宽度，找出最大宽度
  let maxLineWidth = 0;
  for (const line of lines) {
    const lineWidth = ctx.measureText(line).width + padding * 2;
    maxLineWidth = Math.max(maxLineWidth, lineWidth);
  }

  // 设置 Canvas 的宽度
  const canvasWidth = Math.min(Math.max(width, maxLineWidth), 1200); // 限制最大宽度
  
  // 计算 Canvas 的高度
  const canvasHeight = lines.length * fontSize * lineHeight + padding * 2;
  
  // 设置 Canvas 尺寸
  canvas.width = canvasWidth;
  canvas.height = canvasHeight;
  
  // 填充背景色
  ctx.fillStyle = backgroundColor;
  ctx.fillRect(0, 0, canvasWidth, canvasHeight);
  
  // 绘制水印
  ctx.save();
  ctx.font = `${fontSize * 2.5}px ${fontFamily}`;
  ctx.fillStyle = watermarkColor;
  ctx.translate(canvasWidth / 2, canvasHeight / 2);
  ctx.rotate(-Math.PI / 6); // 倾斜角度
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  ctx.fillText(watermark, 0, 0);
  ctx.restore();
  
  // 设置文本样式
  ctx.font = `${fontSize}px ${fontFamily}`;
  ctx.fillStyle = textColor;
  ctx.textBaseline = 'top';
  
  // 绘制文本行
  for (let i = 0; i < lines.length; i++) {
    const y = padding + i * fontSize * lineHeight;
    ctx.fillText(lines[i], padding, y);
  }
  
  // 转换为 base64 图片
  return canvas.toDataURL('image/png');
};

/**
 * 将 base64 图片数据转换为可在 docx 中使用的 ArrayBuffer
 * @param {string} base64 base64 编码的图片数据
 * @returns {Promise<ArrayBuffer>} ArrayBuffer 数据
 */
export const base64ToArrayBuffer = async (base64) => {
  if (!base64) {
    return null;
  }
  
  // 去掉 base64 编码的前缀
  const base64Data = base64.replace(/^data:image\/(png|jpeg|jpg);base64,/, '');
  
  // 转换为 ArrayBuffer
  const binaryString = window.atob(base64Data);
  const bytes = new Uint8Array(binaryString.length);
  for (let i = 0; i < binaryString.length; i++) {
    bytes[i] = binaryString.charCodeAt(i);
  }
  
  return bytes.buffer;
};

/**
 * 将教师评语转换为 docx 图片数据
 * @param {string} comment 教师评语
 * @param {object} options 配置项
 * @returns {Promise<{buffer: ArrayBuffer, width: number, height: number}>} 图片数据和尺寸
 */
export const convertCommentToImageForDocx = async (comment, options = {}) => {
  const base64 = await commentToImage(comment, options);
  if (!base64) {
    return null;
  }
  
  // 创建一个临时图像元素来获取图像尺寸
  const img = new Image();
  
  // 使用 Promise 等待图像加载
  await new Promise((resolve) => {
    img.onload = resolve;
    img.src = base64;
  });
  
  // 转换为 docx 需要的格式
  const buffer = await base64ToArrayBuffer(base64);
  
  // 获取图像实际尺寸（单位：像素）
  const width = img.width;
  const height = img.height;
  
  return {
    buffer,
    width,
    height
  };
};

export default {
  commentToImage,
  base64ToArrayBuffer,
  convertCommentToImageForDocx
};