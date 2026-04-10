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
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.springframework.stereotype.Service;

/**
 * Service that renders "red-pen teacher annotation" overlays onto student reports.
 * <p>
 * Supports both DOCX and PDF input files.  The output looks as if a teacher
 * physically marked the paper with a red pen: handwriting-style score on the
 * first page, scattered red check-marks (√) in the body, and a teacher review
 * block appended at the end.
 */
@Service
public class AnnotatedStudentReportService {
    public static final String FILE_TYPE_ANNOTATED_DOCX = "annodoc";
    public static final String FILE_TYPE_ANNOTATED_PDF = "annopdf";

    /* ── colour palette ── */
    private static final String RED_HEX = "D62828";
    private static final Color RED_COLOR = new Color(214, 40, 40);
    private static final Color RED_LIGHT = new Color(214, 40, 40, 180);

    /* ── font names ── */
    private static final String HANDWRITING_FONT = "华文行楷";
    private static final String HANDWRITING_FALLBACK = "楷体";

    /* ── marks ── */
    private static final String DOCX_CHECK_MARK = "√";
    private static final String PDF_CHECK_MARK = "V";
    private static final byte[] PDF_MAGIC = {0x25, 0x50, 0x44, 0x46};
    private static final List<String> SCORE_KEYWORDS = List.of("得分", "分数", "成绩", "评分", "score", "总分");

    /* ── check-mark image cache (thread-safe lazy init) ── */
    private volatile byte[] checkMarkPngBytes;

    // ════════════════════════════════════════════════════════════════════
    //  Public entry point
    // ════════════════════════════════════════════════════════════════════

    public RenderedReport render(String originalFilename,
                                 byte[] sourceBytes,
                                 String studentName,
                                 BigDecimal totalScore,
                                 String teacherComment,
                                 List<String> dimensionComments) {
        if (sourceBytes == null || sourceBytes.length == 0) {
            throw new IllegalArgumentException("Source report is empty");
        }

        String normalizedFilename = originalFilename == null ? "" : originalFilename.toLowerCase(Locale.ROOT);
        try {
            if (normalizedFilename.endsWith(".docx")) {
                return renderDocx(sourceBytes, studentName, totalScore, teacherComment, dimensionComments);
            }
            if (normalizedFilename.endsWith(".pdf") || isPdf(sourceBytes)) {
                return renderPdf(sourceBytes, studentName, totalScore, teacherComment, dimensionComments);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to annotate report", e);
        }
        throw new IllegalArgumentException("Only PDF and DOCX student reports are supported");
    }

    // ════════════════════════════════════════════════════════════════════
    //  DOCX rendering
    // ════════════════════════════════════════════════════════════════════

    private RenderedReport renderDocx(byte[] sourceBytes,
                                      String studentName,
                                      BigDecimal totalScore,
                                      String teacherComment,
                                      List<String> dimensionComments) throws IOException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(sourceBytes));
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Random random = buildRandom(studentName, totalScore);
            List<XWPFParagraph> paragraphs = collectDocxParagraphs(document);

            // 1) Red handwriting score on front page
            insertDocxScoreInFrontMatter(document, paragraphs, totalScore);

            // 2) Scattered red check-marks with handwriting-style images
            appendDocxCheckMarkImages(document, paragraphs, random);

