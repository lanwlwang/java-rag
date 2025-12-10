package com.example.rag.controller;

import com.example.rag.chat.ChatMemoryService;
import com.example.rag.model.Answer;
import com.example.rag.pipeline.RAGPipeline;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

/**
 * RAG REST API 控制器
 * 
 * 提供 HTTP 接口:
 * 1. POST /upload-pdf - 上传 PDF 并处理
 * 2. POST /ask - 问答接口（支持对话记忆）
 * 3. POST /chat/new - 创建新会话
 * 4. DELETE /chat/{sessionId} - 删除会话
 * 5. DELETE /chat/{sessionId}/clear - 清空会话历史
 * 6. GET /health - 健康检查
 */
@Slf4j
@RestController
@RequestMapping("/")
public class RAGController {
    
    @Autowired
    private RAGPipeline ragPipeline;
    
    @Autowired
    private ChatMemoryService chatMemoryService;
    
    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("RAG Service is running!");
    }

    @PostMapping("/upload-pdf-by-path")
    public ResponseEntity<UploadResponse> uploadPdfByPath(
            @RequestParam("filePath") String filePath,
            @RequestParam("companyName") String companyName) {
        try {
            log.info("收到 PDF 上传请求: {}, 公司: {}", filePath, companyName);
            ragPipeline.processPdfToVectorStore(new File(filePath), companyName);
            return ResponseEntity.ok(new UploadResponse(
                    true,
                    "PDF 处理成功",
                    filePath
            ));
        }catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(new UploadResponse(
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
    public ResponseEntity<UploadResponse> uploadPdf(
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
            
            return ResponseEntity.ok(new UploadResponse(
                true,
                "PDF 处理成功",
                file.getOriginalFilename()
            ));
            
        } catch (Exception e) {
            log.error("处理 PDF 失败", e);
            return ResponseEntity.internalServerError()
                .body(new UploadResponse(
                    false,
                    "处理失败: " + e.getMessage(),
                    file.getOriginalFilename()
                ));
        }
    }
    
    /**
     * 问答接口（支持对话记忆）
     * 
     * @param request 问答请求
     * @return 答案（包含会话 ID）
     */
    @PostMapping("/ask")
    public ResponseEntity<AnswerResponse> ask(@RequestBody QuestionRequest request) {
        log.info("收到问答请求: {}, 会话: {}", request.getQuestion(), request.getSessionId());
        
        try {
            // 如果没有提供会话 ID，创建新会话
            String sessionId = request.getSessionId();
            if (sessionId == null || sessionId.isEmpty()) {
                sessionId = chatMemoryService.createSession();
                log.info("创建新会话: {}", sessionId);
            }
            
            // 处理问题（带会话 ID）
            Answer answer = ragPipeline.answerQuestion(
                request.getQuestion(),
                request.getKind(),
                sessionId
            );
            
            // 构建响应
            AnswerResponse response = new AnswerResponse();
            response.setSessionId(sessionId);
            response.setAnswer(answer);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("问答失败", e);
            
            Answer errorAnswer = Answer.builder()
                .stepByStepAnalysis("错误: " + e.getMessage())
                .reasoningSummary("处理失败")
                .finalAnswer("N/A")
                .build();
            
            AnswerResponse errorResponse = new AnswerResponse();
            errorResponse.setSessionId(request.getSessionId());
            errorResponse.setAnswer(errorAnswer);
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * 创建新会话
     * 
     * @return 会话 ID
     */
    @PostMapping("/chat/new")
    public ResponseEntity<Map<String, String>> createNewSession() {
        String sessionId = chatMemoryService.createSession();
        
        Map<String, String> response = new HashMap<>();
        response.put("sessionId", sessionId);
        response.put("message", "新会话创建成功");
        
        log.info("创建新会话: {}", sessionId);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 清空会话历史
     * 
     * @param sessionId 会话 ID
     * @return 操作结果
     */
    @DeleteMapping("/chat/{sessionId}/clear")
    public ResponseEntity<Map<String, String>> clearSession(@PathVariable String sessionId) {
        chatMemoryService.clearSession(sessionId);
        
        Map<String, String> response = new HashMap<>();
        response.put("sessionId", sessionId);
        response.put("message", "会话历史已清空");
        
        log.info("清空会话历史: {}", sessionId);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 删除会话
     * 
     * @param sessionId 会话 ID
     * @return 操作结果
     */
    @DeleteMapping("/chat/{sessionId}")
    public ResponseEntity<Map<String, String>> deleteSession(@PathVariable String sessionId) {
        chatMemoryService.deleteSession(sessionId);
        
        Map<String, String> response = new HashMap<>();
        response.put("sessionId", sessionId);
        response.put("message", "会话已删除");
        
        log.info("删除会话: {}", sessionId);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 获取会话统计信息
     * 
     * @return 统计信息
     */
    @GetMapping("/chat/stats")
    public ResponseEntity<Map<String, Object>> getChatStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("activeSessions", chatMemoryService.getActiveSessionCount());
        
        return ResponseEntity.ok(stats);
    }
    
    /**
     * 批量处理目录中的 PDF
     * 
     * @param request 批量处理请求
     * @return 处理结果
     */
    @PostMapping("/process-directory")
    public ResponseEntity<String> processDirectory(@RequestBody ProcessDirectoryRequest request) {
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
    
    // DTO 类
    
    @Data
    public static class QuestionRequest {
        private String question;
        private String kind = "string";  // 默认为 string 类型
        private String sessionId;  // 可选：会话 ID，如果不提供则创建新会话
    }
    
    @Data
    public static class AnswerResponse {
        private String sessionId;  // 会话 ID
        private Answer answer;  // 答案内容
    }
    
    @Data
    public static class UploadResponse {
        private boolean success;
        private String message;
        private String filename;
        
        public UploadResponse(boolean success, String message, String filename) {
            this.success = success;
            this.message = message;
            this.filename = filename;
        }
    }
    
    @Data
    public static class ProcessDirectoryRequest {
        private String directory;
    }
}
