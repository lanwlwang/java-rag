package com.example.rag.document;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 飞书在线文档（Docx）读取工具类（基于document-block/list接口）
 */
@Component
public class FeishuWikiReader {
    private static final Logger logger = LoggerFactory.getLogger(FeishuWikiReader.class);
    private static final String FEISHU_BASE_URL = "https://open.feishu.cn/open-apis";
    
    @Value("${feishu.app-id}")
    private String appId;
    
    @Value("${feishu.app-secret}")
    private String appSecret;
    
    private String tenantAccessToken;

    public FeishuWikiReader() {
        // Spring会通过setter注入配置值
    }
    
    /**
     * 初始化方法，在依赖注入后调用
     */
    @PostConstruct
    public void init() {
        if (appId != null && appSecret != null) {
            this.tenantAccessToken = getTenantAccessToken();
        }
    }
    
    /**
     * 带参数的构造函数（用于非Spring环境）
     */
    public FeishuWikiReader(String appId, String appSecret) {
        this.appId = appId;
        this.appSecret = appSecret;
        this.tenantAccessToken = getTenantAccessToken();
    }

    // 【复用原有逻辑】获取租户令牌
    private String getTenantAccessToken() {
        String tokenUrl = FEISHU_BASE_URL + "/auth/v3/tenant_access_token/internal";
        OkHttpClient client = new OkHttpClient().newBuilder().build();

        JSONObject requestBody = new JSONObject();
        requestBody.put("app_id", appId);
        requestBody.put("app_secret", appSecret);

        RequestBody body = RequestBody.create(
                requestBody.toJSONString(),
                MediaType.parse("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url(tokenUrl)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("获取令牌失败：" + response);
            }
            String responseBody = response.body().string();
            JSONObject result = JSON.parseObject(responseBody);
            if (result.getInteger("code") == 0) {
                return result.getString("tenant_access_token");
            } else {
                throw new RuntimeException("令牌接口返回错误：" + result.getString("msg"));
            }
        } catch (Exception e) {
            throw new RuntimeException("获取租户令牌异常", e);
        }
    }

    /**
     * 读取飞书在线文档的Block列表（核心接口）
     * @param documentId 飞书文档ID（从文档URL中提取）
     * @param pageSize   每页数量（最大500）
     * @return Block列表 + 纯文本内容
     */
    public Map<String, Object> readDocxBlocks(String documentId, int pageSize) {
        // 使用正确的飞书API端点：GET /docx/v1/documents/{document_id}/blocks
        String blockUrl = FEISHU_BASE_URL + "/docx/v1/documents/" + documentId + "/blocks";
        OkHttpClient client = new OkHttpClient().newBuilder().build();

        // 构建查询参数
        HttpUrl.Builder urlBuilder = HttpUrl.parse(blockUrl).newBuilder();
        urlBuilder.addQueryParameter("page_size", String.valueOf(pageSize));
        // page_token 是可选的，第一页可以不传
        String finalUrl = urlBuilder.build().toString();

        Request request = new Request.Builder()
                .url(finalUrl)
                .get()  // 使用GET请求而不是POST
                .addHeader("Authorization", "Bearer " + tenantAccessToken)
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .build();

        Map<String, Object> resultMap = new HashMap<>();
        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body().string();
            
            if (!response.isSuccessful()) {
                // 提供更详细的错误信息
                logger.error("读取Block失败，HTTP状态码: {}, URL: {}, 响应: {}", 
                    response.code(), finalUrl, responseBody);
                
                // 尝试解析错误响应
                try {
                    JSONObject errorResult = JSON.parseObject(responseBody);
                    String errorMsg = errorResult.getString("msg");
                    if (errorMsg != null && !errorMsg.isEmpty()) {
                        throw new IOException(String.format("读取Block失败 (HTTP %d): %s", response.code(), errorMsg));
                    }
                } catch (Exception e) {
                    // 如果解析失败，使用原始响应
                }
                throw new IOException(String.format("读取Block失败 (HTTP %d): %s", response.code(), responseBody));
            }
            
            JSONObject result;
            try {
                result = JSON.parseObject(responseBody);
            } catch (Exception e) {
                logger.error("解析响应JSON失败，响应内容: {}", responseBody);
                throw new RuntimeException("解析飞书API响应失败: " + e.getMessage(), e);
            }

            if (result.getInteger("code") == 0) {
                JSONObject data = result.getJSONObject("data");
                if (data != null) {
                    JSONArray blocks = data.getJSONArray("items");
                    if (blocks != null) {
                        String plainText = extractDocxPlainText(blocks); // 提取纯文本

                        resultMap.put("blocks", blocks); // 原始Block列表
                        resultMap.put("plainText", plainText); // 解析后的纯文本
                        resultMap.put("nextPageToken", data.getString("page_token")); // 分页令牌
                    } else {
                        logger.warn("响应中没有items字段，文档可能为空");
                        resultMap.put("blocks", new JSONArray());
                        resultMap.put("plainText", "");
                        resultMap.put("nextPageToken", null);
                    }
                } else {
                    logger.warn("响应中没有data字段");
                    resultMap.put("blocks", new JSONArray());
                    resultMap.put("plainText", "");
                    resultMap.put("nextPageToken", null);
                }
            } else {
                // API返回了错误码
                String errorMsg = result.getString("msg");
                throw new RuntimeException("飞书API返回错误 (code: " + result.getInteger("code") + "): " + errorMsg);
            }
        } catch (IOException e) {
            throw new RuntimeException("读取Docx Block异常: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("读取Docx Block异常", e);
        }
        return resultMap;
    }

