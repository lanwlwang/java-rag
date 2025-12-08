package com.example.rag.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 向量化服务
 * 对应 Python 中的 VectorDBIngestor._get_embeddings
 * 
 * 核心功能:
 * 1. 将文本转换为向量
 * 2. 支持批量向量化
 * 3. 支持 OpenAI 和 DashScope
 */
@Slf4j
@Service
public class EmbeddingService {
    
    @Autowired
    private EmbeddingModel embeddingModel;
    
    /**
     * 将单个文本转换为向量
     * 
     * @param text 文本
     * @return 向量
     */
    public Embedding embedText(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("文本不能为空");
        }
        
        log.debug("生成文本向量，文本长度: {}", text.length());
        
        TextSegment segment = TextSegment.from(text);
        return embeddingModel.embed(segment).content();
    }
    
    /**
     * 批量将文本转换为向量
     * 
     * @param texts 文本列表
     * @return 向量列表
     */
    public List<Embedding> embedTexts(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return new ArrayList<>();
        }
        
        log.info("批量生成向量，文本数量: {}", texts.size());
        
        // 过滤空文本
        List<String> validTexts = texts.stream()
            .filter(text -> text != null && !text.trim().isEmpty())
            .toList();
        
        if (validTexts.isEmpty()) {
            log.warn("没有有效的文本需要向量化");
            return new ArrayList<>();
        }
        
        // 转换为 TextSegment
        List<TextSegment> segments = validTexts.stream()
            .map(TextSegment::from)
            .toList();
        
        // 批量生成向量
        var response = embeddingModel.embedAll(segments);
        
        log.info("批量向量化完成，生成向量数量: {}", response.content().size());
        
        return response.content();
    }
    
    /**
     * 获取向量维度
     * 
     * @return 维度
     */
    public int getDimension() {
        return embeddingModel.dimension();
    }
}
