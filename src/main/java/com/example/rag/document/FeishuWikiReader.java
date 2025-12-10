package com.example.rag.document;

import com.example.rag.model.Document;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 飞书 Wiki 文档读取器
 * 
 * 功能:
 * 1. 读取飞书 Wiki 文档内容
 * 2. 解析富文本内容为纯文本
 * 3. 转换为 RAG 系统的 Document 格式
 */
@Slf4j
@Component
public class FeishuWikiReader {

    @Value("${feishu.tenant-access-token:}")
    private String tenantAccessToken;

    private static final String FEISHU_API_BASE = "https://open.feishu.cn/open-apis";

    private final OkHttpClient httpClient = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 测试方法
     * 
     * 使用前需要:
     * 1. 在 application.yml 中配置飞书 app-id 和 app-secret
     * 2. 或者手动调用 setTenantAccessToken() 设置访问令牌
     * 3. 将 documentId 替换为实际的飞书文档 ID
     */
    public static void main(String[] args) {
        FeishuWikiReader reader = new FeishuWikiReader();
        
        // 手动设置访问令牌（从配置文件或环境变量获取）
       reader.setTenantAccessToken("t-g104caeRIR6RO55SWOIK42PD6HEHWVPSPXV4JOAE");
        
        // 示例：读取飞书文档
        // 文档 ID 可以从飞书文档 URL 中获取
        // 例如: https://xxx.feishu.cn/docx/KgTSwsMzBiw1qGk7UYGcjksxnXd
        //String documentId = "KgTSwsMzBiw1qGk7UYGcjksxnXd";
        String documentId = "JLlUd7lIIoM0Gtx4PyJcP2VDnSc";

        try {
            System.out.println("=== 测试读取飞书文档 ===");
            System.out.println("文档 ID: " + documentId);

            JsonNode wikiContent = reader.readWikiDocument(documentId);
            System.out.println("\n文档原始内容:");
            System.out.println(wikiContent.toPrettyString());
            
            // 转换为 RAG Document
            Document document = reader.convertToDocument(documentId, "测试文档");
            System.out.println("\n转换后的文档:");
            System.out.println("- 页数: " + document.getContent().getPages().size());
            System.out.println("- 内容长度: " + document.getContent().getPages().get(0).getText().length() + " 字符");
            System.out.println("- 前 200 字符: " + 
                document.getContent().getPages().get(0).getText().substring(0, 
                    Math.min(200, document.getContent().getPages().get(0).getText().length())));
            
        } catch (Exception e) {
            System.err.println("\n错误: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void  setTenantAccessToken(String token) {
        this.tenantAccessToken = token;
    }

    /**
     * 读取飞书 Wiki 文档内容
     * 
     * @param documentId 文档 ID（可以从飞书文档 URL 中获取）
     * @return 文档原始内容的 JSON 数据
     * @throws IOException 网络或解析异常
     */
    public JsonNode readWikiDocument(String documentId) throws IOException {
        log.info("读取飞书 Wiki 文档: {}", documentId);
        
        // Wiki 文档需要使用 blocks API，而不是 raw_content API
        // 参考: https://open.feishu.cn/document/server-docs/docs/docs/docx-v1/document-block/list
        return readDocumentBlocks(documentId);
    }
    
    /**
     * 将飞书 Wiki 文档转换为 RAG Document 格式
     * 
     * @param documentId Wiki 文档 ID
     * @param title 文档标题
     * @return RAG Document 对象
     * @throws IOException 读取或转换异常
     */
    public Document convertToDocument(String documentId, String title) throws IOException {
        log.info("转换飞书 Wiki 文档为 RAG Document: {}", documentId);
        
        // 读取文档内容
        JsonNode content = readWikiDocument(documentId);
        
        // 解析为纯文本
        String plainText = extractPlainText(content);
        
        // 创建元信息
        Document.MetaInfo metaInfo = Document.MetaInfo.builder()
            .sha1(documentId)  // 使用文档 ID 作为标识
            .companyName(title)
            .fileName("feishu_wiki_" + documentId + ".txt")
            .build();
        
        // 创建单页内容（Wiki 文档通常作为单页处理）
        List<Document.Page> pages = new ArrayList<>();
        Document.Page page = Document.Page.builder()
            .page(1)
            .text(plainText)
            .build();
        pages.add(page);
        
        Document.Content docContent = Document.Content.builder()
            .pages(pages)
            .chunks(new ArrayList<>())  // 后续分块时填充
            .build();
        
        return Document.builder()
            .metaInfo(metaInfo)
            .content(docContent)
            .build();
    }


    /**
     * 读取飞书文档的 Block 内容（适用于 Docx 和 Wiki 文档）
     *
     * @param documentId 文档 ID
     * @return 文档 Block 列表的 JSON 数据
     * @throws IOException 网络或解析异常
     */
    public JsonNode readDocumentBlocks(String documentId) throws IOException {
        log.info("读取飞书文档 Blocks: {}", documentId);

        // 使用 Docx API 获取文档 Block 列表
        // 参考: https://open.feishu.cn/document/server-docs/docs/docs/docx-v1/document-block/list
        String url = FEISHU_API_BASE + "/docx/v1/documents/" + documentId + "/blocks?page_size=500";

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + tenantAccessToken)
                .addHeader("Content-Type", "application/json")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                log.error("飞书 API 调用失败: HTTP {} - {}, 响应内容: {}",
                        response.code(), response.message(), responseBody);
                throw new IOException("飞书 API 调用失败: " + response.code() + " - " + response.message()
                        + "\n响应: " + responseBody);
            }

            JsonNode rootNode = objectMapper.readTree(responseBody);

            // 检查返回码
            if (rootNode.has("code")) {
                int code = rootNode.get("code").asInt();
                if (code != 0) {
                    String msg = rootNode.has("msg") ? rootNode.get("msg").asText() : "未知错误";
                    log.error("飞书 API 返回错误码: {}, 消息: {}, 完整响应: {}", code, msg, responseBody);
                    throw new IOException("飞书 API 返回错误: code=" + code + ", msg=" + msg);
                }
            }

            return rootNode.has("data") ? rootNode.get("data") : rootNode;
        }
    }

    /**
     * 下载飞书文档中的附件（图片、表格文件等）
     *
     * @param fileToken 文件 Token
     * @return 文件字节数组
     * @throws IOException 下载异常
     */
    public byte[] downloadFile(String fileToken) throws IOException {
        log.info("下载飞书文件: {}", fileToken);

        String url = FEISHU_API_BASE + "/drive/v1/medias/" + fileToken + "/download";

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + tenantAccessToken)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("文件下载失败: " + response.code());
            }

            return response.body().bytes();
        }
    }

    /**
     * 从 JSON 中提取纯文本（处理 Block 结构）
     *
     * @param jsonContent 飞书文档 JSON
     * @return 纯文本内容
     */
    protected String extractPlainText(JsonNode jsonContent) {
        StringBuilder text = new StringBuilder();

        // 如果是 items 数组（blocks API 返回）
        if (jsonContent.has("items")) {
            JsonNode items = jsonContent.get("items");
            for (JsonNode block : items) {
                extractBlockText(block, text);
            }
        } else {
            // 递归提取所有文本节点
            extractTextRecursive(jsonContent, text);
        }

        return text.toString().trim();
    }

    /**
     * 从 Block 中提取文本
     */
    protected void extractBlockText(JsonNode block, StringBuilder text) {
        if (block == null) {
            return;
        }

        // 获取 block 类型
        String blockType = block.has("block_type") ? block.get("block_type").asText() : "";

        // 处理不同类型的 block
        if (blockType.equals("page")) {
            // page block 通常是根节点，跳过
            return;
        }

        // 提取文本内容
        if (block.has("text")) {
            JsonNode textNode = block.get("text");
            if (textNode.has("elements")) {
                for (JsonNode element : textNode.get("elements")) {
                    if (element.has("text_run")) {
                        JsonNode textRun = element.get("text_run");
                        if (textRun.has("content")) {
                            text.append(textRun.get("content").asText());
                        }
                    }
                }
            }
        }

        // 添加换行
        if (blockType.startsWith("heading") || blockType.equals("text") ||
                blockType.equals("bullet") || blockType.equals("ordered")) {
            text.append("\n");
        }
    }

    /**
     * 递归提取 JSON 中的文本内容
     */
    protected void extractTextRecursive(JsonNode node, StringBuilder text) {
        if (node == null) {
            return;
        }

        // 如果是文本节点
        if (node.has("text")) {
            text.append(node.get("text").asText()).append(" ");
        }

        // 如果是数组，递归处理每个元素
        if (node.isArray()) {
            for (JsonNode item : node) {
                extractTextRecursive(item, text);
            }
        }

        // 如果是对象，递归处理所有字段
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                extractTextRecursive(entry.getValue(), text);
            });
        }
    }

}
