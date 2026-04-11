package com.tap.backend.service;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.util.Matrix;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.xmlbeans.XmlCursor;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.springframework.stereotype.Service;

/**
 * Service that renders "red-pen teacher annotation" overlays onto student reports.
 * <p>
 * Supports both DOCX and PDF input files.  The output looks as if a teacher
 * physically marked the paper with a red pen: handwriting-style score on the
 * first page, scattered red check-marks (鈭? in the body, and a teacher review
 * block appended at the end.
 */
@Service
public class AnnotatedStudentReportService {
    public static final String FILE_TYPE_ANNOTATED_DOCX = "annodoc";
    public static final String FILE_TYPE_ANNOTATED_PDF = "annopdf";

    /* 鈹€鈹€ colour palette 鈹€鈹€ */
    private static final String RED_HEX = "D62828";
    private static final Color RED_COLOR = new Color(214, 40, 40);
    private static final Color RED_LIGHT = new Color(214, 40, 40, 180);

    /* 鈹€鈹€ font names 鈹€鈹€ */
    private static final String HANDWRITING_FONT = "\u534e\u6587\u884c\u6977";
    private static final String HANDWRITING_FALLBACK = "\u6977\u4f53";

    /* 鈹€鈹€ marks 鈹€鈹€ */
    private static final String DOCX_CHECK_MARK = "\u221a";
    private static final String PDF_CHECK_MARK = "V";
    private static final byte[] PDF_MAGIC = {0x25, 0x50, 0x44, 0x46};
    private static final List<String> SCORE_KEYWORDS = List.of(
            "\u5f97\u5206",
            "\u5206\u6570",
            "\u6210\u7ee9",
            "\u8bc4\u5206",
            "score",
            "\u603b\u5206"
    );

    /* 鈹€鈹€ check-mark image cache (thread-safe lazy init) 鈹€鈹€ */
    private volatile byte[] checkMarkPngBytes;

    // 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲
    //  Public entry point
    // 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲

    public RenderedReport render(String originalFilename,
                                 byte[] sourceBytes,
                                 String studentName,
                                 BigDecimal totalScore,
                                 String teacherComment,
                                 List<String> dimensionComments,
                                 String teacherSignature) {
        if (sourceBytes == null || sourceBytes.length == 0) {
            throw new IllegalArgumentException("Source report is empty");
        }

        String normalizedFilename = originalFilename == null ? "" : originalFilename.toLowerCase(Locale.ROOT);
        try {
            if (normalizedFilename.endsWith(".docx")) {
                return renderDocx(sourceBytes, studentName, totalScore, teacherComment, dimensionComments, teacherSignature);
            }
            if (normalizedFilename.endsWith(".pdf") || isPdf(sourceBytes)) {
                return renderPdf(sourceBytes, studentName, totalScore, teacherComment, dimensionComments, teacherSignature);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to annotate report", e);
        }
        throw new IllegalArgumentException("Only PDF and DOCX student reports are supported");
    }

    // 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲
    //  DOCX rendering
    // 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲

    private RenderedReport renderDocx(byte[] sourceBytes,
                                      String studentName,
                                      BigDecimal totalScore,
                                      String teacherComment,
                                      List<String> dimensionComments,
                                      String teacherSignature) throws IOException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(sourceBytes));
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Random random = buildRandom(studentName, totalScore);
            List<XWPFParagraph> paragraphs = collectDocxParagraphs(document);

            // 1) Red handwriting score on front page
            insertDocxScoreInFrontMatter(document, paragraphs, totalScore);

            // 2) Scattered red check-marks with handwriting-style images
            appendDocxCheckMarkImages(document, paragraphs, random);

            // 3) Teacher review block at the end
            appendDocxReviewBlock(document, teacherComment, dimensionComments, teacherSignature);

