package com.example.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 文档模型
 * 对应 Python 中的 document 结构
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Document {
    
    /**
     * 文档元信息
     */
    private MetaInfo metaInfo;
    
    /**
     * 文档内容
     */
    private Content content;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetaInfo {
        /**
         * 文件的 SHA1 哈希值
         */
        private String sha1;
        
        /**
         * 公司名称
         */
        private String companyName;
        
        /**
         * 文件名
         */
        private String fileName;
        
        /**
         * 其他元数据
         */
        private Map<String, Object> metadata;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Content {
        /**
         * 分块列表
         */
        private List<Chunk> chunks;
        
        /**
         * 页面列表
         */
        private List<Page> pages;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Page {
        /**
         * 页码 (从1开始)
         */
        private int page;
        
        /**
         * 页面文本内容
         */
        private String text;
    }
}
