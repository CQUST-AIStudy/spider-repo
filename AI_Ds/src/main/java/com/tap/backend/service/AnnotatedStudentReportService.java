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
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Service;

@Service
public class AnnotatedStudentReportService {
    public static final String FILE_TYPE_ANNOTATED_DOCX = "annodoc";
    public static final String FILE_TYPE_ANNOTATED_PDF = "annopdf";

    private static final String RED_HEX = "D62828";
    private static final String HANDWRITING_FONT = "华文行楷";
    private static final byte[] PDF_MAGIC = {0x25, 0x50, 0x44, 0x46};

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

            List<XWPFParagraph> candidates = document.getParagraphs().stream()
                    .filter(paragraph -> paragraph.getText() != null && !paragraph.getText().trim().isBlank())
                    .toList();
            for (Integer index : pickIndices(candidates.size(), Math.min(4, Math.max(2, candidates.size() / 8)), random)) {
                XWPFParagraph paragraph = candidates.get(index);
                XWPFRun run = paragraph.createRun();
                run.setColor(RED_HEX);
                run.setBold(true);
                run.setFontFamily(HANDWRITING_FONT);
                run.setFontSize(22);
                run.setText("  √");
            }

            appendDocxReviewBlock(document, totalScore, teacherComment, dimensionComments);
            document.write(outputStream);
            return new RenderedReport(
                    FILE_TYPE_ANNOTATED_DOCX,
                    ".docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    outputStream.toByteArray()
            );
        }
    }

    private void appendDocxReviewBlock(XWPFDocument document,
                                       BigDecimal totalScore,
                                       String teacherComment,
                                       List<String> dimensionComments) {
        XWPFParagraph spacer = document.createParagraph();
        spacer.setSpacingBefore(320);

        XWPFParagraph title = document.createParagraph();
        title.setAlignment(ParagraphAlignment.LEFT);
        XWPFRun titleRun = title.createRun();
        titleRun.setColor(RED_HEX);
        titleRun.setBold(true);
        titleRun.setFontFamily(HANDWRITING_FONT);
        titleRun.setFontSize(18);
        titleRun.setText("教师批注");

        XWPFParagraph scoreParagraph = document.createParagraph();
        XWPFRun scoreRun = scoreParagraph.createRun();
        scoreRun.setColor(RED_HEX);
        scoreRun.setFontFamily(HANDWRITING_FONT);
        scoreRun.setFontSize(16);
        scoreRun.setBold(true);
        scoreRun.setText("得分：" + formatScore(totalScore));

        for (String line : buildReviewLines(teacherComment, dimensionComments)) {
            XWPFParagraph paragraph = document.createParagraph();
            XWPFRun run = paragraph.createRun();
            run.setColor(RED_HEX);
            run.setFontFamily(HANDWRITING_FONT);
            run.setFontSize(14);
            run.setText(line);
        }
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
            PDFont font = fontSelection.font();

            if (!document.getPages().iterator().hasNext()) {
                document.addPage(new PDPage(PDRectangle.A4));
            }

            List<PDPage> pages = new ArrayList<>();
            document.getPages().forEach(pages::add);

            PDPage firstPage = pages.get(0);
            try (PDPageContentStream stream = new PDPageContentStream(document, firstPage, AppendMode.APPEND, true, true)) {
                stream.setNonStrokingColor(Color.RED);
                drawText(stream, font, 18, firstPage.getMediaBox().getWidth() - 140, firstPage.getMediaBox().getHeight() - 48,
                        normalizeForFont(fontSelection, "得分：" + formatScore(totalScore), "Score: " + formatScore(totalScore)));
            }

            List<Integer> pageIndices = pickIndices(pages.size(), Math.min(3, pages.size()), random);
            for (Integer pageIndex : pageIndices) {
                PDPage page = pages.get(pageIndex);
                PDRectangle box = page.getMediaBox();
                float baseX = box.getWidth() - 78f - random.nextInt(18);
                float baseY = Math.max(90f, box.getHeight() * (0.45f + (random.nextFloat() * 0.3f)));
                try (PDPageContentStream stream = new PDPageContentStream(document, page, AppendMode.APPEND, true, true)) {
                    stream.setNonStrokingColor(Color.RED);
                    drawText(stream, font, 28, baseX, baseY, normalizeForFont(fontSelection, "√", "V"));
                }
            }

            appendPdfReviewPage(document, fontSelection, totalScore, teacherComment, dimensionComments);
            document.save(outputStream);
            return new RenderedReport(FILE_TYPE_ANNOTATED_PDF, ".pdf", "application/pdf", outputStream.toByteArray());
        }
    }

    private void appendPdfReviewPage(PDDocument document,
                                     FontSelection fontSelection,
                                     BigDecimal totalScore,
                                     String teacherComment,
                                     List<String> dimensionComments) throws IOException {
        PDFont font = fontSelection.font();
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);

        float margin = 54f;
        float width = page.getMediaBox().getWidth() - margin * 2;
        float y = page.getMediaBox().getHeight() - 56f;

        try (PDPageContentStream stream = new PDPageContentStream(document, page, AppendMode.OVERWRITE, true, true)) {
            stream.setNonStrokingColor(Color.RED);
            String title = normalizeForFont(fontSelection, "教师批注", "Teacher Review");
            drawText(stream, font, 20, margin, y, title);
            y -= 34f;

            String scoreLine = normalizeForFont(fontSelection, "得分：" + formatScore(totalScore), "Score: " + formatScore(totalScore));
            drawText(stream, font, 16, margin, y, scoreLine);
            y -= 28f;

            for (String rawLine : buildReviewLines(teacherComment, dimensionComments)) {
                for (String wrapped : wrapText(font, normalizeForFont(fontSelection, rawLine, rawLine), 12f, width)) {
                    if (y < 60f) {
                        page = new PDPage(PDRectangle.A4);
                        document.addPage(page);
                        y = page.getMediaBox().getHeight() - 56f;
                        stream.close();
                        throw new IllegalStateException("Unexpected PDF review overflow");
                    }
                    drawText(stream, font, 12, margin, y, wrapped);
                    y -= 20f;
                }
                y -= 4f;
            }
        } catch (IllegalStateException overflow) {
            // Review text in this workflow is intentionally short; keep a compact fallback instead of complex pagination.
            rewriteCompactPdfReviewPage(document, fontSelection, totalScore, teacherComment);
        }
    }

    private void rewriteCompactPdfReviewPage(PDDocument document,
                                             FontSelection fontSelection,
                                             BigDecimal totalScore,
                                             String teacherComment) throws IOException {
        PDPage lastPage = document.getPage(document.getNumberOfPages() - 1);
        PDFont font = fontSelection.font();
        try (PDPageContentStream stream = new PDPageContentStream(document, lastPage, AppendMode.OVERWRITE, true, true)) {
            stream.setNonStrokingColor(Color.RED);
            drawText(stream, font, 18, 54f, lastPage.getMediaBox().getHeight() - 56f,
                    normalizeForFont(fontSelection, "教师批注", "Teacher Review"));
            drawText(stream, font, 14, 54f, lastPage.getMediaBox().getHeight() - 88f,
                    normalizeForFont(fontSelection, "得分：" + formatScore(totalScore), "Score: " + formatScore(totalScore)));
            String compact = teacherComment == null || teacherComment.isBlank()
                    ? normalizeForFont(fontSelection, "已完成评分，请查看分项意见。", "Scored. Please see item comments.")
                    : teacherComment.trim();
            for (int i = 0; i < Math.min(10, wrapText(font, normalizeForFont(fontSelection, compact, compact), 12f, 480f).size()); i++) {
                drawText(stream, font, 12, 54f, lastPage.getMediaBox().getHeight() - 120f - i * 20f,
                        wrapText(font, normalizeForFont(fontSelection, compact, compact), 12f, 480f).get(i));
            }
        }
    }

    private void drawText(PDPageContentStream stream, PDFont font, float fontSize, float x, float y, String text)
            throws IOException {
        stream.beginText();
        stream.setFont(font, fontSize);
        stream.newLineAtOffset(x, y);
        stream.showText(text);
        stream.endText();
    }

    private List<String> wrapText(PDFont font, String text, float fontSize, float maxWidth) throws IOException {
        if (text == null || text.isBlank()) {
            return List.of("");
        }
        List<String> lines = new ArrayList<>();
        for (String rawLine : text.replace("\r", "").split("\n")) {
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
                Path.of("C:\\Windows\\Fonts\\simkai.ttf"),
                Path.of("C:\\Windows\\Fonts\\STXINGKA.TTF"),
                Path.of("C:\\Windows\\Fonts\\simhei.ttf"),
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
                if (comment == null) {
                    continue;
                }
                String trimmed = comment.trim();
                if (!trimmed.isBlank()) {
                    lines.add("• " + trimmed);
                }
                if (lines.size() >= 8) {
                    break;
                }
            }
        }

        if (lines.isEmpty()) {
            lines.add("请继续完善实验过程说明和结果分析。");
        }
        if (lines.size() > 8) {
            return lines.subList(0, 8);
        }
        return lines;
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

    public record RenderedReport(String fileType, String extension, String contentType, byte[] bytes) {}

    private record FontSelection(PDFont font, boolean supportsChinese) {}
}
