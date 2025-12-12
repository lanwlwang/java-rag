package com.example.rag.ai;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * DashScope (通义千问) ChatModel 适配器
 * 实现 LangChain4j 的 ChatLanguageModel 接口
 */
@Slf4j
public class DashScopeChatModel implements ChatModel {
    
    private final String apiKey;
    private final String modelName;
    private final double temperature;
    private final Generation generation;
    
    public DashScopeChatModel(String apiKey, String modelName, double temperature) {
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.temperature = temperature;
        this.generation = new Generation();
        
        log.info("DashScope ChatModel 初始化完成, 模型: {}", modelName);
    }
    
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        try {
            // 转换 LangChain4j 消息格式为 DashScope 格式
            List<Message> dashscopeMessages = convertMessages(messages);
            
            // 构建请求参数
            GenerationParam param = GenerationParam.builder()
                .apiKey(apiKey)
                .model(modelName)
                .messages(dashscopeMessages)
                .temperature((float) temperature)
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .build();
            
            // 调用 API
            GenerationResult result = generation.call(param);
            
            // 解析响应
            String content = result.getOutput()
                .getChoices()
                .get(0)
                .getMessage()
                .getContent();
            
            log.debug("DashScope 响应: {}", content);
            
            return Response.from(AiMessage.from(content));
            
        } catch (ApiException | NoApiKeyException | InputRequiredException e) {
            log.error("DashScope API 调用失败", e);
            throw new RuntimeException("调用通义千问 API 失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 简化接口:直接传入文本消息
     */
    public String generate(String message) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(dev.langchain4j.data.message.UserMessage.from(message));
        
        Response<AiMessage> response = generate(messages);
        return response.content().text();
    }
    
    /**
     * 转换 LangChain4j 消息为 DashScope 消息
     */
    private List<Message> convertMessages(List<ChatMessage> messages) {
        List<Message> dashscopeMessages = new ArrayList<>();
        
        for (ChatMessage msg : messages) {
            Role role = switch (msg.type()) {
                case USER -> Role.USER;
                case AI -> Role.ASSISTANT;
                case SYSTEM -> Role.SYSTEM;
                default -> Role.USER;
            };
            
            // 根据消息类型提取文本内容
            String content = extractTextFromMessage(msg);
            
            dashscopeMessages.add(
                Message.builder()
                    .role(role.getValue())
                    .content(content)
                    .build()
            );
        }
        
        return dashscopeMessages;
    }
    
    /**
     * 从 ChatMessage 中提取文本内容
     */
    private String extractTextFromMessage(ChatMessage message) {
        if (message instanceof UserMessage userMsg) {
            return userMsg.singleText();
        } else if (message instanceof AiMessage aiMsg) {
            return aiMsg.text();
        } else if (message instanceof SystemMessage systemMsg) {
            return systemMsg.text();
        } else {
            return "";
        }
    }
}
