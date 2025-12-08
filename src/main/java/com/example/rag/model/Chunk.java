package com.example.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文本分块模型
 * 对应 Python 中的 chunk 结构
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Chunk {
    
    /**
     * 分块ID
     */
    private int id;
    
    /**
     * 分块类型 (content, serialized_table)
     */
    private String type;
    
    /**
     * 页码
     */
    private int page;
    
    /**
     * 分块文本内容
     */
    private String text;
    
    /**
     * Token 数量
     */
    private int lengthTokens;
    
    /**
     * 表格ID (仅当 type='serialized_table' 时)
     */
    private String tableId;
}
