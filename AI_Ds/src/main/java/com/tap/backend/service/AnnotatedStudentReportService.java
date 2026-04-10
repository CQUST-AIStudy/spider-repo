package com.tap.backend.service;

import java.awt.Color;
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
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.util.Matrix;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;

@Service
public class AnnotatedStudentReportService {
    public static final String FILE_TYPE_ANNOTATED_DOCX = "annodoc";
    public static final String FILE_TYPE_ANNOTATED_PDF = "annopdf";

    private static final String RED_HEX = "D62828";
    private static final Color RED_COLOR = new Color(214, 40, 40);
    private static final String HANDWRITING_FONT = "华文行楷";
    private static final String HANDWRITING_FALLBACK = "楷体";
    private static final String CHECK_MARK = "√";
    private static final byte[] PDF_MAGIC = {0x25, 0x50, 0x44, 0x46};
    private static final List<String> SCORE_KEYWORDS = List.of("得分", "分数", "成绩", "评分", "score");

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

    private RenderedReport renderDocx(byte[] sourceBytes,
                                      String studentName,
                                      BigDecimal totalScore,
                                      String teacherComment,
                                      List<String> dimensionComments) throws IOException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(sourceBytes));
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Random random = buildRandom(studentName, totalScore);
            List<XWPFParagraph> paragraphs = collectDocxParagraphs(document);

            insertDocxScoreInFrontMatter(document, paragraphs, totalScore);
            appendRandomDocxCheckMarks(paragraphs, random);
            appendDocxReviewBlock(document, teacherComment, dimensionComments);

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

    private void insertDocxScoreInFrontMatter(XWPFDocument document,
                                              List<XWPFParagraph> paragraphs,
                                              BigDecimal totalScore) {
        String scoreText = " AI评分：" + formatScore(totalScore) + "分";
        int inspected = 0;
        for (XWPFParagraph paragraph : paragraphs) {
            String text = safeText(paragraph.getText());
            if (text.isBlank()) {
                continue;
            }
            inspected++;
            if (containsScoreKeyword(text)) {
                appendDocxRun(paragraph, scoreText, 18, true);
                return;
            }
            if (inspected >= 24) {
                break;
            }
        }

        XWPFParagraph fallback = paragraphs.stream()
                .filter(paragraph -> !safeText(paragraph.getText()).isBlank())
                .findFirst()
                .orElseGet(document::createParagraph);
        appendDocxRun(fallback, "  " + scoreText.trim(), 18, true);
    }

    private void appendRandomDocxCheckMarks(List<XWPFParagraph> paragraphs, Random random) {
        List<XWPFParagraph> candidates = paragraphs.stream()
                .filter(paragraph -> !safeText(paragraph.getText()).isBlank())
                .toList();
        int desired = Math.min(6, Math.max(3, candidates.size() / 7));
        for (Integer index : pickIndices(candidates.size(), desired, random)) {
            XWPFParagraph paragraph = candidates.get(index);
            appendDocxRun(paragraph, "  " + CHECK_MARK, 28, true);
        }
    }

    private void appendDocxReviewBlock(XWPFDocument document,
                                       String teacherComment,
                                       List<String> dimensionComments) {
        XWPFParagraph spacer = document.createParagraph();
        spacer.setSpacingBefore(320);

        XWPFParagraph title = document.createParagraph();
        title.setAlignment(ParagraphAlignment.LEFT);
        XWPFRun titleRun = title.createRun();
        styleDocxRun(titleRun, 18, true);
        titleRun.setText("教师评语");

        for (String line : buildReviewLines(teacherComment, dimensionComments)) {
            XWPFParagraph paragraph = document.createParagraph();
            XWPFRun run = paragraph.createRun();
            styleDocxRun(run, 14, false);
            run.setText(line);
        }
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
    }

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

            drawPdfScoreOnFirstPage(document, pages.get(0), fontSelection, totalScore);
            appendRandomPdfCheckMarks(document, pages, fontSelection, random);
            drawPdfReviewOnLastPage(document, pages.get(pages.size() - 1), fontSelection, teacherComment, dimensionComments);

            document.save(outputStream);
            return new RenderedReport(FILE_TYPE_ANNOTATED_PDF, ".pdf", "application/pdf", outputStream.toByteArray());
        }
    }

    private void drawPdfScoreOnFirstPage(PDDocument document,
                                         PDPage page,
                                         FontSelection fontSelection,
                                         BigDecimal totalScore) throws IOException {
        String label = normalizeForFont(fontSelection, "AI评分：" + formatScore(totalScore) + "分",
                "AI Score: " + formatScore(totalScore));
        PdfTextAnchor anchor = locatePdfKeyword(document, 1, SCORE_KEYWORDS);
        PDRectangle box = page.getMediaBox();
        float x = anchor != null ? Math.min(box.getWidth() - 170f, anchor.endX() + 10f) : box.getWidth() - 170f;
        float y = anchor != null ? Math.max(48f, box.getHeight() - anchor.yDirAdj() - 4f) : box.getHeight() - 52f;

        try (PDPageContentStream stream = new PDPageContentStream(document, page, AppendMode.APPEND, true, true)) {
            stream.setNonStrokingColor(RED_COLOR);
            drawPdfText(stream, fontSelection.font(), 18f, x, y, label);
        }
    }

    private void appendRandomPdfCheckMarks(PDDocument document,
                                           List<PDPage> pages,
                                           FontSelection fontSelection,
                                           Random random) throws IOException {
        int desired = Math.min(5, Math.max(2, pages.size() + 1));
        for (Integer pageIndex : pickIndices(pages.size(), desired, random)) {
            PDPage page = pages.get(pageIndex);
            PDRectangle box = page.getMediaBox();
            float x = 42f + random.nextFloat() * Math.max(60f, box.getWidth() - 160f);
            float y = 90f + random.nextFloat() * Math.max(120f, box.getHeight() - 220f);
            float angle = (float) Math.toRadians(-18 + random.nextInt(37));

            try (PDPageContentStream stream = new PDPageContentStream(document, page, AppendMode.APPEND, true, true)) {
                stream.beginText();
                stream.setNonStrokingColor(RED_COLOR);
                stream.setFont(fontSelection.font(), 34f);
                stream.setTextMatrix(Matrix.getRotateInstance(angle, x, y));
                stream.showText(normalizeForFont(fontSelection, CHECK_MARK, "V"));
                stream.endText();
            }
        }
    }

    private void drawPdfReviewOnLastPage(PDDocument document,
                                         PDPage page,
                                         FontSelection fontSelection,
                                         String teacherComment,
                                         List<String> dimensionComments) throws IOException {
        PDRectangle box = page.getMediaBox();
        float margin = 52f;
        float maxWidth = box.getWidth() - margin * 2;
        float topY = Math.min(210f, Math.max(150f, box.getHeight() * 0.28f));
        List<String> lines = new ArrayList<>();
        lines.add(normalizeForFont(fontSelection, "教师评语", "Teacher Review"));
        lines.addAll(buildReviewLines(teacherComment, dimensionComments));

        try (PDPageContentStream stream = new PDPageContentStream(
                document,
                page,
                AppendMode.APPEND,
                true,
                true
        )) {
            stream.setNonStrokingColor(RED_COLOR);
            float y = topY;
            for (int i = 0; i < lines.size(); i++) {
                float fontSize = i == 0 ? 18f : 13f;
                for (String wrapped : wrapPdfText(fontSelection.font(), normalizeForFont(fontSelection, lines.get(i), lines.get(i)), fontSize, maxWidth)) {
                    if (y < 44f) {
                        return;
                    }
                    drawPdfText(stream, fontSelection.font(), fontSize, margin, y, wrapped);
                    y -= fontSize + 8f;
                }
                y -= 2f;
            }
        }
    }

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
        stream.showText(text);
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
                float width = font.getStringWidth(next) / 1000f * fontSize;
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
        return fontSelection.supportsChinese() ? preferred : fallback;
    }

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
                    lines.add("· " + trimmed);
                }
                if (lines.size() >= 8) {
                    break;
                }
            }
        }
        if (lines.isEmpty()) {
            lines.add("批阅完成，请继续完善实验过程说明、结果分析与总结。");
        }
        return lines.size() > 8 ? lines.subList(0, 8) : lines;
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

    public record RenderedReport(String fileType, String extension, String contentType, byte[] bytes) {}

    private record FontSelection(PDFont font, boolean supportsChinese) {}

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