            // 3) Teacher review block at the end
            appendDocxReviewBlock(document, teacherComment, dimensionComments, totalScore);

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
     * next to an existing "得分" / "成绩" keyword if found.
     */
    private void insertDocxScoreInFrontMatter(XWPFDocument document,
                                              List<XWPFParagraph> paragraphs,
                                              BigDecimal totalScore) {
        String scoreText = " AI评分：" + formatScore(totalScore) + " 分 ";
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
     * Insert handwriting-style check-mark images (red √) into random paragraphs.
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
                            Units.toEMU(28 + random.nextInt(8)),
                            Units.toEMU(24 + random.nextInt(6))
                    );
                } else {
                    // Text-based red check mark with slight variation
                    String mark = random.nextBoolean() ? "  " + DOCX_CHECK_MARK : " " + DOCX_CHECK_MARK + " ";
                    appendDocxRun(paragraph, mark, 26 + random.nextInt(8), true);
                }
            } catch (Exception ignored) {
                // Fallback to text check mark
                appendDocxRun(paragraph, "  " + DOCX_CHECK_MARK, 28, true);
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
                                       BigDecimal totalScore) {
        // Add a visual separator
        XWPFParagraph separator = document.createParagraph();
        separator.setSpacingBefore(300);
        separator.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun sepRun = separator.createRun();
        styleDocxRun(sepRun, 11, false);
        sepRun.setText("----------- 教师批阅 -----------");

        // Title line
        XWPFParagraph anchor = document.createParagraph();
        anchor.setAlignment(ParagraphAlignment.LEFT);
        anchor.setSpacingBefore(120);
        anchor.setSpacingAfter(40);

        XWPFRun titleRun = anchor.createRun();
        styleDocxRun(titleRun, 18, true);
        titleRun.setText("教师评语：");

        // Score summary line
        if (totalScore != null) {
            XWPFParagraph scorePara = document.createParagraph();
            scorePara.setSpacingBefore(60);
            scorePara.setSpacingAfter(40);
            XWPFRun scoreRun = scorePara.createRun();
            styleDocxRun(scoreRun, 16, true);
            scoreRun.setText("本次批改总分：" + formatScore(totalScore) + " 分");
        }

        // Review lines
        List<String> reviewLines = buildReviewLines(teacherComment, dimensionComments);
        for (String line : reviewLines) {
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.setSpacingBefore(20);
            paragraph.setSpacingAfter(20);
            XWPFRun run = paragraph.createRun();
            styleDocxRun(run, 12, false);
            run.setText(line);
        }

        // Signature line
        XWPFParagraph sigPara = document.createParagraph();
        sigPara.setAlignment(ParagraphAlignment.RIGHT);
        sigPara.setSpacingBefore(160);
        XWPFRun sigRun = sigPara.createRun();
        styleDocxRun(sigRun, 14, true);
        sigRun.setText("AI 教学助手  批阅");
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

    // ════════════════════════════════════════════════════════════════════
    //  PDF rendering
    // ════════════════════════════════════════════════════════════════════

    private RenderedReport renderPdf(byte[] sourceBytes,
                                     String studentName,
                                     BigDecimal totalScore,
                                     String teacherComment,
                                     List<String> dimensionComments) throws IOException {
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
                    teacherComment, dimensionComments, totalScore);

            document.save(outputStream);
            return new RenderedReport(FILE_TYPE_ANNOTATED_PDF, ".pdf", "application/pdf", outputStream.toByteArray());
        }
    }

    private void drawPdfScoreOnFirstPage(PDDocument document,
                                         PDPage page,
                                         FontSelection fontSelection,
                                         BigDecimal totalScore) throws IOException {
        String scoreLabel = normalizeForFont(fontSelection,
                "AI评分：" + formatScore(totalScore) + " 分",
                "AI Score: " + formatScore(totalScore));
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
            float size = 28f + random.nextInt(12);

            try (PDPageContentStream stream = new PDPageContentStream(document, page, AppendMode.APPEND, true, true)) {
                stream.beginText();
                stream.setNonStrokingColor(RED_COLOR);
                stream.setFont(fontSelection.font(), size);
                stream.setTextMatrix(Matrix.getRotateInstance(angle, x, y));
                stream.showText(PDF_CHECK_MARK);
                stream.endText();

                // Optionally draw a small tick-like stroke for more natural look
                if (random.nextInt(3) == 0) {
                    stream.setStrokingColor(RED_LIGHT);
                    stream.setLineWidth(1.5f);
                    float sx = x + size * 0.6f;
                    float sy = y + size * 0.2f;
                    stream.moveTo(sx, sy);
                    stream.lineTo(sx + 6f + random.nextFloat() * 4f, sy + 8f + random.nextFloat() * 4f);
                    stream.stroke();
                }
            }
        }
    }

    private void drawPdfReviewOnLastPage(PDDocument document,
                                         PDPage page,
                                         FontSelection fontSelection,
                                         String teacherComment,
                                         List<String> dimensionComments,
                                         BigDecimal totalScore) throws IOException {
        PDRectangle box = page.getMediaBox();
        float margin = 44f;
        float maxWidth = box.getWidth() - margin * 2;

        List<StyledLine> styledLines = new ArrayList<>();

        // Separator
        styledLines.add(new StyledLine(normalizeForFont(fontSelection,
                "---- 教师批阅 ----", "---- Teacher Review ----"), 12f));

        // Title
        styledLines.add(new StyledLine(normalizeForFont(fontSelection, "教师评语", "Teacher Review"), 16f));

        // Score line
        if (totalScore != null) {
            styledLines.add(new StyledLine(normalizeForFont(fontSelection,
                    "本次批改总分：" + formatScore(totalScore) + " 分",
                    "Total Score: " + formatScore(totalScore)), 14f));
        }

        // Review content
        for (String line : buildReviewLines(teacherComment, dimensionComments)) {
            styledLines.add(new StyledLine(normalizeForFont(fontSelection, line, line), 11f));
        }

        // Signature
        styledLines.add(new StyledLine(normalizeForFont(fontSelection,
                "AI 教学助手  批阅", "AI Teaching Assistant"), 12f));

        float y = Math.min(240f, box.getHeight() * 0.30f);
        try (PDPageContentStream stream = new PDPageContentStream(document, page, AppendMode.APPEND, true, true)) {
            stream.setNonStrokingColor(RED_COLOR);

            // Draw a thin separator line
            stream.setStrokingColor(RED_LIGHT);
            stream.setLineWidth(0.8f);
            stream.moveTo(margin, y + 8f);
            stream.lineTo(box.getWidth() - margin, y + 8f);
            stream.stroke();

            y -= 4f;
            for (StyledLine styledLine : styledLines) {
                List<String> wrapped = wrapPdfText(
                        fontSelection.font(),
                        styledLine.text(),
                        styledLine.fontSize(),
                        maxWidth
                );
                for (String line : wrapped) {
                    if (y < 36f) {
                        return;
                    }
                    drawPdfText(stream, fontSelection.font(), styledLine.fontSize(), margin, y, line);
                    y -= styledLine.fontSize() + 6f;
                }
                y -= 4f;
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  Check-mark image generation (for DOCX)
    // ════════════════════════════════════════════════════════════════════

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
        int w = 48, h = 44;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        // Draw a hand-drawn style check mark
        g.setColor(RED_COLOR);
        g.setStroke(new BasicStroke(3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        Path2D.Float path = new Path2D.Float();
        path.moveTo(6, 24);
        path.curveTo(10, 26, 14, 32, 18, 38);
        path.curveTo(22, 34, 30, 18, 42, 6);

        g.draw(path);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }

    // ════════════════════════════════════════════════════════════════════
    //  PDF text utilities
    // ════════════════════════════════════════════════════════════════════

    private PdfTextAnchor locatePdfKeyword(PDDocument document, int pageNumber, List<String> keywords) throws IOException {
        PdfKeywordLocator locator = new PdfKeywordLocator(pageNumber, keywords);
        locator.getText(document);
        return locator.anchor();
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

    // ════════════════════════════════════════════════════════════════════
    //  Shared utilities
    // ════════════════════════════════════════════════════════════════════

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
        if (dimensionComments != null) {
            for (String comment : dimensionComments) {
                String trimmed = safeText(comment);
                if (!trimmed.isBlank()) {
                    lines.add("- " + trimmed);
                }
                if (lines.size() >= 12) {
                    break;
                }
            }
        }
        if (lines.isEmpty()) {
            lines.add("批阅完成，请继续完善实验过程说明、结果分析与总结。");
        }
        return lines.size() > 12 ? lines.subList(0, 12) : lines;
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

    // ════════════════════════════════════════════════════════════════════
    //  Records & inner classes
    // ════════════════════════════════════════════════════════════════════

    public record RenderedReport(String fileType, String extension, String contentType, byte[] bytes) {}

    private record FontSelection(PDFont font, boolean supportsChinese) {}

    private record StyledLine(String text, float fontSize) {}

    private record PdfTextAnchor(float endX, float yDirAdj) {}

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
}
