-- ============================================
-- 修复向量维度不匹配问题
-- ============================================
-- 问题: 数据库表定义为 1536 维，但 DashScope 返回 1024 维
-- 解决: 修改表结构或重建表
-- ============================================

-- 方案 1: 修改现有表的向量维度（如果已有数据，会失败）
-- 注意: 如果表中已有数据，ALTER 会失败，需要先清空或备份数据

-- 尝试直接修改（如果表为空）
ALTER TABLE rag_embeddings 
ALTER COLUMN embedding TYPE vector(1024);

-- 如果上面失败，使用下面的方案

-- 方案 2: 重建表（会删除所有数据！）
-- 警告: 这将删除所有已存储的向量数据

-- 2.1 删除旧表
-- DROP TABLE IF EXISTS rag_embeddings CASCADE;

-- 2.2 创建新表（1024 维）
-- CREATE TABLE rag_embeddings (
--     embedding_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
--     embedding vector(1024),
--     text TEXT,
--     metadata JSONB,
--     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
-- );

-- 2.3 重建索引
-- CREATE INDEX rag_embeddings_embedding_idx 
-- ON rag_embeddings 
-- USING ivfflat (embedding vector_cosine_ops) 
-- WITH (lists = 100);

-- CREATE INDEX rag_embeddings_metadata_idx 
-- ON rag_embeddings 
-- USING gin (metadata);

-- CREATE INDEX rag_embeddings_company_idx 
-- ON rag_embeddings 
-- ((metadata->>'company_name'));

-- ============================================
-- 验证修改
-- ============================================

-- 查看表结构
\d rag_embeddings

-- 检查向量维度
SELECT 
    column_name,
    udt_name,
    character_maximum_length
FROM information_schema.columns 
WHERE table_name = 'rag_embeddings' 
AND column_name = 'embedding';

-- 查看数据量
SELECT COUNT(*) FROM rag_embeddings;

-- ============================================
-- 推荐方案：清空数据并重建
-- ============================================

-- 如果测试数据不重要，推荐直接清空并让 LangChain4j 自动创建正确的表

-- 步骤 1: 删除旧表
DROP TABLE IF EXISTS rag_embeddings CASCADE;

-- 步骤 2: 重启应用，LangChain4j 会自动创建正确维度的表（1024 维）

-- ============================================
-- 不同模型的向量维度参考
-- ============================================

-- OpenAI text-embedding-3-large: 1536 维
-- OpenAI text-embedding-3-small: 1536 维
-- OpenAI text-embedding-ada-002: 1536 维

-- DashScope text-embedding-v3: 1024 维
-- DashScope text-embedding-v2: 1536 维
-- DashScope text-embedding-v1: 1536 维

-- 如果要使用 OpenAI 模型，将配置改为:
-- langchain4j.pgvector.dimension: 1536
-- 并修改表结构为 vector(1536)
