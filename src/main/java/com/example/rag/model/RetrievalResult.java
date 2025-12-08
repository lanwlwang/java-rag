package com.example.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 检索结果模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetrievalResult {
    
    /**
     * 向量距离/相似度分数
     */
    private double distance;
    
    /**
     * 页码
     */
    private int page;
    
    /**
     * 文本内容
     */
    private String text;
    
    /**
     * 重排分数 (可选)
     */
    private Double rerankScore;
}
