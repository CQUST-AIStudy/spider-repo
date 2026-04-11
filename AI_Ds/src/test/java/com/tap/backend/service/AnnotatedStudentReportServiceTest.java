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
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
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
                "本次实验能够完成主要任务，知识掌握整体较好，但对关键现象的原因解释还可以更深入，报告结论也可以再凝练一些。",
                List.of("图表说明比较完整", "实验结论还可以再凝练一些"),
                "张老师"
        );

        assertEquals(AnnotatedStudentReportService.FILE_TYPE_ANNOTATED_DOCX, rendered.fileType());
        assertFalse(rendered.bytes().length == 0);

        try (XWPFDocument document = new XWPFDocument(new java.io.ByteArrayInputStream(rendered.bytes()))) {
            String text = new XWPFWordExtractor(document).getText();
            assertTrue(text.contains("86"));
            assertTrue(text.contains("教师评语"));
            assertTrue(text.contains("张老师"));
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
                "整体完成度较高，建议进一步把关键实验步骤、现象解释和结论依据写得更扎实。",
                List.of("结果截图齐全", "分析部分可以再展开"),
                "张老师"
        );

        assertEquals(AnnotatedStudentReportService.FILE_TYPE_ANNOTATED_PDF, rendered.fileType());
        assertTrue(rendered.bytes().length > source.length);

        try (PDDocument pdf = PDDocument.load(rendered.bytes())) {
            assertEquals(1, pdf.getNumberOfPages());
            String text = new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("91") || text.toLowerCase().contains("score"));
            assertTrue(text.contains("张老师") || text.toLowerCase().contains("teacher"));
        }
    }
}
