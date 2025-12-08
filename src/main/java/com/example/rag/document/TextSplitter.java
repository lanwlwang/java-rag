package com.example.rag.document;

import com.example.rag.model.Chunk;
import com.example.rag.model.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import dev.langchain4j.data.segment.TextSegment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本分块工具
 * 对应 Python 中的 TextSplitter 类
 * 
 * 核心功能:
 * 1. 将长文本按页或段落分块
 * 2. 控制每个分块的 token 数量
 * 3. 支持分块重叠以保持上下文连贯性
 */
@Slf4j
@Component
public class TextSplitter {
    
    @Value("${rag.document.chunk-size:300}")
    private int chunkSize;
    
    @Value("${rag.document.chunk-overlap:50}")
    private int chunkOverlap;
    
    @Value("${rag.document.max-chunk-size:500}")
    private int maxChunkSize;
    
    /**
     * 将文档按页分块
     * 
     * @param document 输入文档
     * @return 分块后的文档
     */
    public Document splitDocument(Document document) {
        log.info("开始分块文档: {}", document.getMetaInfo().getFileName());
        
        List<Chunk> chunks = new ArrayList<>();
        int chunkId = 0;
        
        // 遍历每一页
        for (Document.Page page : document.getContent().getPages()) {
            // 对每页文本进行分块
            List<Chunk> pageChunks = splitPage(page, chunkId);
            chunks.addAll(pageChunks);
            chunkId += pageChunks.size();
        }
        
        // 更新文档的分块列表
        document.getContent().setChunks(chunks);
        
        log.info("分块完成，共生成 {} 个分块", chunks.size());
        return document;
    }
    
    /**
     * 将单页文本分块
     * 
     * @param page 页面对象
     * @param startChunkId 起始分块ID
     * @return 分块列表
     */
    private List<Chunk> splitPage(Document.Page page, int startChunkId) {
        List<Chunk> chunks = new ArrayList<>();
        String text = page.getText();
        
        if (text == null || text.trim().isEmpty()) {
            return chunks;
        }
        
        // 使用 LangChain4j 的文档分割器
        DocumentSplitter splitter = new DocumentByParagraphSplitter(
            chunkSize,
            chunkOverlap
        );
        
        // 创建临时文档对象用于分割
        dev.langchain4j.data.document.Document tempDoc = 
            dev.langchain4j.data.document.Document.from(text);
        
        List<TextSegment> segments = splitter.split(tempDoc);
        
        // 转换为我们的 Chunk 模型
        int chunkId = startChunkId;
        for (TextSegment segment : segments) {
            Chunk chunk = Chunk.builder()
                .id(chunkId++)
                .type("content")
                .page(page.getPage())
                .text(segment.text())
                .lengthTokens(estimateTokenCount(segment.text()))
                .build();
            
            chunks.add(chunk);
        }
        
        return chunks;
    }
    
    /**
     * 简单估算 token 数量
     * 英文约 4 字符 = 1 token，中文约 1.5 字符 = 1 token
     * 
     * @param text 文本
     * @return 估算的 token 数
     */
    private int estimateTokenCount(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        
        // 统计中英文字符
        int chineseCount = 0;
        int englishCount = 0;
        
        for (char c : text.toCharArray()) {
            if (isChinese(c)) {
                chineseCount++;
            } else {
                englishCount++;
            }
        }
        
        // 估算公式: 中文/1.5 + 英文/4
        return (int) (chineseCount / 1.5 + englishCount / 4.0);
    }
    
    /**
     * 判断是否为中文字符
     */
    private boolean isChinese(char c) {
        return c >= 0x4E00 && c <= 0x9FA5;
    }
    
    /**
     * Markdown 文件分块
     * 按行数分块，适用于已经是 Markdown 格式的文档
     * 
     * @param markdownText Markdown 文本
     * @param linesPerChunk 每个分块的行数
     * @param overlapLines 重叠行数
     * @return 分块列表
     */
    public List<Chunk> splitMarkdown(String markdownText, int linesPerChunk, int overlapLines) {
        List<Chunk> chunks = new ArrayList<>();
        String[] lines = markdownText.split("\n");
        
        int chunkId = 0;
        int i = 0;
        
        while (i < lines.length) {
            int start = i;
            int end = Math.min(i + linesPerChunk, lines.length);
            
            // 提取当前分块的文本
            StringBuilder chunkText = new StringBuilder();
            for (int j = start; j < end; j++) {
                chunkText.append(lines[j]).append("\n");
            }
            
            Chunk chunk = Chunk.builder()
                .id(chunkId++)
                .type("content")
                .text(chunkText.toString())
                .lengthTokens(estimateTokenCount(chunkText.toString()))
                .build();
            
            chunks.add(chunk);
            
            // 移动到下一个分块，考虑重叠
            i += (linesPerChunk - overlapLines);
        }
        
        log.info("Markdown 分块完成，共 {} 个分块", chunks.size());
        return chunks;
    }
}