            document.write(outputStream);
            return new RenderedReport(
                    FILE_TYPE_ANNOTATED_DOCX,
                    ".docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    outputStream.toByteArray()
            );
        }
    }

    private List<XWPFParagraph> collectDocxParagraphs(XWPFDocument document) {
        List<XWPFParagraph> result = new ArrayList<>(document.getParagraphs());
        for (XWPFTable table : document.getTables()) {
            collectTableParagraphs(table, result);
        }
        return result;
    }

    private void collectTableParagraphs(XWPFTable table, List<XWPFParagraph> target) {
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                target.addAll(cell.getParagraphs());
                for (XWPFTable nested : cell.getTables()) {
                    collectTableParagraphs(nested, target);
                }
            }
        }
    }

    /**
     * Insert a red handwriting-style score near the top of the document,
     * next to an existing "寰楀垎" / "鎴愮哗" keyword if found.
     */
    private void insertDocxScoreInFrontMatter(XWPFDocument document,
                                              List<XWPFParagraph> paragraphs,
                                              BigDecimal totalScore) {
        String scoreText = " " + formatScore(totalScore) + "\u5206 ";
        int inspected = 0;
        for (XWPFParagraph paragraph : paragraphs) {
            String text = safeText(paragraph.getText());
            if (text.isBlank()) {
                continue;
            }
            inspected++;
            if (containsScoreKeyword(text)) {
                appendDocxRun(paragraph, scoreText, 22, true);
                return;
            }
            if (inspected >= 24) {
                break;
            }
        }

        // Fallback: add to the first non-blank paragraph
        XWPFParagraph fallback = paragraphs.stream()
                .filter(paragraph -> !safeText(paragraph.getText()).isBlank())
                .findFirst()
                .orElseGet(document::createParagraph);
        appendDocxRun(fallback, "  " + scoreText.trim(), 22, true);
    }

    /**
     * Insert handwriting-style check-mark images (red 鈭? into random paragraphs.
     */
    private void appendDocxCheckMarkImages(XWPFDocument document,
                                           List<XWPFParagraph> paragraphs,
                                           Random random) {
        List<XWPFParagraph> candidates = paragraphs.stream()
                .filter(paragraph -> {
                    String text = safeText(paragraph.getText());
                    return !text.isBlank() && text.length() > 8;
                })
                .toList();
        int desired = Math.min(8, Math.max(3, candidates.size() / 6));
        byte[] checkImg = getCheckMarkPng();

        for (Integer index : pickIndices(candidates.size(), desired, random)) {
            XWPFParagraph paragraph = candidates.get(index);
            try {
                // Alternate between image check-marks and text check-marks
                if (random.nextBoolean() && checkImg != null) {
                    XWPFRun imgRun = paragraph.createRun();
                    imgRun.addPicture(
                            new ByteArrayInputStream(checkImg),
                            XWPFDocument.PICTURE_TYPE_PNG,
                            "check.png",
                            Units.toEMU(84 + random.nextInt(24)),
                            Units.toEMU(68 + random.nextInt(20))
                    );
                } else {
                    // Text-based red check mark with slight variation
                    String mark = random.nextBoolean() ? "  " + DOCX_CHECK_MARK : " " + DOCX_CHECK_MARK + " ";
                    appendDocxRun(paragraph, mark, 68 + random.nextInt(16), true);
                }
            } catch (Exception ignored) {
                // Fallback to text check mark
                appendDocxRun(paragraph, "  " + DOCX_CHECK_MARK, 72, true);
            }
        }
    }

    /**
     * Append a teacher review block at the end of the document with a styled
     * separator and red handwriting-style text.
     */
    private void appendDocxReviewBlock(XWPFDocument document,
                                       String teacherComment,
                                       List<String> dimensionComments,
                                       String teacherSignature) {
        XWPFParagraph anchorParagraph = findDocxReviewAnchor(document);

        XWPFParagraph separator = insertDocxParagraphAfterAnchor(document, anchorParagraph);
        separator.setSpacingBefore(220);
        separator.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun sepRun = separator.createRun();
        styleDocxRun(sepRun, 11, false);
        sepRun.setText("\u002d\u002d\u002d\u002d\u002d\u002d\u002d\u002d \u6559\u5e08\u8bc4\u8bed \u002d\u002d\u002d\u002d\u002d\u002d\u002d\u002d");

        XWPFParagraph titleParagraph = insertDocxParagraphAfterAnchor(document, separator);
        titleParagraph.setAlignment(ParagraphAlignment.LEFT);
        titleParagraph.setSpacingBefore(100);
        titleParagraph.setSpacingAfter(30);
        XWPFRun titleRun = titleParagraph.createRun();
        styleDocxRun(titleRun, 17, true);
        titleRun.setText("\u6559\u5e08\u8bc4\u8bed\uff1a");

        List<String> reviewLines = buildReviewLines(teacherComment, dimensionComments);
        XWPFParagraph lastParagraph = titleParagraph;
        for (String line : reviewLines) {
            XWPFParagraph paragraph = insertDocxParagraphAfterAnchor(document, lastParagraph);
            paragraph.setSpacingBefore(12);
            paragraph.setSpacingAfter(12);
            XWPFRun run = paragraph.createRun();
            styleDocxRun(run, 12, false);
            run.setText(line);
            lastParagraph = paragraph;
        }

        XWPFParagraph sigPara = insertDocxParagraphAfterAnchor(document, lastParagraph);
        sigPara.setAlignment(ParagraphAlignment.RIGHT);
        sigPara.setSpacingBefore(140);
        XWPFRun sigRun = sigPara.createRun();
        styleDocxRun(sigRun, 14, true);
        sigRun.setText(resolveTeacherSignature(teacherSignature));
    }

    private void appendDocxRun(XWPFParagraph paragraph, String text, int fontSize, boolean bold) {
        XWPFRun run = paragraph.createRun();
        styleDocxRun(run, fontSize, bold);
        run.setText(text);
    }

    private void styleDocxRun(XWPFRun run, int fontSize, boolean bold) {
        run.setColor(RED_HEX);
        run.setBold(bold);
        run.setFontFamily(HANDWRITING_FONT);
        run.setFontSize(fontSize);

        CTRPr runProperties = run.getCTR().isSetRPr() ? run.getCTR().getRPr() : run.getCTR().addNewRPr();
        CTFonts fonts = runProperties.addNewRFonts();
        fonts.setAscii(HANDWRITING_FALLBACK);
        fonts.setHAnsi(HANDWRITING_FALLBACK);
        fonts.setEastAsia(HANDWRITING_FONT);
    }

    private XWPFParagraph findDocxReviewAnchor(XWPFDocument document) {
        List<XWPFParagraph> paragraphs = document.getParagraphs();
        for (int i = paragraphs.size() - 1; i >= 0; i--) {
            XWPFParagraph paragraph = paragraphs.get(i);
            String text = safeText(paragraph.getText()).replace(" ", "");
            if (text.matches(".*[-_—一~]{6,}.*")) {
                return paragraph;
            }
        }
        return paragraphs.isEmpty() ? document.createParagraph() : paragraphs.get(paragraphs.size() - 1);
    }

    private XWPFParagraph insertDocxParagraphAfterAnchor(XWPFDocument document, XWPFParagraph anchor) {
        if (anchor == null) {
            return document.createParagraph();
        }
        XmlCursor cursor = anchor.getCTP().newCursor();
        try {
            cursor.toEndToken();
            XWPFParagraph paragraph = document.insertNewParagraph(cursor);
            return paragraph != null ? paragraph : document.createParagraph();
        } catch (Exception ignored) {
            return document.createParagraph();
        } finally {
            cursor.dispose();
        }
    }

    // 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲
    //  PDF rendering
    // 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲

    private RenderedReport renderPdf(byte[] sourceBytes,
                                     String studentName,
                                     BigDecimal totalScore,
                                     String teacherComment,
                                     List<String> dimensionComments,
                                     String teacherSignature) throws IOException {
        try (PDDocument document = PDDocument.load(sourceBytes);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Random random = buildRandom(studentName, totalScore);
            FontSelection fontSelection = loadPdfFont(document);

            if (document.getNumberOfPages() == 0) {
                document.addPage(new PDPage(PDRectangle.A4));
            }

            List<PDPage> pages = new ArrayList<>();
            document.getPages().forEach(pages::add);

            // 1) Draw score on first page
            drawPdfScoreOnFirstPage(document, pages.get(0), fontSelection, totalScore);

            // 2) Draw handwriting-style check marks with rotation
            drawPdfCheckMarks(document, pages, fontSelection, random);

            // 3) Draw review on last page
            drawPdfReviewOnLastPage(document, pages.get(pages.size() - 1), fontSelection,
                    teacherComment, dimensionComments, teacherSignature);

            document.save(outputStream);
            return new RenderedReport(FILE_TYPE_ANNOTATED_PDF, ".pdf", "application/pdf", outputStream.toByteArray());
        }
    }

    private void drawPdfScoreOnFirstPage(PDDocument document,
                                         PDPage page,
                                         FontSelection fontSelection,
                                         BigDecimal totalScore) throws IOException {
        String scoreLabel = normalizeForFont(fontSelection,
                formatScore(totalScore) + "\u5206",
                "Score: " + formatScore(totalScore));
        PdfTextAnchor anchor = locatePdfKeyword(document, 1, SCORE_KEYWORDS);
        PDRectangle box = page.getMediaBox();

        float x, y;
        if (anchor != null) {
            x = Math.min(box.getWidth() - 180f, anchor.endX() + 12f);
            y = Math.max(48f, box.getHeight() - anchor.yDirAdj() - 4f);
        } else {
            x = box.getWidth() - 200f;
            y = box.getHeight() - 52f;
        }

        try (PDPageContentStream stream = new PDPageContentStream(document, page, AppendMode.APPEND, true, true)) {
            stream.setNonStrokingColor(RED_COLOR);
            drawPdfText(stream, fontSelection.font(), 20f, x, y, scoreLabel);

            // Draw a subtle underline
            stream.setStrokingColor(RED_LIGHT);
            stream.setLineWidth(1.2f);
            float textWidth = fontSelection.font().getStringWidth(scoreLabel) / 1000f * 20f;
            stream.moveTo(x, y - 3f);
            stream.lineTo(x + textWidth, y - 3f);
            stream.stroke();
        }
    }

    /**
     * Draw handwriting-style check marks on random pages with rotation
     * to simulate natural teacher marking.
     */
    private void drawPdfCheckMarks(PDDocument document,
                                   List<PDPage> pages,
                                   FontSelection fontSelection,
                                   Random random) throws IOException {
        int desired = Math.min(6, Math.max(2, pages.size() + 2));
        for (Integer pageIndex : pickIndices(pages.size(), desired, random)) {
            PDPage page = pages.get(pageIndex);
            PDRectangle box = page.getMediaBox();

            // Place check mark at a realistic position (margins area)
            float x = 30f + random.nextFloat() * Math.max(60f, box.getWidth() - 140f);
            float y = 80f + random.nextFloat() * Math.max(120f, box.getHeight() - 200f);
            float angle = (float) Math.toRadians(-22 + random.nextInt(45));
            float size = 88f + random.nextInt(26);

            try (PDPageContentStream stream = new PDPageContentStream(document, page, AppendMode.APPEND, true, true)) {
                drawPdfCheckStroke(stream, x, y, size, angle);
            }
        }
    }

    private void drawPdfCheckStroke(PDPageContentStream stream,
                                    float x,
                                    float y,
                                    float size,
                                    float angle) throws IOException {
        stream.saveGraphicsState();
        stream.transform(Matrix.getRotateInstance(angle, x, y));
        stream.setStrokingColor(RED_COLOR);
        stream.setLineWidth(Math.max(5.2f, size / 10f));
        stream.moveTo(x - size * 0.46f, y + size * 0.16f);
        stream.curveTo(
                x - size * 0.34f, y + size * 0.08f,
                x - size * 0.22f, y - size * 0.02f,
                x - size * 0.08f, y - size * 0.24f
        );
        stream.curveTo(
                x + size * 0.10f, y - size * 0.02f,
                x + size * 0.34f, y + size * 0.22f,
                x + size * 0.70f, y + size * 0.64f
        );
        stream.stroke();
        stream.restoreGraphicsState();
    }

        private void drawPdfReviewOnLastPage(PDDocument document,
                                         PDPage page,
                                         FontSelection fontSelection,
                                         String teacherComment,
                                         List<String> dimensionComments,
                                         String teacherSignature) throws IOException {
        List<StyledLine> styledLines = new ArrayList<>();
        styledLines.add(new StyledLine(normalizeForFont(fontSelection,
                "-------- 教师评语 --------", "-------- Teacher Review --------"), 12f));
        styledLines.add(new StyledLine(normalizeForFont(fontSelection,
                "教师评语", "Teacher Review"), 16f));
        for (String line : buildReviewLines(teacherComment, dimensionComments)) {
            styledLines.add(new StyledLine(normalizeForFont(fontSelection, line, line), 11f));
        }
        String signatureLine = normalizeForFont(fontSelection, resolveTeacherSignature(teacherSignature), "Teacher");

        PDRectangle templateBox = page.getMediaBox();
        float margin = 44f;
        float maxWidth = templateBox.getWidth() - margin * 2;
        PDPage currentPage = page;
        float initialStartY = findPdfReviewStartY(document, page, templateBox);
        float y;

        PDPageContentStream stream = new PDPageContentStream(document, currentPage, AppendMode.APPEND, true, true);
        try {
            stream.setNonStrokingColor(RED_COLOR);
            y = startPdfReviewSection(stream, templateBox, margin, fontSelection, false, initialStartY);
            for (StyledLine styledLine : styledLines) {
                List<String> wrapped = wrapPdfText(
                        fontSelection.font(),
                        styledLine.text(),
                        styledLine.fontSize(),
                        maxWidth
                );
                for (String line : wrapped) {
                    float nextLineHeight = styledLine.fontSize() + 6f;
                    if (y - nextLineHeight < 40f) {
                        stream.close();
                        currentPage = new PDPage(templateBox);
                        document.addPage(currentPage);
                        stream = new PDPageContentStream(document, currentPage, AppendMode.APPEND, true, true);
                        stream.setNonStrokingColor(RED_COLOR);
                        y = startPdfReviewSection(stream, templateBox, margin, fontSelection, true, templateBox.getHeight() - 72f);
                    }
                    drawPdfText(stream, fontSelection.font(), styledLine.fontSize(), margin, y, line);
                    y -= nextLineHeight;
                }
                y -= 4f;
            }
            if (y - 20f < 40f) {
                stream.close();
                currentPage = new PDPage(templateBox);
                document.addPage(currentPage);
                stream = new PDPageContentStream(document, currentPage, AppendMode.APPEND, true, true);
                stream.setNonStrokingColor(RED_COLOR);
                y = startPdfReviewSection(stream, templateBox, margin, fontSelection, true, templateBox.getHeight() - 72f);
            }
            float sigWidth = fontSelection.font().getStringWidth(signatureLine) / 1000f * 12f;
            drawPdfText(stream, fontSelection.font(), 12f, templateBox.getWidth() - margin - sigWidth, y - 8f, signatureLine);
        } finally {
            stream.close();
        }
    }

    private float startPdfReviewSection(PDPageContentStream stream,
                                        PDRectangle box,
                                        float margin,
                                        FontSelection fontSelection,
                                        boolean continued,
                                        float startY) throws IOException {
        float y = startY;
        stream.setStrokingColor(RED_LIGHT);
        stream.setLineWidth(0.9f);
        stream.moveTo(margin, y + 10f);
        stream.lineTo(box.getWidth() - margin, y + 10f);
        stream.stroke();
        if (continued) {
            drawPdfText(stream, fontSelection.font(), 14f, margin, y - 2f,
                    normalizeForFont(fontSelection, "教师评语（续）", "Teacher Review (Cont.)"));
            y -= 24f;
        } else {
            y -= 4f;
        }
        return y;
    }

    private float findPdfReviewStartY(PDDocument document, PDPage page, PDRectangle box) throws IOException {
        PdfPageMetrics metrics = locatePdfPageMetrics(document, page);
        float candidate = metrics.lowestTextY() > 0f ? metrics.lowestTextY() - 34f : Math.min(240f, box.getHeight() * 0.30f);
        float maxY = box.getHeight() - 72f;
        float minY = 96f;
        return Math.max(minY, Math.min(maxY, candidate));
    }

    // 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲
    //  Check-mark image generation (for DOCX)
    // 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲

    /**
     * Generate a red handwriting-style check-mark as a PNG image.
     * This is cached because every annotation needs the same base image.
     */
    private byte[] getCheckMarkPng() {
        if (checkMarkPngBytes != null) {
            return checkMarkPngBytes;
        }
        synchronized (this) {
            if (checkMarkPngBytes != null) {
                return checkMarkPngBytes;
            }
            try {
                checkMarkPngBytes = renderCheckMarkImage();
            } catch (Exception e) {
                return null;
            }
        }
        return checkMarkPngBytes;
    }

        private byte[] renderCheckMarkImage() throws IOException {
        int w = 136, h = 108;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        g.setColor(RED_COLOR);
        g.setStroke(new BasicStroke(8.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        Path2D.Float path = new Path2D.Float();
        path.moveTo(14, 54);
        path.curveTo(24, 50, 34, 60, 44, 74);
        path.curveTo(58, 54, 76, 28, 112, 8);

        g.draw(path);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }

    // 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲
    //  PDF text utilities
    // 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲

    private PdfTextAnchor locatePdfKeyword(PDDocument document, int pageNumber, List<String> keywords) throws IOException {
        PdfKeywordLocator locator = new PdfKeywordLocator(pageNumber, keywords);
        locator.getText(document);
        return locator.anchor();
    }

    private PdfPageMetrics locatePdfPageMetrics(PDDocument document, PDPage page) throws IOException {
        int pageNumber = 1;
        int index = 0;
        for (PDPage candidate : document.getPages()) {
            index++;
            if (candidate == page) {
                pageNumber = index;
                break;
            }
        }
        PdfPageMetricsLocator locator = new PdfPageMetricsLocator(pageNumber, page.getMediaBox());
        locator.getText(document);
        return locator.metrics();
    }

    private void drawPdfText(PDPageContentStream stream, PDFont font, float fontSize, float x, float y, String text)
            throws IOException {
        stream.beginText();
        stream.setFont(font, fontSize);
        stream.newLineAtOffset(x, y);
        stream.showText(sanitizeForPdfFont(font, text));
        stream.endText();
    }

    private List<String> wrapPdfText(PDFont font, String text, float fontSize, float maxWidth) throws IOException {
        if (safeText(text).isBlank()) {
            return List.of("");
        }
        List<String> lines = new ArrayList<>();
        for (String rawLine : safeText(text).split("\n")) {
            if (rawLine.isBlank()) {
                lines.add("");
                continue;
            }
            StringBuilder current = new StringBuilder();
            for (char ch : rawLine.toCharArray()) {
                String next = current + String.valueOf(ch);
                float width;
                try {
                    width = font.getStringWidth(next) / 1000f * fontSize;
                } catch (Exception e) {
                    current.append(ch);
                    continue;
                }
                if (width > maxWidth && current.length() > 0) {
                    lines.add(current.toString());
                    current = new StringBuilder().append(ch);
                } else {
                    current.append(ch);
                }
            }
            if (current.length() > 0) {
                lines.add(current.toString());
            }
        }
        return lines;
    }

    private FontSelection loadPdfFont(PDDocument document) throws IOException {
        List<Path> candidates = List.of(
                Path.of("C:\\Windows\\Fonts\\STXINGKA.TTF"),
                Path.of("C:\\Windows\\Fonts\\simkai.ttf"),
                Path.of("C:\\Windows\\Fonts\\KAIU.TTF"),
                Path.of("C:\\Windows\\Fonts\\msyh.ttf"),
                Path.of("/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc"),
                Path.of("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc")
        );
        for (Path path : candidates) {
            if (!Files.exists(path)) {
                continue;
            }
            try (var inputStream = Files.newInputStream(path)) {
                return new FontSelection(PDType0Font.load(document, inputStream, true), true);
            } catch (Exception ignored) {
            }
        }
        return new FontSelection(PDType1Font.HELVETICA_BOLD, false);
    }

    private String normalizeForFont(FontSelection fontSelection, String preferred, String fallback) {
        String candidate = fontSelection.supportsChinese() ? preferred : fallback;
        String normalized = sanitizeForPdfFont(fontSelection.font(), candidate);
        if (!normalized.isBlank()) {
            return normalized;
        }
        return sanitizeForPdfFont(fontSelection.font(), fallback);
    }

    private String sanitizeForPdfFont(PDFont font, String text) {
        String value = safeText(text);
        if (value.isBlank()) {
            return value;
        }
        try {
            font.encode(value);
            return value;
        } catch (Exception ignored) {
        }

        StringBuilder sanitized = new StringBuilder();
        for (char ch : value.toCharArray()) {
            if (Character.isWhitespace(ch)) {
                sanitized.append(ch);
                continue;
            }
            try {
                font.encode(String.valueOf(ch));
                sanitized.append(ch);
            } catch (Exception ignored) {
                sanitized.append(' ');
            }
        }
        return sanitized.toString().replaceAll(" {2,}", " ").trim();
    }

    // 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲
    //  Shared utilities
    // 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲

    private boolean containsScoreKeyword(String text) {
        String lower = safeText(text).toLowerCase(Locale.ROOT);
        for (String keyword : SCORE_KEYWORDS) {
            if (lower.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

        private List<String> buildReviewLines(String teacherComment, List<String> dimensionComments) {
        List<String> lines = new ArrayList<>();
        if (teacherComment != null && !teacherComment.isBlank()) {
            for (String line : teacherComment.replace("\r", "").split("\n")) {
                String trimmed = line.trim();
                if (!trimmed.isBlank()) {
                    lines.add(trimmed);
                }
            }
        }
        if (lines.isEmpty()) {
            lines.add("批阅完成，请继续围绕实验任务、原理理解、结果分析与总结反思进一步完善报告。");
        }
        return lines.size() > 24 ? lines.subList(0, 24) : lines;
    }

    private String resolveTeacherSignature(String teacherSignature) {
        String normalized = safeText(teacherSignature).trim();
        return normalized.isBlank() ? "任课教师" : normalized;
    }

    private List<Integer> pickIndices(int size, int desiredCount, Random random) {
        if (size <= 0 || desiredCount <= 0) {
            return List.of();
        }
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            indices.add(i);
        }
        Collections.shuffle(indices, random);
        return indices.stream()
                .limit(Math.min(size, desiredCount))
                .sorted()
                .toList();
    }

    private Random buildRandom(String studentName, BigDecimal totalScore) {
        return new Random(Objects.hash(studentName, formatScore(totalScore)));
    }

    private boolean isPdf(byte[] sourceBytes) {
        if (sourceBytes.length < PDF_MAGIC.length) {
            return false;
        }
        for (int i = 0; i < PDF_MAGIC.length; i++) {
            if (sourceBytes[i] != PDF_MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    private String formatScore(BigDecimal totalScore) {
        if (totalScore == null) {
            return "待评";
        }
        return totalScore.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    // 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲
    //  Records & inner classes
    // 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲

    public record RenderedReport(String fileType, String extension, String contentType, byte[] bytes) {}

    private record FontSelection(PDFont font, boolean supportsChinese) {}

    private record StyledLine(String text, float fontSize) {}

    private record PdfTextAnchor(float endX, float yDirAdj) {}

    private record PdfPageMetrics(float lowestTextY) {}

    private static final class PdfKeywordLocator extends PDFTextStripper {
        private final List<String> keywords;
        private PdfTextAnchor anchor;

        private PdfKeywordLocator(int pageNumber, List<String> keywords) throws IOException {
            this.keywords = keywords;
            setStartPage(pageNumber);
            setEndPage(pageNumber);
            setSortByPosition(true);
        }

        @Override
        protected void writeString(String text, List<TextPosition> positions) throws IOException {
            if (anchor != null || text == null || positions == null || positions.isEmpty()) {
                return;
            }
            String lower = text.toLowerCase(Locale.ROOT);
            for (String keyword : keywords) {
                int start = lower.indexOf(keyword.toLowerCase(Locale.ROOT));
                if (start < 0 || start >= positions.size()) {
                    continue;
                }
                int end = Math.min(positions.size() - 1, start + keyword.length() - 1);
                TextPosition endPos = positions.get(end);
                anchor = new PdfTextAnchor(endPos.getXDirAdj() + endPos.getWidthDirAdj(), endPos.getYDirAdj());
                return;
            }
        }

        private PdfTextAnchor anchor() {
            return anchor;
        }
    }

    private static final class PdfPageMetricsLocator extends PDFTextStripper {
        private final PDRectangle box;
        private float lowestTextY = -1f;

        private PdfPageMetricsLocator(int pageNumber, PDRectangle box) throws IOException {
            this.box = box;
            setStartPage(pageNumber);
            setEndPage(pageNumber);
            setSortByPosition(true);
        }

        @Override
        protected void writeString(String text, List<TextPosition> positions) throws IOException {
            if (positions == null || positions.isEmpty() || text == null || text.isBlank()) {
                return;
            }
            for (TextPosition position : positions) {
                float pageY = box.getHeight() - position.getYDirAdj();
                if (lowestTextY < 0f || pageY < lowestTextY) {
                    lowestTextY = pageY;
                }
            }
        }

        private PdfPageMetrics metrics() {
            return new PdfPageMetrics(lowestTextY);
        }
    }
}

