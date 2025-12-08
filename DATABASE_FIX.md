# 数据库列名修复指南

## 问题描述

错误信息：
```
ERROR: column "embedding_id" of relation "rag_embeddings" does not exist
```

**原因**：
- 数据库表使用 `id` 作为主键列名
- LangChain4j 的 `PgVectorEmbeddingStore` 期望主键列名为 `embedding_id`

## 快速修复

### 方案 1: 重命名列（推荐，保留数据）

```bash
# 连接数据库
psql -U postgres -d rag_db

# 执行修复脚本
\i sql/fix_column_name.sql
```

或者直接执行 SQL：

```sql
ALTER TABLE rag_embeddings 
RENAME COLUMN id TO embedding_id;
```

### 方案 2: 重建表（数据不重要时使用）

```bash
# 连接数据库
psql -U postgres -d rag_db

# 删除旧表
DROP TABLE IF EXISTS rag_embeddings CASCADE;

# 重新创建表
\i sql/init_database.sql
```

## 验证修复

```sql
-- 查看表结构
\d rag_embeddings

-- 应该看到 embedding_id 列（不是 id）
```

## 完成后

重新启动应用，然后重新处理 PDF 文件。

## 其他已修复的问题

1. ✅ **批次大小限制**：DashScope API 每次最多处理 10 个文本的限制已在代码中处理
2. ✅ **数据库列名**：表结构已更新为使用 `embedding_id`
