package com.example.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 答案模型
 * 对应 Python 中的 answer_dict 结构
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Answer {
    
    /**
     * 分步推理过程
     */
    private String stepByStepAnalysis;
    
    /**
     * 推理摘要
     */
    private String reasoningSummary;
    
    /**
     * 相关页码列表
     */
    private List<Integer> relevantPages;
    
    /**
     * 最终答案 (可能是字符串、数字、布尔值等)
     */
    private Object finalAnswer;
    
    /**
     * 引用列表
     */
    private List<Reference> references;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Reference {
        /**
         * PDF 的 SHA1 哈希值
         */
        private String pdfSha1;
        
        /**
         * 页面索引 (0-based)
         */
        private int pageIndex;
    }
}
