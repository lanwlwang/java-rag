package com.example.rag.document;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 飞书 Token 管理器
 * 
 * 功能:
 * 1. 自动获取和刷新 tenant_access_token
 * 2. Token 缓存和过期管理
 * 3. 线程安全的 Token 获取
 */
@Slf4j
@Component
public class FeishuTokenManager {

    @Value("${feishu.app-id:}")
    private String appId;
    
    @Value("${feishu.app-secret:}")
    private String appSecret;
    
    private static final String FEISHU_API_BASE = "https://open.feishu.cn/open-apis";
    private static final long TOKEN_REFRESH_BUFFER = 5 * 60 * 1000; // 提前 5 分钟刷新
    
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ReentrantLock lock = new ReentrantLock();
    
    // Token 缓存
    private String cachedToken;
    private long tokenExpireTime;

    public FeishuTokenManager() {
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 获取有效的 tenant_access_token
     * 如果 token 不存在或即将过期，会自动刷新
     * 
     * @return 有效的 tenant_access_token
     * @throws IOException Token 获取失败
     */
    public String getToken() throws IOException {
        // 检查是否需要刷新 token
        if (needsRefresh()) {
            lock.lock();
            try {
                // 双重检查，避免多次刷新
                if (needsRefresh()) {
                    refreshToken();
                }
            } finally {
                lock.unlock();
            }
        }

        log.info("使用飞书 Token: {}", cachedToken);
        return cachedToken;
    }
    
    /**
     * 检查是否需要刷新 token
     */
    private boolean needsRefresh() {
        return cachedToken == null || 
               System.currentTimeMillis() >= (tokenExpireTime - TOKEN_REFRESH_BUFFER);
    }
    
    /**
     * 刷新 tenant_access_token
     * 
     * @throws IOException 刷新失败
     */
    private void refreshToken() throws IOException {
        log.info("刷新飞书 tenant_access_token");
        
        if (appId == null || appId.isEmpty() || appSecret == null || appSecret.isEmpty()) {
            throw new IOException("飞书应用配置不完整: app-id 或 app-secret 未配置");
        }
        
        String url = FEISHU_API_BASE + "/auth/v3/tenant_access_token/internal";
        
        // 构建请求体
        String requestBody = String.format(
            "{\"app_id\":\"%s\",\"app_secret\":\"%s\"}", 
            appId, appSecret
        );
        
        Request request = new Request.Builder()
            .url(url)
            .post(RequestBody.create(
                requestBody,
                MediaType.parse("application/json")
            ))
            .addHeader("Content-Type", "application/json")
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            
            if (!response.isSuccessful()) {
                log.error("获取飞书 Token 失败: HTTP {} - {}", response.code(), responseBody);
                throw new IOException("获取飞书 Token 失败: " + response.code() + "\n响应: " + responseBody);
            }
            
            JsonNode rootNode = objectMapper.readTree(responseBody);
            
            // 检查返回码
            if (rootNode.has("code")) {
                int code = rootNode.get("code").asInt();
                if (code != 0) {
                    String msg = rootNode.has("msg") ? rootNode.get("msg").asText() : "未知错误";
                    log.error("飞书 API 返回错误码: {}, 消息: {}", code, msg);
                    throw new IOException("飞书 API 返回错误: code=" + code + ", msg=" + msg);
                }
            }
            
            // 提取 token 和过期时间
            if (rootNode.has("tenant_access_token") && rootNode.has("expire")) {
                cachedToken = rootNode.get("tenant_access_token").asText();
                int expireSeconds = rootNode.get("expire").asInt();
                tokenExpireTime = System.currentTimeMillis() + (expireSeconds * 1000L);
                
                log.info("成功获取飞书 Token, 有效期: {} 秒, 过期时间: {}", 
                    expireSeconds, 
                    new java.util.Date(tokenExpireTime));
            } else {
                throw new IOException("飞书 API 响应缺少 token 或 expire 字段");
            }
        }
    }

}
