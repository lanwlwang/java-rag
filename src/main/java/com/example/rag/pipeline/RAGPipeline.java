package com.example.rag.pipeline;

import com.example.rag.document.PDFParser;
import com.example.rag.document.TextSplitter;
import com.example.rag.embedding.PGVectorStore;
import com.example.rag.model.Answer;
import com.example.rag.model.Document;
import com.example.rag.model.Question;
import com.example.rag.qa.QuestionProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * RAG 主流程编排
 * 对应 Python 中的 Pipeline 类
 * 
 * 核心功能:
 * 1. 解析 PDF 报告
 * 2. 文本分块
 * 3. 向量化并存储
 * 4. 处理问题并生成答案
 */
@Slf4j
@Service
public class RAGPipeline {
    
    @Autowired
    private PDFParser pdfParser;
    
    @Autowired
    private TextSplitter textSplitter;
    
    @Autowired
    private PGVectorStore pgVectorStore;
    
    @Autowired
    private QuestionProcessor questionProcessor;
    
    /**
     * 完整流程: 从 PDF 到向量库
     * 
     * @param pdfFile PDF 文件
     * @param companyName 公司名称
     */
    public void processPdfToVectorStore(File pdfFile, String companyName) {
        log.info("=== 开始处理 PDF 报告: {} ===", pdfFile.getName());
        
        try {
            // 1. 解析 PDF
            log.info("步骤 1/3: 解析 PDF...");
            Document document = pdfParser.parsePdf(pdfFile, companyName);
            
            // 2. 文本分块
            log.info("步骤 2/3: 文本分块...");
            document = textSplitter.splitDocument(document);
            
            // 3. 向量化并存储
            log.info("步骤 3/3: 向量化并存储...");
            pgVectorStore.storeDocument(document);
            
            log.info("=== PDF 处理完成 ===");
            
        } catch (Exception e) {
            log.error("处理 PDF 失败", e);
            throw new RuntimeException("处理 PDF 失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 批量处理目录下的所有 PDF
     * 
     * @param pdfDirectory PDF 目录路径
     */
    public void processPdfDirectory(String pdfDirectory) {
        log.info("=== 批量处理 PDF 目录: {} ===", pdfDirectory);
        
        try (Stream<Path> paths = Files.walk(Paths.get(pdfDirectory))) {
            List<File> pdfFiles = paths
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().toLowerCase().endsWith(".pdf"))
                .map(Path::toFile)
                .toList();
            
            log.info("找到 {} 个 PDF 文件", pdfFiles.size());
            
            for (File pdfFile : pdfFiles) {
                // 从文件名提取公司名 (简单处理,实际可能需要更复杂的逻辑)
                String companyName = extractCompanyNameFromFilename(pdfFile.getName());
                log.info("提取到的公司名： " + companyName);
                processPdfToVectorStore(pdfFile, companyName);
            }
            
            log.info("=== 批量处理完成 ===");
            
        } catch (IOException e) {
            log.error("批量处理失败", e);
            throw new RuntimeException("批量处理失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 处理单个问题（支持对话记忆）
     * 
     * @param questionText 问题文本
     * @param kind 问题类型
     * @param sessionId 会话 ID（可选）
     * @return 答案
     */
    public Answer answerQuestion(String questionText, String kind, String sessionId) {
        log.info("=== 回答问题: {}, 会话: {} ===", questionText, sessionId);
        
        Question question = Question.builder()
            .text(questionText)
            .kind(kind)
            .build();
        
        Answer answer = questionProcessor.processQuestion(question, sessionId);
        
        log.info("=== 问题回答完成 ===");
        
        return answer;
    }
    
    /**
     * 处理单个问题（无对话记忆，兼容旧接口）
     * 
     * @param questionText 问题文本
     * @param kind 问题类型
     * @return 答案
     */
    public Answer answerQuestion(String questionText, String kind) {
        return answerQuestion(questionText, kind, null);
    }
    
    /**
     * 批量处理问题列表
     * 
     * @param questions 问题列表
     * @return 答案列表
     */
    public List<Answer> answerQuestions(List<Question> questions) {
        log.info("=== 批量回答 {} 个问题 ===", questions.size());
        
        List<Answer> answers = new ArrayList<>();
        
        for (int i = 0; i < questions.size(); i++) {
            log.info("处理问题 {}/{}", i + 1, questions.size());
            
            Question question = questions.get(i);
            Answer answer = questionProcessor.processQuestion(question);
            answers.add(answer);
        }
        
        log.info("=== 批量回答完成 ===");
        
        return answers;
    }
    
    /**
     * 从文件名提取公司名
     * 示例: "中芯国际2024年年度报告.pdf" -> "中芯国际"
     * 
     * @param filename 文件名
     * @return 公司名
     */
    private String extractCompanyNameFromFilename(String filename) {
        // 移除扩展名
        filename = filename.replaceAll("\\.pdf$", "");
        
        // 简单处理: 移除年份、"年度报告"等字样
        filename = filename.replaceAll("\\d{4}年.*", "")
                          .replaceAll("年度报告", "")
                          .replaceAll("财报", "")
                          .replaceAll("【.*?】", "")
                          .trim();
        
        return filename;
    }
}
