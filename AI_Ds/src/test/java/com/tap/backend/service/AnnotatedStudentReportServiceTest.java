package com.tap.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.junit.jupiter.api.Test;

class AnnotatedStudentReportServiceTest {

    private final AnnotatedStudentReportService service = new AnnotatedStudentReportService();

    @Test
    void renderDocxAddsTeacherAnnotations() throws Exception {
        Path sample = Path.of("..", "2025520535-杨天-实验1.docx");
        byte[] source = Files.readAllBytes(sample);

        AnnotatedStudentReportService.RenderedReport rendered = service.render(
                "2025520535-杨天-实验1.docx",
                source,
                "杨天",
                new BigDecimal("86"),
                "实验过程完整，结果分析清楚，注意补充误差讨论。",
                List.of("图表说明比较完整", "实验结论还可以再凝练一点")
        );

        assertEquals(AnnotatedStudentReportService.FILE_TYPE_ANNOTATED_DOCX, rendered.fileType());
        assertFalse(rendered.bytes().length == 0);

        try (XWPFDocument document = new XWPFDocument(new java.io.ByteArrayInputStream(rendered.bytes()))) {
            String text = new XWPFWordExtractor(document).getText();
            assertTrue(text.contains("AI评分") || text.contains("86"));
            assertTrue(text.contains("教师评语"));
        }
    }

    @Test
    void renderPdfAddsScoreAndReview() throws Exception {
        byte[] source;
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            source = output.toByteArray();
        }

        AnnotatedStudentReportService.RenderedReport rendered = service.render(
                "sample.pdf",
                source,
                "测试同学",
                new BigDecimal("91"),
                "整体完成度高，注意把关键实验步骤再写得更清楚一些。",
                List.of("结果截图齐全", "分析部分可以再展开")
        );

        assertEquals(AnnotatedStudentReportService.FILE_TYPE_ANNOTATED_PDF, rendered.fileType());
        assertTrue(rendered.bytes().length > source.length);

        try (PDDocument pdf = PDDocument.load(rendered.bytes())) {
            assertEquals(1, pdf.getNumberOfPages());
            String text = new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("91") || text.toLowerCase().contains("score"));
        }
    }
}
