"""Document parser routing PDF and DOCX into a unified ParsedDocument structure."""
import io
import re
import zipfile

from models.pipeline_models import ImageInfo, ParsedDocument, ParsedPage
from pipeline.pdf_parser import parse_pdf


def parse_document(file_bytes: bytes, filename: str | None) -> ParsedDocument:
    lower = (filename or "").lower()
    if file_bytes.startswith(b"%PDF"):
        return parse_pdf(file_bytes)
    if lower.endswith(".docx"):
        return parse_docx(file_bytes)
    if lower.endswith(".pdf") or not lower:
        return parse_pdf(file_bytes)
    return ParsedDocument(error=f"UNSUPPORTED_DOCUMENT_TYPE: {filename or 'unknown'}")


def parse_docx(docx_bytes: bytes) -> ParsedDocument:
    try:
        with zipfile.ZipFile(io.BytesIO(docx_bytes)) as archive:
            if "word/document.xml" not in archive.namelist():
                return ParsedDocument(error="DOCX_PARSE_ERROR: missing word/document.xml")

            document_xml = archive.read("word/document.xml").decode("utf-8", errors="ignore")
            text_fragments = re.findall(r"<w:t[^>]*>(.*?)</w:t>", document_xml)
            text = _normalize_docx_text("".join(_decode_xml_entities(fragment) for fragment in text_fragments))

            images = []
            for name in archive.namelist():
                if not name.startswith("word/media/"):
                    continue
                try:
                    images.append(ImageInfo(page=1, bbox=[], image_bytes=archive.read(name)))
                except Exception:
                    continue

            return ParsedDocument(pages=[ParsedPage(page_num=1, text=text, images=images)])
    except Exception as e:
        return ParsedDocument(error=f"DOCX_PARSE_ERROR: {str(e)}")


def _normalize_docx_text(text: str) -> str:
    normalized = text.replace("\r\n", "\n").replace("\r", "\n")
    normalized = normalized.replace("\u3000", " ")
    normalized = re.sub(r"\n{3,}", "\n\n", normalized)
    return normalized.strip()


def _decode_xml_entities(text: str) -> str:
    return (
        text.replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .replace("&quot;", '"')
        .replace("&apos;", "'")
    )
