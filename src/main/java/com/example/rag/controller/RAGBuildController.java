package com.example.rag.controller;

import com.example.rag.document.FeishuWikiReader;
import com.example.rag.model.Document;
import com.example.rag.pipeline.RAGPipeline;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Slf4j
@RestController
public class RAGBuildController {

    @Autowired
    private FeishuWikiReader feishuWikiReader;
    @Autowired
    private RAGPipeline ragPipeline;

    @GetMapping("/upload-feishu-wiki")
    public void uploadFeishuWiki(@RequestParam String documentId) {

        try {
            System.out.println("=== 测试读取飞书文档 ===");
            System.out.println("文档 ID: " + documentId);

            com.alibaba.fastjson2.JSONObject wikiContent = feishuWikiReader.readWikiDocument(documentId);
            System.out.println("\n文档原始内容:");
            System.out.println(wikiContent.toJSONString());

            // 转换为 RAG Document
            Document document = feishuWikiReader.convertToDocument(documentId, "测试文档");
            System.out.println("\n转换后的文档:");
            System.out.println("- 页数: " + document.getContent().getPages().size());
            System.out.println("- 内容长度: " + document.getContent().getPages().get(0).getText().length() + " 字符");
            System.out.println("- 前 200 字符: " +
                    document.getContent().getPages().get(0).getText().substring(0,
                            Math.min(200, document.getContent().getPages().get(0).getText().length())));

        } catch (Exception e) {
            System.err.println("\n错误: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @PostMapping("/upload-pdf-by-path")
    public ResponseEntity<RAGController.UploadResponse> uploadPdfByPath(
            @RequestParam("filePath") String filePath,
            @RequestParam("companyName") String companyName) {
        try {
            log.info("收到 PDF 上传请求: {}, 公司: {}", filePath, companyName);
            ragPipeline.processPdfToVectorStore(new File(filePath), companyName);
            return ResponseEntity.ok(new RAGController.UploadResponse(
                    true,
                    "PDF 处理成功",
                    filePath
            ));
        }catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(new RAGController.UploadResponse(
                            false,
                            "处理失败: " + e.getMessage(),
                            filePath
                    ));
        }

    }

    /**
     * 上传 PDF 并处理
     *
     * @param file PDF 文件
     * @param companyName 公司名称
     * @return 处理结果
     */
    @PostMapping("/upload-pdf")
    public ResponseEntity<RAGController.UploadResponse> uploadPdf(
            @RequestParam("file") MultipartFile file,
            @RequestParam("companyName") String companyName) {

        log.info("收到 PDF 上传请求: {}, 公司: {}", file.getOriginalFilename(), companyName);

        try {
            // 保存临时文件，保存在磁盘上
            Path tempFile = Files.createTempFile("rag-upload-", ".pdf");
            Files.copy(file.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);

            // 处理 PDF
            ragPipeline.processPdfToVectorStore(tempFile.toFile(), companyName);

            // 删除临时文件
            Files.deleteIfExists(tempFile);

            return ResponseEntity.ok(new RAGController.UploadResponse(
                    true,
                    "PDF 处理成功",
                    file.getOriginalFilename()
            ));

        } catch (Exception e) {
            log.error("处理 PDF 失败", e);
            return ResponseEntity.internalServerError()
                    .body(new RAGController.UploadResponse(
                            false,
                            "处理失败: " + e.getMessage(),
                            file.getOriginalFilename()
                    ));
        }
    }

    /**
     * 批量处理目录中的 PDF
     *
     * @param request 批量处理请求
     * @return 处理结果
     */
    @PostMapping("/process-directory")
    public ResponseEntity<String> processDirectory(@RequestBody RAGController.ProcessDirectoryRequest request) {
        log.info("收到批量处理请求: {}", request.getDirectory());

        try {
            ragPipeline.processPdfDirectory(request.getDirectory());
            return ResponseEntity.ok("批量处理完成");

        } catch (Exception e) {
            log.error("批量处理失败", e);
            return ResponseEntity.internalServerError()
                    .body("批量处理失败: " + e.getMessage());
        }
    }
}
