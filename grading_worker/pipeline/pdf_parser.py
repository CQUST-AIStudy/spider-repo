"""PDF parsing using PyMuPDF (fitz) to extract text and images."""
import fitz  # PyMuPDF
from models.pipeline_models import ParsedDocument, ParsedPage, ImageInfo


def parse_pdf(pdf_bytes: bytes) -> ParsedDocument:
    """Parse a PDF and extract text + images per page."""
    try:
        doc = fitz.open(stream=pdf_bytes, filetype="pdf")
    except Exception as e:
        return ParsedDocument(error=f"PDF_PARSE_ERROR: {str(e)}")

    pages = []
    try:
        for page_num in range(len(doc)):
            page = doc[page_num]
            text = page.get_text("text") or ""

            images = []
            for img_index, img in enumerate(page.get_images(full=True)):
                xref = img[0]
                try:
                    base_image = doc.extract_image(xref)
                    if base_image and base_image.get("image"):
                        # Get image bbox from page
                        img_rects = page.get_image_rects(xref)
                        bbox = []
                        if img_rects:
                            r = img_rects[0]
                            bbox = [r.x0, r.y0, r.x1, r.y1]

                        images.append(ImageInfo(
                            page=page_num + 1,
                            bbox=bbox,
                            image_bytes=base_image["image"],
                        ))
                except Exception:
                    continue

            pages.append(ParsedPage(
                page_num=page_num + 1,
                text=text,
                images=images,
            ))
    except Exception as e:
        return ParsedDocument(error=f"PDF_PARSE_ERROR: {str(e)}")
    finally:
        doc.close()

    return ParsedDocument(pages=pages)
