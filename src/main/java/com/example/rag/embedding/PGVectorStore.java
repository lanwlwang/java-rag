package com.example.rag.embedding;

import com.example.rag.model.Chunk;
import com.example.rag.model.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

/**
 * PGVector 向量存储服务
 * 对应 Python 中的 VectorRetriever 类
 * 
 * 核心功能:
 * 1. 向量数据存储到 PostgreSQL (pgvector 扩展)
 * 2. 基于向量相似度检索
 * 3. 支持元数据过滤
 */
@Slf4j
@Service
public class PGVectorStore {
    
    @Value("${langchain4j.pgvector.host:localhost}")
    private String host;
    
    @Value("${langchain4j.pgvector.port:5432}")
    private Integer port;
    
    @Value("${langchain4j.pgvector.database:rag_db}")
    private String database;
    
    @Value("${langchain4j.pgvector.user:postgres}")
    private String user;
    
    @Value("${langchain4j.pgvector.password}")
    private String password;
    
    @Value("${langchain4j.pgvector.table:rag_embeddings}")
    private String table;
    
    @Value("${langchain4j.pgvector.dimension:1536}")
    private Integer dimension;
    
    @Autowired
    private EmbeddingService embeddingService;
    
    private EmbeddingStore<TextSegment> embeddingStore;
    
    /**
     * 初始化 PGVector 存储
     */
    @PostConstruct
    public void init() {
        log.info("初始化 PGVector 存储: {}:{}/{}", host, port, database);
        
        this.embeddingStore = PgVectorEmbeddingStore.builder()
            .host(host)
            .port(port)
            .database(database)
            .user(user)
            .password(password)
            .table(table)
            .dimension(dimension)
            .createTable(true)
            .dropTableFirst(false) //开发/测试环境可以设置为true，每次启动能获取干净的表结构；生产环境可以设置为false，避免数据丢失
            .build();
        
        log.info("PGVector 存储初始化完成");
    }
    
    /**
     * 将文档的分块存储到向量库
     * 
     * @param document 文档对象
     */
    public void storeDocument(Document document) {
        log.info("开始存储文档向量: {}", document.getMetaInfo().getFileName());
        
        List<Chunk> chunks = document.getContent().getChunks();
        if (chunks == null || chunks.isEmpty()) {
            log.warn("文档没有分块，跳过存储");
            return;
        }
        
        // 提取文本
        List<String> texts = chunks.stream()
            .map(Chunk::getText)
            .toList();
        
        // 批量生成向量
        List<Embedding> embeddings = embeddingService.embedTexts(texts);
        
        // 构建 TextSegment (包含元数据)
        List<TextSegment> segments = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            Chunk chunk = chunks.get(i);
            
            // 创建 Metadata 对象
            Metadata metadata = new Metadata();
            metadata.put("chunk_id", String.valueOf(chunk.getId()));
            metadata.put("page", String.valueOf(chunk.getPage()));
            metadata.put("company_name", document.getMetaInfo().getCompanyName());
            metadata.put("sha1", document.getMetaInfo().getSha1());
            metadata.put("type", chunk.getType());
            
            // 创建 TextSegment
            TextSegment segment = TextSegment.from(chunk.getText(), metadata);
            
            segments.add(segment);
        }
        
        // 批量存储
        embeddingStore.addAll(embeddings, segments);
        
        log.info("文档向量存储完成，共存储 {} 个分块", chunks.size());
    }
    
    /**
     * 基于查询检索相似文档
     * 
     * @param query 查询文本
     * @param companyName 公司名称 (用于过滤)
     * @param topK 返回的结果数量
     * @return 检索结果
     */
    public List<EmbeddingMatch<TextSegment>> retrieve(String query, String companyName, int topK) {
        log.info("检索相似文档，公司: {}, topK: {}", companyName, topK);
        
        // 生成查询向量，调用大模型
        Embedding queryEmbedding = embeddingService.embedText(query);
        
        // 构建检索请求
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
            .queryEmbedding(queryEmbedding)
            .maxResults(topK)
            .minScore(0.0)  // 不设最小分数阈值
            .build();
        
        // 执行检索， 通过向量数据库
        EmbeddingSearchResult<TextSegment> searchResult = 
            embeddingStore.search(searchRequest);

        log.info("检索完成，共找到 {} 个结果", searchResult.matches().size());
        log.info("结果详情: {}", searchResult.matches());
        
        // 过滤出指定公司的结果
        List<EmbeddingMatch<TextSegment>> matches = searchResult.matches().stream()
            .filter(match -> {
//                String company = match.embedded().metadata("company_name");
//                return company != null && company.equals(companyName);
                return true;
            })
            .toList();
        
        log.info("检索完成，返回 {} 个结果", matches.size());
        
        return matches;
    }
    
    /**
     * 清空指定公司的向量数据
     * 
     * @param companyName 公司名称
     */
    public void clearCompanyData(String companyName) {
        log.warn("清空公司向量数据: {}", companyName);
        // PgVectorEmbeddingStore 不直接支持按条件删除
        // 需要手动执行 SQL 或重建表
        log.warn("PGVector 不支持按条件删除，请手动清理数据库");
    }
}
