"""OCR processing using PaddleOCR with multi-pass image enhancement fallback."""
import io
import threading

import numpy as np
from PIL import Image, ImageEnhance

from models.pipeline_models import OcrLine, OcrResult

_thread_local = threading.local()


def _get_ocr():
    engine = getattr(_thread_local, "ocr_engine", None)
    if engine is None:
        from paddleocr import PaddleOCR

        engine = PaddleOCR(use_angle_cls=True, lang="ch", show_log=False)
        _thread_local.ocr_engine = engine
    return engine


def fullwidth_to_halfwidth(text: str) -> str:
    """Convert fullwidth ASCII characters (U+FF01-U+FF5E) to halfwidth (U+0021-U+007E)."""
    result = []
    for ch in text:
        code = ord(ch)
        if 0xFF01 <= code <= 0xFF5E:
            result.append(chr(code - 0xFEE0))
        elif code == 0x3000:  # fullwidth space
            result.append(" ")
        else:
            result.append(ch)
    return "".join(result)


def post_process(text: str) -> str:
    """Normalize OCR text for scoring."""
    return fullwidth_to_halfwidth(text or "").strip()


def _ocr_once(img_array: np.ndarray) -> OcrResult:
    """Run one OCR pass against a prepared numpy image."""
    try:
        ocr = _get_ocr()
        results = ocr.ocr(img_array, cls=True)
    except Exception:
        return OcrResult(text="", confidence=0.0, lines=[])

    if not results or not results[0]:
        return OcrResult(text="", confidence=0.0, lines=[])

    lines = []
    total_conf = 0.0
    text_parts = []
    for line_data in results[0]:
        try:
            bbox_points = line_data[0]
            text_info = line_data[1]
            text = post_process(text_info[0])
            conf = float(text_info[1])
            bbox = [
                float(bbox_points[0][0]),
                float(bbox_points[0][1]),
                float(bbox_points[2][0]),
                float(bbox_points[2][1]),
            ]
            if not text:
                continue
            lines.append(OcrLine(text=text, bbox=bbox, confidence=conf))
            text_parts.append(text)
            total_conf += conf
        except Exception:
            continue

    if not lines:
        return OcrResult(text="", confidence=0.0, lines=[])

    lines.sort(key=lambda l: l.bbox[1] if l.bbox else 0)
    full_text = "\n".join(text_parts)
    avg_conf = total_conf / len(lines)
    return OcrResult(text=full_text, confidence=avg_conf, lines=lines)


def _score_result(result: OcrResult) -> float:
    txt_len = len((result.text or "").strip())
    return txt_len * max(result.confidence, 0.05)


def _is_good_enough(result: OcrResult) -> bool:
    txt = (result.text or "").strip()
    return len(txt) >= 30 or (len(txt) >= 15 and result.confidence >= 0.75)


def run_ocr(image_bytes: bytes) -> OcrResult:
    """Run PaddleOCR on image bytes with enhancement fallback for low-quality scans."""
    try:
        original = Image.open(io.BytesIO(image_bytes)).convert("RGB")
    except Exception:
        return OcrResult(text="", confidence=0.0, lines=[])

    candidates = []

    # Fast path: original image.
    base_result = _ocr_once(np.array(original))
    candidates.append(base_result)
    if _is_good_enough(base_result):
        return base_result

    # Fallback 1: gray + contrast + sharpen.
    gray = original.convert("L")
    gray = ImageEnhance.Contrast(gray).enhance(1.8)
    gray = ImageEnhance.Sharpness(gray).enhance(1.6)
    enhanced = gray.convert("RGB")
    r1 = _ocr_once(np.array(enhanced))
    candidates.append(r1)
    if _is_good_enough(r1):
        return r1

    # Fallback 2: upscale before OCR (helps tiny screenshots).
    w, h = enhanced.size
    if min(w, h) < 1400:
        upscaled = enhanced.resize((int(w * 1.8), int(h * 1.8)), Image.Resampling.BICUBIC)
    else:
        upscaled = enhanced
    r2 = _ocr_once(np.array(upscaled))
    candidates.append(r2)

    return max(candidates, key=_score_result)