    /**
     * 解析Docx Block为纯文本（支持文本、代码块、图片）
     * 根据实际飞书API返回格式解析
     */
    private String extractDocxPlainText(JSONArray blocks) {
        StringBuilder plainText = new StringBuilder();
        if (blocks == null || blocks.isEmpty()) {
            return "";
        }

        for (Object blockObj : blocks) {
            JSONObject block = (JSONObject) blockObj;
            if (block == null) {
                continue;
            }
            
            // 提取text类型的content
            JSONObject textObj = block.getJSONObject("text");
            if (textObj != null) {
                extractContentFromElements(textObj.getJSONArray("elements"), plainText);
            }
            
            // 提取code类型的content
            JSONObject codeObj = block.getJSONObject("code");
            if (codeObj != null) {
                JSONArray elements = codeObj.getJSONArray("elements");
                if (elements != null && !elements.isEmpty()) {
                    plainText.append("\n【代码块】:\n");
                    extractContentFromElements(elements, plainText);
                    plainText.append("\n");
                }
            }
            
            // 提取heading1类型的content
            JSONObject heading1Obj = block.getJSONObject("heading1");
            if (heading1Obj != null) {
                plainText.append("\n");
                extractContentFromElements(heading1Obj.getJSONArray("elements"), plainText);
                plainText.append("\n");
            }
            
            // 提取heading2类型的content
            JSONObject heading2Obj = block.getJSONObject("heading2");
            if (heading2Obj != null) {
                plainText.append("\n");
                extractContentFromElements(heading2Obj.getJSONArray("elements"), plainText);
                plainText.append("\n");
            }
            
            // 提取heading3类型的content
            JSONObject heading3Obj = block.getJSONObject("heading3");
            if (heading3Obj != null) {
                plainText.append("\n");
                extractContentFromElements(heading3Obj.getJSONArray("elements"), plainText);
                plainText.append("\n");
            }
            
            // 提取page类型的content
            JSONObject pageObj = block.getJSONObject("page");
            if (pageObj != null) {
                extractContentFromElements(pageObj.getJSONArray("elements"), plainText);
            }
        }
        return plainText.toString().trim();
    }
    
    /**
     * 从elements数组中提取text_run的content并追加到StringBuilder
     */
    private void extractContentFromElements(JSONArray elements, StringBuilder plainText) {
        if (elements == null || elements.isEmpty()) {
            return;
        }
        
        for (Object elementObj : elements) {
            JSONObject element = (JSONObject) elementObj;
            if (element == null) {
                continue;
            }
            
            JSONObject textRun = element.getJSONObject("text_run");
            if (textRun != null) {
                String content = textRun.getString("content");
                if (content != null && !content.trim().isEmpty()) {
                    plainText.append(content);
                }
            }
        }
    }

