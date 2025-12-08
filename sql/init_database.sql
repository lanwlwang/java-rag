-- ============================================
-- RAG 向量数据库初始化脚本
-- ============================================
-- 数据库: rag_db
-- 用途: 存储文档向量和元数据
-- ============================================

-- 1. 启用 pgvector 扩展 (必须)
CREATE EXTENSION IF NOT EXISTS vector;

-- 验证扩展是否启用
SELECT * FROM pg_extension WHERE extname = 'vector';

-- 2. 创建向量存储表 (LangChain4j 会自动创建,这里是手动版本)
-- 注意: 如果配置了 create-table: true, 则不需要手动创建

-- 注意: LangChain4j PgVectorEmbeddingStore 使用 embedding_id 作为主键列名
-- 向量维度根据使用的模型调整:
--   DashScope text-embedding-v3: 1024 维
--   DashScope text-embedding-v2: 1536 维
--   OpenAI text-embedding-3-*: 1536 维
CREATE TABLE IF NOT EXISTS rag_embeddings (
    embedding_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),  -- 唯一标识符 (LangChain4j 默认列名)
    embedding vector(1024),                          -- 向量数据 (1024维 for DashScope v3)
    text TEXT,                                       -- 原始文本内容
    metadata JSONB,                                  -- 元数据 (JSON格式)
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP  -- 创建时间
);

-- 3. 创建索引以加速向量检索
-- IVFFlat 索引: 适合大规模数据 (> 10万条)
CREATE INDEX IF NOT EXISTS rag_embeddings_embedding_idx 
ON rag_embeddings 
USING ivfflat (embedding vector_cosine_ops) 
WITH (lists = 100);

-- HNSW 索引: 适合高性能检索 (推荐)
-- CREATE INDEX IF NOT EXISTS rag_embeddings_embedding_hnsw_idx 
-- ON rag_embeddings 
-- USING hnsw (embedding vector_cosine_ops) 
-- WITH (m = 16, ef_construction = 64);

-- 4. 为元数据创建 GIN 索引 (加速 JSON 查询)
CREATE INDEX IF NOT EXISTS rag_embeddings_metadata_idx 
ON rag_embeddings 
USING gin (metadata);

-- 5. 为公司名创建索引 (常用过滤条件)
CREATE INDEX IF NOT EXISTS rag_embeddings_company_idx 
ON rag_embeddings 
((metadata->>'company_name'));

-- 6. 查看表结构
\d rag_embeddings

-- ============================================
-- 常用查询示例
-- ============================================

-- 查询表数据量
SELECT COUNT(*) FROM rag_embeddings;

-- 查询各公司的文档数量
SELECT 
    metadata->>'company_name' AS company,
    COUNT(*) AS count
FROM rag_embeddings
GROUP BY metadata->>'company_name'
ORDER BY count DESC;

-- 查看某个公司的数据
SELECT 
    embedding_id,
    LEFT(text, 100) AS text_preview,
    metadata->>'company_name' AS company,
    metadata->>'page' AS page,
    metadata->>'type' AS type
FROM rag_embeddings
WHERE metadata->>'company_name' = '测试公司'
LIMIT 10;

-- 清空指定公司的数据
-- DELETE FROM rag_embeddings WHERE metadata->>'company_name' = '测试公司';

-- 清空所有数据 (慎用!)
-- TRUNCATE TABLE rag_embeddings;

-- ============================================
-- 性能监控查询
-- ============================================

-- 查看索引使用情况
SELECT 
    schemaname,
    tablename,
    indexname,
    idx_scan AS index_scans,
    idx_tup_read AS tuples_read,
    idx_tup_fetch AS tuples_fetched
FROM pg_stat_user_indexes
WHERE tablename = 'rag_embeddings'
ORDER BY idx_scan DESC;

-- 查看表大小
SELECT 
    pg_size_pretty(pg_total_relation_size('rag_embeddings')) AS total_size,
    pg_size_pretty(pg_relation_size('rag_embeddings')) AS table_size,
    pg_size_pretty(pg_indexes_size('rag_embeddings')) AS indexes_size;

-- ============================================
-- 向量检索示例 (手动测试)
-- ============================================

-- 注意: 这需要你先有数据,并且知道某个向量值
-- 下面是一个示例查询,找出与给定向量最相似的 top 5 文档

-- 示例: 余弦相似度检索
-- SELECT 
--     embedding_id,
--     LEFT(text, 100) AS text_preview,
--     metadata->>'company_name' AS company,
--     1 - (embedding <=> '[0.1, 0.2, ...]'::vector) AS similarity
-- FROM rag_embeddings
-- WHERE metadata->>'company_name' = '测试公司'
-- ORDER BY embedding <=> '[0.1, 0.2, ...]'::vector
-- LIMIT 5;

-- ============================================
-- 维护操作
-- ============================================

-- 重建索引 (数据量变化很大时)
-- REINDEX INDEX rag_embeddings_embedding_idx;

-- 分析表 (更新统计信息)
ANALYZE rag_embeddings;

-- 清理碎片
-- VACUUM FULL rag_embeddings;

COMMENT ON TABLE rag_embeddings IS 'RAG系统向量存储表,存储文档分块的向量和元数据';
COMMENT ON COLUMN rag_embeddings.embedding_id IS '唯一标识符 (LangChain4j默认主键列名)';
COMMENT ON COLUMN rag_embeddings.embedding IS '文本向量 (1024维 for DashScope text-embedding-v3)';
COMMENT ON COLUMN rag_embeddings.text IS '原始文本内容';
COMMENT ON COLUMN rag_embeddings.metadata IS '元数据: company_name, page, chunk_id, sha1, type';
COMMENT ON COLUMN rag_embeddings.created_at IS '创建时间';
