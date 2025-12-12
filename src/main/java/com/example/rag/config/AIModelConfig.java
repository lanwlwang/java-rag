package com.example.rag.config;

import com.example.rag.ai.DashScopeChatModel;
import com.example.rag.ai.DashScopeEmbeddingModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 模型配置类
 * 根据配置动态选择 OpenAI 或 DashScope
 */
@Slf4j
@Configuration
public class AIModelConfig {
    
    @Value("${rag.provider:openai}")
    private String provider;
    
    // OpenAI 配置
    @Value("${langchain4j.openai.api-key:}")
    private String openaiApiKey;
    
    @Value("${langchain4j.openai.chat-model.model-name:gpt-4o-mini}")
    private String openaiChatModel;
    
    @Value("${langchain4j.openai.embedding-model.model-name:text-embedding-3-large}")
    private String openaiEmbeddingModel;
    
    @Value("${langchain4j.openai.chat-model.temperature:0.5}")
    private double openaiTemperature;
    
    @Value("${langchain4j.openai.embedding-model.dimensions:1536}")
    private int openaiDimensions;
    
    // DashScope 配置
    @Value("${langchain4j.dashscope.api-key:}")
    private String dashscopeApiKey;
    
    @Value("${langchain4j.dashscope.chat-model.model-name:qwen-plus}")
    private String dashscopeChatModel;
    
    @Value("${langchain4j.dashscope.embedding-model.model-name:text-embedding-v3}")
    private String dashscopeEmbeddingModel;
    
    @Value("${langchain4j.dashscope.chat-model.temperature:0.5}")
    private double dashscopeTemperature;
    
    @Value("${langchain4j.dashscope.embedding-model.dimensions:1536}")
    private int dashscopeDimensions;
    
    /**
     * 根据配置创建 ChatLanguageModel
     */
    @Bean
    public ChatModel chatLanguageModel() {
        log.info("初始化 ChatLanguageModel, 提供商: {}", provider);
        
        if ("dashscope".equalsIgnoreCase(provider)) {
            return createDashScopeChatModel();
        } else {
            return createOpenAiChatModel();
        }
    }
    
    /**
     * 根据配置创建 EmbeddingModel
     */
    @Bean
    public EmbeddingModel embeddingModel() {
        log.info("初始化 EmbeddingModel, 提供商: {}", provider);
        
        if ("dashscope".equalsIgnoreCase(provider)) {
            return createDashScopeEmbeddingModel();
        } else {
            return createOpenAiEmbeddingModel();
        }
    }
    
    /**
     * 创建 OpenAI ChatModel
     */
    private ChatModel createOpenAiChatModel() {
        if (openaiApiKey == null || openaiApiKey.isEmpty()) {
            throw new IllegalStateException("OPENAI_API_KEY 未配置");
        }
        
        log.info("创建 OpenAI ChatModel: {}", openaiChatModel);
        
        return OpenAiChatModel.builder()
            .apiKey(openaiApiKey)
            .modelName(openaiChatModel)
            .temperature(openaiTemperature)
            .maxTokens(2000)
            .build();
    }
    
    /**
     * 创建 OpenAI EmbeddingModel
     */
    private EmbeddingModel createOpenAiEmbeddingModel() {
        if (openaiApiKey == null || openaiApiKey.isEmpty()) {
            throw new IllegalStateException("OPENAI_API_KEY 未配置");
        }
        
        log.info("创建 OpenAI EmbeddingModel: {}", openaiEmbeddingModel);
        
        return OpenAiEmbeddingModel.builder()
            .apiKey(openaiApiKey)
            .modelName(openaiEmbeddingModel)
            .dimensions(openaiDimensions)
            .build();
    }
    
    /**
     * 创建 DashScope ChatModel
     */
    private ChatModel createDashScopeChatModel() {
        if (dashscopeApiKey == null || dashscopeApiKey.isEmpty()) {
            throw new IllegalStateException("DASHSCOPE_API_KEY 未配置");
        }
        
        log.info("创建 DashScope ChatModel: {}", dashscopeChatModel);
        
        return new DashScopeChatModel(
            dashscopeApiKey,
            dashscopeChatModel,
            dashscopeTemperature
        );
    }
    
    /**
     * 创建 DashScope EmbeddingModel
     */
    private EmbeddingModel createDashScopeEmbeddingModel() {
        if (dashscopeApiKey == null || dashscopeApiKey.isEmpty()) {
            throw new IllegalStateException("DASHSCOPE_API_KEY 未配置");
        }
        
        log.info("创建 DashScope EmbeddingModel: {}", dashscopeEmbeddingModel);
        
        return new DashScopeEmbeddingModel(
            dashscopeApiKey,
            dashscopeEmbeddingModel,
            dashscopeDimensions
        );
    }
    
    /**
     * 获取向量维度
     */
    @Bean
    public int embeddingDimension() {
        return "dashscope".equalsIgnoreCase(provider) 
            ? dashscopeDimensions 
            : openaiDimensions;
    }
}