    /**
     * 提取飞书文档中所有block的content内容，拼接为List
     * @param documentId 飞书文档ID
     * @return content内容列表
     */
    public List<String> extractAllContents(String documentId) {
        List<String> contents = new ArrayList<>();
        try {
            Map<String, Object> result = readDocxBlocks(documentId, 500);
            JSONArray blocks = (JSONArray) result.get("blocks");
            
            if (blocks != null && !blocks.isEmpty()) {
                for (Object blockObj : blocks) {
                    JSONObject block = (JSONObject) blockObj;
                    if (block == null) {
                        continue;
                    }
                    
                    // 提取text类型的content
                    JSONObject textObj = block.getJSONObject("text");
                    if (textObj != null) {
                        extractContentFromElements(textObj.getJSONArray("elements"), contents);
                    }
                    
                    // 提取code类型的content
                    JSONObject codeObj = block.getJSONObject("code");
                    if (codeObj != null) {
                        extractContentFromElements(codeObj.getJSONArray("elements"), contents);
                    }
                    
                    // 提取heading1类型的content
                    JSONObject heading1Obj = block.getJSONObject("heading1");
                    if (heading1Obj != null) {
                        extractContentFromElements(heading1Obj.getJSONArray("elements"), contents);
                    }
                    
                    // 提取heading2类型的content
                    JSONObject heading2Obj = block.getJSONObject("heading2");
                    if (heading2Obj != null) {
                        extractContentFromElements(heading2Obj.getJSONArray("elements"), contents);
                    }
                    
                    // 提取heading3类型的content
                    JSONObject heading3Obj = block.getJSONObject("heading3");
                    if (heading3Obj != null) {
                        extractContentFromElements(heading3Obj.getJSONArray("elements"), contents);
                    }
                    
                    // 提取page类型的content
                    JSONObject pageObj = block.getJSONObject("page");
                    if (pageObj != null) {
                        extractContentFromElements(pageObj.getJSONArray("elements"), contents);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("提取content失败", e);
        }
        return contents;
    }
    
    /**
     * 从elements数组中提取text_run的content
     */
    private void extractContentFromElements(JSONArray elements, List<String> contents) {
        if (elements == null || elements.isEmpty()) {
            return;
        }
        
        for (Object elementObj : elements) {
            JSONObject element = (JSONObject) elementObj;
            if (element == null) {
                continue;
            }
            
            JSONObject textRun = element.getJSONObject("text_run");
            if (textRun != null) {
                String content = textRun.getString("content");
                if (content != null && !content.trim().isEmpty()) {
                    contents.add(content);
                }
            }
        }
    }

    /**
     * 获取图片的访问URL（需drive:media:readonly权限）
     */
    private String getImageUrl(String fileToken) {
        String imageUrl = FEISHU_BASE_URL + "/drive/v1/medias/" + fileToken + "/download";
        OkHttpClient client = new OkHttpClient().newBuilder().build();

        Request request = new Request.Builder()
                .url(imageUrl)
                .get()
                .addHeader("Authorization", "Bearer " + tenantAccessToken)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                JSONObject result = JSON.parseObject(response.body().string());
                return result.getJSONObject("data").getString("download_url");
            } else {
                logger.error("获取图片URL失败，HTTP状态码: {}", response.code());
                return "获取图片URL失败，HTTP状态码: " + response.code();
            }
        } catch (Exception e) {
            logger.error("获取图片URL异常", e);
            return "获取图片URL异常: " + e.getMessage();
        }
    }

    // 测试主方法
    public static void main(String[] args) {
        
        String appId = "cli_a9b2639d78385bcc";
        String appSecret = "d5g5ZDMUPpgbxZ0JBBdOoc7DtdnkokzY";
        String documentId = "JLlUd7lIIoM0Gtx4PyJcP2VDnSc";

        try {
            FeishuWikiReader docxReader = new FeishuWikiReader(appId, appSecret);
            Map<String, Object> docxContent = docxReader.readDocxBlocks(documentId, 500);

            System.out.println("文档纯文本：" + docxContent.get("plainText"));
            System.out.println("Block列表：" + docxContent.get("blocks"));
            
            // 提取所有content并输出
            System.out.println("\n=== 所有Content列表 ===");
            List<String> contents = docxReader.extractAllContents(documentId);
            for (int i = 0; i < contents.size(); i++) {
                System.out.println("Content " + (i + 1) + ": " + contents.get(i));
            }
            System.out.println("共 " + contents.size() + " 个content");
        } catch (Exception e) {
            System.err.println("错误: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * 读取飞书Wiki文档（兼容旧接口）
     * @param documentId 文档ID
     * @return JSON节点
     */
    public com.alibaba.fastjson2.JSONObject readWikiDocument(String documentId) {
        Map<String, Object> result = readDocxBlocks(documentId, 500);
        com.alibaba.fastjson2.JSONObject jsonObject = new com.alibaba.fastjson2.JSONObject();
        jsonObject.put("blocks", result.get("blocks"));
        jsonObject.put("plainText", result.get("plainText"));
        return jsonObject;
    }

    /**
     * 将飞书文档转换为RAG Document对象
     * @param documentId 文档ID
     * @param title 文档标题
     * @return Document对象
     */
    public com.example.rag.model.Document convertToDocument(String documentId, String title) {
        Map<String, Object> result = readDocxBlocks(documentId, 500);
        String plainText = (String) result.get("plainText");
        
        // 创建Document对象
        com.example.rag.model.Document.Page page = com.example.rag.model.Document.Page.builder()
            .page(1)
            .text(plainText != null ? plainText : "")
            .build();
        
        com.example.rag.model.Document.Content content = com.example.rag.model.Document.Content.builder()
            .pages(java.util.List.of(page))
            .build();
        
        com.example.rag.model.Document.MetaInfo metaInfo = com.example.rag.model.Document.MetaInfo.builder()
            .fileName(title)
            .companyName("") // 可以根据需要设置
            .sha1("") // 可以根据需要计算
            .build();
        
        return com.example.rag.model.Document.builder()
            .metaInfo(metaInfo)
            .content(content)
            .build();
    }
}
