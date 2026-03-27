let fontStylesPromise = null

export async function ensureHandwritingFont(fontFamily = 'ZiYouLangManTi', fontSize = 28) {
  if (typeof window === 'undefined') return

  if (!fontStylesPromise) {
    fontStylesPromise = import('../assets/styles/fonts.css').catch(() => null)
  }

  await fontStylesPromise

  if (!document.fonts?.load) return

  try {
    await document.fonts.load(`${fontSize}px ${fontFamily}`)
    await document.fonts.ready
  } catch {
    // Ignore font loading failures and fall back to system fonts.
  }
}

export default {
  ensureHandwritingFont,
}
