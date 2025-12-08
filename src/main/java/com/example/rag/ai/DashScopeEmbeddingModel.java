package com.example.rag.ai;

import com.alibaba.dashscope.embeddings.TextEmbedding;
import com.alibaba.dashscope.embeddings.TextEmbeddingParam;
import com.alibaba.dashscope.embeddings.TextEmbeddingResult;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * DashScope (通义千问) EmbeddingModel 适配器
 * 实现 LangChain4j 的 EmbeddingModel 接口
 */
@Slf4j
public class DashScopeEmbeddingModel implements EmbeddingModel {
    
    private final String apiKey;
    private final String modelName;
    private final int dimensions;
    private final TextEmbedding textEmbedding;
    
    public DashScopeEmbeddingModel(String apiKey, String modelName, int dimensions) {
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.dimensions = dimensions;
        this.textEmbedding = new TextEmbedding();
        
        log.info("DashScope EmbeddingModel 初始化完成, 模型: {}", modelName);
    }
    
    @Override
    public Response<Embedding> embed(TextSegment textSegment) {
        return embed(textSegment.text());
    }
    
    @Override
    public Response<Embedding> embed(String text) {
        try {
            // 构建请求参数
            TextEmbeddingParam param = TextEmbeddingParam.builder()
                .apiKey(apiKey)
                .model(modelName)
                .texts(List.of(text))
                .build();
            
            // 调用 API
            TextEmbeddingResult result = textEmbedding.call(param);
            
            // 解析响应
            List<Double> vector = result.getOutput()
                .getEmbeddings()
                .get(0)
                .getEmbedding();
            
            // 转换为 float[]
            float[] floatVector = vector.stream()
                .map(Double::floatValue)
                .collect(
                    () -> new float[vector.size()],
                    (arr, i) -> arr[0] = i,
                    (a, b) -> {}
                );
            
            // 正确方式:转换 List<Double> 为 float[]
            float[] vectorArray = new float[vector.size()];
            for (int i = 0; i < vector.size(); i++) {
                vectorArray[i] = vector.get(i).floatValue();
            }
            
            log.debug("DashScope Embedding 生成完成, 维度: {}", vectorArray.length);
            
            return Response.from(Embedding.from(vectorArray));
            
        } catch (ApiException | NoApiKeyException e) {
            log.error("DashScope Embedding API 调用失败", e);
            throw new RuntimeException("生成向量失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        // 提取所有文本
        List<String> texts = textSegments.stream()
            .map(TextSegment::text)
            .toList();
        
        // DashScope API 限制: 每次最多 10 个文本
        final int BATCH_SIZE = 10;
        List<Embedding> allEmbeddings = new ArrayList<>();
        
        try {
            // 分批处理
            for (int i = 0; i < texts.size(); i += BATCH_SIZE) {
                int end = Math.min(i + BATCH_SIZE, texts.size());
                List<String> batch = texts.subList(i, end);
                
                log.debug("处理批次 {}/{}, 大小: {}", (i / BATCH_SIZE + 1), 
                    (texts.size() + BATCH_SIZE - 1) / BATCH_SIZE, batch.size());
                
                // 构建请求参数(批量)
                TextEmbeddingParam param = TextEmbeddingParam.builder()
                    .apiKey(apiKey)
                    .model(modelName)
                    .texts(batch)
                    .build();
                
                // 调用 API
                TextEmbeddingResult result = textEmbedding.call(param);
                
                // 解析向量
                for (var embeddingItem : result.getOutput().getEmbeddings()) {
                    List<Double> vector = embeddingItem.getEmbedding();
                    
                    float[] vectorArray = new float[vector.size()];
                    for (int j = 0; j < vector.size(); j++) {
                        vectorArray[j] = vector.get(j).floatValue();
                    }
                    
                    allEmbeddings.add(Embedding.from(vectorArray));
                }
            }
            
            log.info("DashScope 批量 Embedding 生成完成, 总数量: {}", allEmbeddings.size());
            
            return Response.from(allEmbeddings);
            
        } catch (ApiException | NoApiKeyException e) {
            log.error("DashScope 批量 Embedding API 调用失败", e);
            throw new RuntimeException("批量生成向量失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public int dimension() {
        return dimensions;
    }
}
