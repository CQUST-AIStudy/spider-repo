package com.cqust.ai_server.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@RestController
public class ConvertController {

    // LibreOffice 在 Windows 系统中的安装路径
    private static final String LIBREOFFICE_WIN_PATH = "C:\\Program Files\\LibreOffice\\program\\soffice.exe";

    // 备用路径，如果安装在不同位置可以尝试这些路径
    private static final String[] ALTERNATIVE_WIN_PATHS = {
            "C:\\Program Files (x86)\\LibreOffice\\program\\soffice.exe",
            "C:\\Program Files\\LibreOffice 7\\program\\soffice.exe",
            "C:\\Program Files\\LibreOffice 7.5\\program\\soffice.exe",
            "C:\\Program Files\\LibreOffice 7.6\\program\\soffice.exe"
    };

    @PostMapping("/api/convert-to-pdf")
    public ResponseEntity<byte[]> convertToPdf(@RequestParam("wordFile") MultipartFile wordFile) {
        try {
            // 创建临时目录存储文件
            String tempDir = System.getProperty("java.io.tmpdir");
            String uniqueId = UUID.randomUUID().toString();

            // 保存Word文档
            Path wordPath = Paths.get(tempDir, uniqueId + ".docx");
            wordFile.transferTo(wordPath.toFile());

            // PDF输出路径基于输入文件名
            String pdfFileName = uniqueId + ".pdf";
            Path pdfPath = Paths.get(tempDir, pdfFileName);

            // 确定LibreOffice可执行文件路径
            String officePath = getLibreOfficePath();
            if (officePath == null) {
                throw new RuntimeException("无法找到LibreOffice可执行文件。请确保已安装LibreOffice并设置正确路径。");
            }

            System.out.println("使用LibreOffice路径: " + officePath);
            System.out.println("输入文件: " + wordPath);
            System.out.println("输出目录: " + tempDir);

            // 使用LibreOffice进行转换
            ProcessBuilder pb = new ProcessBuilder(
                    officePath,
                    "--headless",
                    "--convert-to", "pdf",
                    "--outdir", tempDir,
                    wordPath.toString()
            );

            // 设置工作目录
            pb.directory(new File(tempDir));

            // 重定向错误流，合并到标准输出
            pb.redirectErrorStream(true);

            // 启动进程
            Process process = pb.start();

            // 读取进程输出
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append(System.lineSeparator());
                }
            }

            // 等待进程结束
            int exitCode = process.waitFor();
            System.out.println("LibreOffice执行结果: " + exitCode);
            System.out.println("输出: " + output.toString());

            if (exitCode != 0) {
                throw new RuntimeException("转换失败：LibreOffice返回错误码 " + exitCode + "\n" + output.toString());
            }

            // 生成的PDF文件名可能与docx文件名相同，但扩展名为.pdf
            String expectedPdfName = wordPath.getFileName().toString().replace(".docx", ".pdf");
            Path expectedPdfPath = Paths.get(tempDir, expectedPdfName);

            // 尝试查找生成的PDF文件
            Path actualPdfPath = findGeneratedPdfFile(tempDir, uniqueId, expectedPdfName);
            if (actualPdfPath == null) {
                throw new RuntimeException("无法找到生成的PDF文件。转换可能失败。\n" + output.toString());
            }

            // 读取生成的PDF
            byte[] pdfContent = Files.readAllBytes(actualPdfPath);

            // 清理临时文件
            try {
                Files.deleteIfExists(wordPath);
                Files.deleteIfExists(actualPdfPath);
            } catch (IOException e) {
                // 忽略清理错误
                System.out.println("清理临时文件时出错: " + e.getMessage());
            }

            // 设置响应头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "report.pdf");

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(pdfContent);

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * 获取LibreOffice可执行文件路径
     */
    private String getLibreOfficePath() {
        String osName = System.getProperty("os.name").toLowerCase();

        if (osName.contains("win")) {
            // Windows系统
            File file = new File(LIBREOFFICE_WIN_PATH);
            if (file.exists() && file.canExecute()) {
                return LIBREOFFICE_WIN_PATH;
            }

            // 尝试备用路径
            for (String path : ALTERNATIVE_WIN_PATHS) {
                file = new File(path);
                if (file.exists() && file.canExecute()) {
                    return path;
                }
            }

            // 如果上述路径都不存在，可能需要用户手动指定
            return null;
        } else {
            // Linux/Mac系统，通常可以直接使用soffice命令
            return "soffice";
        }
    }

    /**
     * 查找生成的PDF文件
     */
    private Path findGeneratedPdfFile(String directory, String uniqueId, String expectedFileName) {
        // 首先检查预期的文件名
        Path expectedPath = Paths.get(directory, expectedFileName);
        if (Files.exists(expectedPath)) {
            return expectedPath;
        }

        // 然后在目录中搜索匹配的PDF文件
        File dir = new File(directory);
        File[] files = dir.listFiles((d, name) ->
                (name.startsWith(uniqueId) || name.contains(uniqueId.substring(0, 5))) &&
                        name.toLowerCase().endsWith(".pdf"));

        if (files != null && files.length > 0) {
            return files[0].toPath();
        }

        return null;
    }
}
