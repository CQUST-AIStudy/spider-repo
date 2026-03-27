import { ensureHandwritingFont } from './handwritingFont'

export async function commentToImage(comment, options = {}) {
  if (!comment || !comment.trim()) {
    return null
  }

  const {
    width = 900,
    fontSize = 28,
    lineHeight = 1.8,
    fontFamily = 'ZiYouLangManTi',
    padding = 28,
    textColor = '#C81E1E',
    backgroundColor = '#FFF8F8',
    watermark = '教师评语',
    watermarkColor = 'rgba(200, 120, 120, 0.16)',
  } = options

  await ensureHandwritingFont(fontFamily, fontSize)

  const canvas = document.createElement('canvas')
  const ctx = canvas.getContext('2d')
  ctx.font = `${fontSize}px ${fontFamily}, KaiTi, STKaiti, serif`

  const lines = comment.split(/\r?\n/)
  let maxLineWidth = 0
  for (const line of lines) {
    maxLineWidth = Math.max(maxLineWidth, ctx.measureText(line || '　').width + padding * 2)
  }

  canvas.width = Math.min(Math.max(width, maxLineWidth), 1400)
  canvas.height = Math.max(lines.length, 1) * fontSize * lineHeight + padding * 2

  ctx.fillStyle = backgroundColor
  ctx.fillRect(0, 0, canvas.width, canvas.height)

  ctx.save()
  ctx.font = `${fontSize * 2.2}px ${fontFamily}, KaiTi, STKaiti, serif`
  ctx.fillStyle = watermarkColor
  ctx.translate(canvas.width / 2, canvas.height / 2)
  ctx.rotate(-Math.PI / 8)
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  ctx.fillText(watermark, 0, 0)
  ctx.restore()

  ctx.font = `${fontSize}px ${fontFamily}, KaiTi, STKaiti, serif`
  ctx.fillStyle = textColor
  ctx.textBaseline = 'top'

  lines.forEach((line, index) => {
    const y = padding + index * fontSize * lineHeight
    ctx.fillText(line, padding, y)
  })

  return canvas.toDataURL('image/png')
}

export async function base64ToArrayBuffer(base64) {
  if (!base64) return null
  const base64Data = base64.replace(/^data:image\/(png|jpeg|jpg);base64,/, '')
  const binaryString = window.atob(base64Data)
  const bytes = new Uint8Array(binaryString.length)
  for (let i = 0; i < binaryString.length; i += 1) {
    bytes[i] = binaryString.charCodeAt(i)
  }
  return bytes.buffer
}

export async function convertCommentToImageForDocx(comment, options = {}) {
  const base64 = await commentToImage(comment, options)
  if (!base64) return null

  const img = new Image()
  await new Promise(resolve => {
    img.onload = resolve
    img.src = base64
  })

  const buffer = await base64ToArrayBuffer(base64)
  return {
    buffer,
    width: img.width,
    height: img.height,
  }
}

export default {
  commentToImage,
  base64ToArrayBuffer,
  convertCommentToImageForDocx,
}
