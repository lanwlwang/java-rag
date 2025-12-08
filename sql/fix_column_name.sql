-- ============================================
-- 修复 rag_embeddings 表的列名
-- ============================================
-- 问题: 表中使用 id 列，但 LangChain4j 期望 embedding_id
-- 解决: 重命名列或重建表
-- ============================================

-- 方案 1: 重命名列 (如果表中已有数据，推荐此方案)
-- 将 id 列重命名为 embedding_id
ALTER TABLE rag_embeddings 
RENAME COLUMN id TO embedding_id;

-- 验证修改
\d rag_embeddings

-- 方案 2: 删除旧表并重建 (如果表中无重要数据，可使用此方案)
-- 警告: 这将删除所有数据！
-- DROP TABLE IF EXISTS rag_embeddings CASCADE;

-- 然后运行 init_database.sql 中的创建表语句

-- ============================================
-- 验证查询
-- ============================================

-- 查看表结构
SELECT 
    column_name, 
    data_type, 
    is_nullable,
    column_default
FROM information_schema.columns
WHERE table_name = 'rag_embeddings'
ORDER BY ordinal_position;

-- 查看数据
SELECT COUNT(*) FROM rag_embeddings;
