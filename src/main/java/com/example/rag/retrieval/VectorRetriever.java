package com.example.rag.retrieval;

import com.example.rag.embedding.PGVectorStore;
import com.example.rag.model.RetrievalResult;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 向量检索器
 * 对应 Python 中的 VectorRetriever 类
 * 
 * 核心功能:
 * 1. 基于向量相似度检索相关文档
 * 2. 支持公司名过滤
 * 3. 返回格式化的检索结果
 */
@Slf4j
@Service
public class VectorRetriever {
    
    @Autowired
    private PGVectorStore pgVectorStore;
    
    @Value("${rag.retrieval.top-k:10}")
    private int defaultTopK;
    
    @Value("${rag.retrieval.similarity-threshold:0.7}")
    private double similarityThreshold;
    
    /**
     * 基于公司名检索相关文档
     * 
     * @param companyName 公司名称
     * @param query 查询文本
     * @param topN 返回的结果数量
     * @return 检索结果列表
     */
    public List<RetrievalResult> retrieveByCompanyName(
            String companyName, 
            String query, 
            int topN) {
        
        log.info("开始检索，公司: {}, 查询: {}, topN: {}", companyName, query, topN);
        
        // 从向量库检索
        List<EmbeddingMatch<TextSegment>> matches = 
            pgVectorStore.retrieve(query, companyName, topN);
        
        // 转换为我们的 RetrievalResult 格式
        List<RetrievalResult> results = matches.stream()
            .map(this::convertToRetrievalResult)
            .collect(Collectors.toList());
        
        log.info("检索完成，返回 {} 个结果", results.size());
        
        return results;
    }
    
    /**
     * 将 LangChain4j 的 EmbeddingMatch 转换为我们的 RetrievalResult
     */
    private RetrievalResult convertToRetrievalResult(EmbeddingMatch<TextSegment> match) {
        TextSegment segment = match.embedded();
        
        // 提取元数据
        Metadata metadata = segment.metadata();
        // 尝试从metadata中获取page值
        String pageStr = null;
        if (metadata != null) {
            try {
                // 尝试使用getString方法
                try {
                    java.lang.reflect.Method getStringMethod = metadata.getClass().getMethod("getString", String.class);
                    pageStr = (String) getStringMethod.invoke(metadata, "page");
                } catch (NoSuchMethodException e1) {
                    // 如果getString不存在，尝试toMap
                    try {
                        java.lang.reflect.Method toMapMethod = metadata.getClass().getMethod("toMap");
                        java.util.Map<?, ?> map = (java.util.Map<?, ?>) toMapMethod.invoke(metadata);
                        if (map != null) {
                            Object pageObj = map.get("page");
                            pageStr = pageObj != null ? pageObj.toString() : null;
                        }
                    } catch (NoSuchMethodException e2) {
                        // 如果toMap也不存在，尝试直接作为Map
                        if (metadata instanceof java.util.Map) {
                            Object pageObj = ((java.util.Map<?, ?>) metadata).get("page");
                            pageStr = pageObj != null ? pageObj.toString() : null;
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("无法从metadata中获取page值", e);
            }
        }
        int page = 0;
        if (pageStr != null && !pageStr.trim().isEmpty()) {
            try {
                page = Integer.parseInt(pageStr);
            } catch (NumberFormatException e) {
                log.warn("无法解析page值: {}, 使用默认值0", pageStr);
            }
        }
        String text = segment.text();
        double score = match.score();
        
        return RetrievalResult.builder()
            .distance(score)
            .page(page)
            .text(text)
            .build();
    }
    
    /**
     * 检索所有页面 (用于全文上下文)
     * 
     * @param companyName 公司名称
     * @return 所有页面的检索结果
     */
    public List<RetrievalResult> retrieveAll(String companyName) {
        log.info("检索公司所有内容: {}", companyName);
        
        // 使用一个通用查询，返回大量结果
        return retrieveByCompanyName(companyName, companyName, 1000);
    }
}
