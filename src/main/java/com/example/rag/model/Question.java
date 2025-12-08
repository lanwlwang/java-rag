package com.example.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 问题模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Question {
    
    /**
     * 问题文本
     */
    private String text;
    
    /**
     * 问题类型: string, number, boolean, names
     */
    private String kind;
    
    /**
     * 涉及的公司名称
     */
    private String companyName;
}
