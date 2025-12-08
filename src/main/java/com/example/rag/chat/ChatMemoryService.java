package com.example.rag.chat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 对话记忆服务
 * 
 * 功能:
 * 1. 管理多个会话的对话历史
 * 2. 支持会话创建、获取、清空
 * 3. 支持消息窗口限制（避免上下文过长）
 * 4. 自动过期清理
 */
@Slf4j
@Service
public class ChatMemoryService {
    
    /**
     * 每个会话保留的最大消息数（包括系统消息、用户消息、AI 回复）
     * 例如：maxMessages=10 表示保留最近 5 轮对话
     */
    @Value("${rag.chat.memory.max-messages:20}")
    private int maxMessages;
    
    /**
     * 会话超时时间（毫秒）默认 30 分钟
     */
    @Value("${rag.chat.memory.session-timeout:1800000}")
    private long sessionTimeout;
    
    /**
     * 存储所有会话的内存
     * Key: sessionId
     * Value: SessionInfo (包含 ChatMemory 和最后访问时间)
     */
    private final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();
    
    /**
     * 创建新会话
     * 
     * @return 会话 ID
     */
    public String createSession() {
        String sessionId = UUID.randomUUID().toString();
        
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
            .maxMessages(maxMessages)
            .build();
        
        SessionInfo sessionInfo = new SessionInfo(chatMemory, System.currentTimeMillis());
        sessions.put(sessionId, sessionInfo);
        
        log.info("创建新会话: {}", sessionId);
        
        // 清理过期会话
        cleanExpiredSessions();
        
        return sessionId;
    }
    
    /**
     * 获取会话的对话历史
     * 
     * @param sessionId 会话 ID
     * @return 对话消息列表
     */
    public List<ChatMessage> getMessages(String sessionId) {
        SessionInfo sessionInfo = sessions.get(sessionId);
        
        if (sessionInfo == null) {
            log.warn("会话不存在: {}, 创建新会话", sessionId);
            return List.of();
        }
        
        // 更新最后访问时间
        sessionInfo.updateLastAccessTime();
        
        return sessionInfo.getChatMemory().messages();
    }
    
    /**
     * 添加系统消息
     * 
     * @param sessionId 会话 ID
     * @param systemPrompt 系统提示词
     */
    public void addSystemMessage(String sessionId, String systemPrompt) {
        SessionInfo sessionInfo = getOrCreateSession(sessionId);
        sessionInfo.getChatMemory().add(SystemMessage.from(systemPrompt));
        sessionInfo.updateLastAccessTime();
        
        log.debug("会话 {} 添加系统消息", sessionId);
    }
    
    /**
     * 添加用户消息
     * 
     * @param sessionId 会话 ID
     * @param userMessage 用户消息
     */
    public void addUserMessage(String sessionId, String userMessage) {
        SessionInfo sessionInfo = getOrCreateSession(sessionId);
        sessionInfo.getChatMemory().add(UserMessage.from(userMessage));
        sessionInfo.updateLastAccessTime();
        
        log.debug("会话 {} 添加用户消息: {}", sessionId, userMessage);
    }
    
    /**
     * 添加 AI 回复
     * 
     * @param sessionId 会话 ID
     * @param aiResponse AI 回复内容
     */
    public void addAiMessage(String sessionId, String aiResponse) {
        SessionInfo sessionInfo = getOrCreateSession(sessionId);
        sessionInfo.getChatMemory().add(AiMessage.from(aiResponse));
        sessionInfo.updateLastAccessTime();
        
        log.debug("会话 {} 添加 AI 回复", sessionId);
    }
    
    /**
     * 清空会话历史
     * 
     * @param sessionId 会话 ID
     */
    public void clearSession(String sessionId) {
        SessionInfo sessionInfo = sessions.get(sessionId);
        
        if (sessionInfo != null) {
            sessionInfo.getChatMemory().clear();
            log.info("清空会话历史: {}", sessionId);
        }
    }
    
    /**
     * 删除会话
     * 
     * @param sessionId 会话 ID
     */
    public void deleteSession(String sessionId) {
        sessions.remove(sessionId);
        log.info("删除会话: {}", sessionId);
    }
    
    /**
     * 获取会话信息，如果不存在则创建
     */
    private SessionInfo getOrCreateSession(String sessionId) {
        return sessions.computeIfAbsent(sessionId, id -> {
            log.info("会话不存在，自动创建: {}", id);
            ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .maxMessages(maxMessages)
                .build();
            return new SessionInfo(chatMemory, System.currentTimeMillis());
        });
    }
    
    /**
     * 清理过期会话
     */
    private void cleanExpiredSessions() {
        long now = System.currentTimeMillis();
        
        sessions.entrySet().removeIf(entry -> {
            boolean expired = (now - entry.getValue().getLastAccessTime()) > sessionTimeout;
            if (expired) {
                log.info("清理过期会话: {}", entry.getKey());
            }
            return expired;
        });
    }
    
    /**
     * 获取活跃会话数量
     */
    public int getActiveSessionCount() {
        cleanExpiredSessions();
        return sessions.size();
    }
    
    /**
     * 检查会话是否存在
     */
    public boolean sessionExists(String sessionId) {
        return sessions.containsKey(sessionId);
    }
    
    /**
     * 会话信息内部类
     */
    private static class SessionInfo {
        private final ChatMemory chatMemory;
        private long lastAccessTime;
        
        public SessionInfo(ChatMemory chatMemory, long lastAccessTime) {
            this.chatMemory = chatMemory;
            this.lastAccessTime = lastAccessTime;
        }
        
        public ChatMemory getChatMemory() {
            return chatMemory;
        }
        
        public long getLastAccessTime() {
            return lastAccessTime;
        }
        
        public void updateLastAccessTime() {
            this.lastAccessTime = System.currentTimeMillis();
        }
    }
}
