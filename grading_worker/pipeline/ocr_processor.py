"""OCR processing using PaddleOCR with post-processing."""
import io
import numpy as np
from PIL import Image
from models.pipeline_models import OcrResult, OcrLine

_ocr_engine = None


def _get_ocr():
    global _ocr_engine
    if _ocr_engine is None:
        from paddleocr import PaddleOCR
        _ocr_engine = PaddleOCR(use_angle_cls=True, lang="ch", show_log=False)
    return _ocr_engine


def fullwidth_to_halfwidth(text: str) -> str:
    """Convert fullwidth ASCII characters (U+FF01-U+FF5E) to halfwidth (U+0021-U+007E)."""
    result = []
    for ch in text:
        code = ord(ch)
        if 0xFF01 <= code <= 0xFF5E:
            result.append(chr(code - 0xFEE0))
        elif code == 0x3000:  # fullwidth space
            result.append(' ')
        else:
            result.append(ch)
    return ''.join(result)


def common_char_fixes(text: str) -> str:
    """Fix common OCR character confusions."""
    replacements = {
        '０': '0', '１': '1', '２': '2', '３': '3', '４': '4',
        '５': '5', '６': '6', '７': '7', '８': '8', '９': '9',
        '（': '(', '）': ')', '【': '[', '】': ']',
        '；': ';', '：': ':', '，': ',',
    }
    for old, new in replacements.items():
        text = text.replace(old, new)
    return text


def post_process(text: str) -> str:
    """Apply fullwidth→halfwidth and common fixes."""
    text = fullwidth_to_halfwidth(text)
    text = common_char_fixes(text)
    return text


def run_ocr(image_bytes: bytes) -> OcrResult:
    """Run PaddleOCR on image bytes and return structured result."""
    try:
        img = Image.open(io.BytesIO(image_bytes)).convert("RGB")
        img_array = np.array(img)

        ocr = _get_ocr()
        results = ocr.ocr(img_array, cls=True)

        if not results or not results[0]:
            return OcrResult(text="", confidence=0.0, lines=[])

        lines = []
        all_text_parts = []
        total_conf = 0.0

        for line_data in results[0]:
            bbox_points = line_data[0]
            text_info = line_data[1]
            text = text_info[0]
            conf = text_info[1]

            text = post_process(text)

            bbox = [
                bbox_points[0][0], bbox_points[0][1],
                bbox_points[2][0], bbox_points[2][1]
            ]

            lines.append(OcrLine(text=text, bbox=bbox, confidence=conf))
            all_text_parts.append(text)
            total_conf += conf

        # Sort lines by vertical position to preserve reading order
        lines.sort(key=lambda l: l.bbox[1] if l.bbox else 0)

        full_text = "\n".join(all_text_parts)
        avg_conf = total_conf / len(lines) if lines else 0.0

        return OcrResult(text=full_text, confidence=avg_conf, lines=lines)

    except Exception as e:
        return OcrResult(text="", confidence=0.0, lines=[])
