# 数据库表结构详解

## 📊 表结构概览

```
┌──────────────────────────────────────────────────────────┐
│              rag_embeddings (向量存储表)                   │
├──────────────────┬──────────────┬────────────────────────┤
│ 字段名            │ 类型          │ 说明                   │
├──────────────────┼──────────────┼────────────────────────┤
│ id               │ UUID         │ 主键,自动生成           │
│ embedding        │ vector(1536) │ 文本向量 (1536维)       │
│ text             │ TEXT         │ 原始文本内容            │
│ metadata         │ JSONB        │ 元数据 (JSON格式)       │
│ created_at       │ TIMESTAMP    │ 创建时间                │
└──────────────────┴──────────────┴────────────────────────┘

索引:
  ├─ PRIMARY KEY: id
  ├─ IVFFlat: embedding (vector_cosine_ops)
  ├─ GIN: metadata
  └─ B-tree: metadata->>'company_name'
```

---

## 🔍 字段详解

### 1. id (UUID)

- **类型**: UUID
- **说明**: 主键,唯一标识每条记录
- **默认值**: `gen_random_uuid()` (自动生成)
- **示例**: `a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11`

**用途**:
- 唯一标识每个文档分块
- 支持分布式环境下的全局唯一性
- 避免主键冲突

### 2. embedding (vector)

- **类型**: `vector(1536)`
- **说明**: 文本的向量表示,用于相似度检索
- **维度**: 1536 (根据 Embedding 模型调整)

**支持的模型和维度**:

| 模型 | 维度 | 配置 |
|------|------|------|
| OpenAI text-embedding-3-large | 1536 / 3072 | `dimensions: 1536` |
| OpenAI text-embedding-3-small | 1536 | `dimensions: 1536` |
| DashScope text-embedding-v3 | 1536 | `dimensions: 1536` |

**示例数据**:
```
[0.123, -0.456, 0.789, ..., 0.321]  (共1536个浮点数)
```

**相似度计算**:
```sql
-- 余弦距离 (推荐)
embedding <=> '[0.1, 0.2, ...]'::vector

-- 欧氏距离
embedding <-> '[0.1, 0.2, ...]'::vector

-- 内积距离
embedding <#> '[0.1, 0.2, ...]'::vector
```

### 3. text (TEXT)

- **类型**: TEXT (无长度限制)
- **说明**: 原始文本内容,用于返回检索结果

**示例**:
```
"公司在2024年实现营业收入120亿元,同比增长15%。
其中主营业务收入100亿元,占比83.3%。"
```

**用途**:
- 向量检索后返回原始文本
- 用于 LLM 上下文构建
- 支持全文检索 (配合 PostgreSQL 全文索引)

### 4. metadata (JSONB)

- **类型**: JSONB (二进制 JSON)
- **说明**: 存储文档的元数据,支持复杂查询

**标准字段**:

```json
{
  "chunk_id": "0",           // 分块编号 (同一文档内的序号)
  "page": "5",               // 来源页码
  "company_name": "测试公司", // 公司名称 (用于过滤)
  "sha1": "abc123...",       // 文档SHA1哈希 (去重标识)
  "type": "markdown"         // 内容类型: markdown, table, image
}
```

**查询示例**:

```sql
-- 按公司名过滤
SELECT * FROM rag_embeddings 
WHERE metadata->>'company_name' = '测试公司';

-- 按页码过滤
SELECT * FROM rag_embeddings 
WHERE (metadata->>'page')::int = 5;

-- 按类型过滤
SELECT * FROM rag_embeddings 
WHERE metadata->>'type' = 'table';

-- 复杂条件
SELECT * FROM rag_embeddings 
WHERE metadata->>'company_name' = '测试公司'
  AND (metadata->>'page')::int BETWEEN 1 AND 10
  AND metadata->>'type' = 'markdown';
```

**扩展字段** (可选):

```json
{
  "company_name": "测试公司",
  "page": "5",
  "chunk_id": "0",
  "sha1": "abc123",
  "type": "markdown",
  
  // 可选扩展字段
  "year": "2024",           // 报告年份
  "section": "财务报告",     // 章节名称
  "heading": "营业收入分析", // 标题
  "table_id": "table_1",    // 表格ID (如果是表格)
  "confidence": 0.95,       // 解析置信度
  "language": "zh"          // 语言
}
```

### 5. created_at (TIMESTAMP)

- **类型**: TIMESTAMP
- **说明**: 记录创建时间
- **默认值**: `CURRENT_TIMESTAMP`

**示例**:
```
2024-01-15 10:30:00
```

**用途**:
- 数据审计
- 按时间范围清理旧数据
- 监控数据导入速度

---

## 📐 索引设计

### 1. 主键索引

```sql
PRIMARY KEY (id)
```

- **类型**: B-tree
- **用途**: 确保记录唯一性,快速定位
- **性能**: O(log n)

### 2. 向量索引 (IVFFlat)

```sql
CREATE INDEX rag_embeddings_embedding_idx 
ON rag_embeddings 
USING ivfflat (embedding vector_cosine_ops) 
WITH (lists = 100);
```

- **类型**: IVFFlat (倒排文件索引)
- **距离度量**: 余弦相似度
- **参数**: lists = 100 (聚类中心数量)
- **用途**: 加速向量相似度检索

**性能特点**:
- 构建速度: 快 ⚡
- 查询速度: 较快 🚀
- 内存占用: 小 💾
- 适用场景: 数据频繁更新,内存受限

**调优参数**:
```sql
-- 查询时设置 probes (搜索的聚类数量)
SET ivfflat.probes = 10;  -- 默认1,越大越精确但越慢

-- 构建索引时设置 lists (聚类中心数量)
-- 推荐: rows/1000 (例如 100万条数据 → lists=1000)
WITH (lists = 100)
```

### 3. 向量索引 (HNSW) - 可选

```sql
CREATE INDEX rag_embeddings_embedding_hnsw_idx 
ON rag_embeddings 
USING hnsw (embedding vector_cosine_ops) 
WITH (m = 16, ef_construction = 64);
```

- **类型**: HNSW (分层可导航小世界图)
- **用途**: 更快的向量检索

**性能特点**:
- 构建速度: 慢 🐌
- 查询速度: 极快 ⚡⚡⚡
- 内存占用: 大 💾💾
- 适用场景: 读多写少,追求极致性能

**参数说明**:
- `m = 16`: 每个节点的最大连接数 (12-48,默认16)
- `ef_construction = 64`: 构建时搜索深度 (32-512,默认64)

### 4. 元数据索引 (GIN)

```sql
CREATE INDEX rag_embeddings_metadata_idx 
ON rag_embeddings 
USING gin (metadata);
```

- **类型**: GIN (通用倒排索引)
- **用途**: 加速 JSONB 字段查询
- **支持操作**: `@>`, `?`, `?|`, `?&`

**使用示例**:
```sql
-- 包含查询
SELECT * FROM rag_embeddings 
WHERE metadata @> '{"company_name": "测试公司"}';

-- 键存在查询
SELECT * FROM rag_embeddings 
WHERE metadata ? 'company_name';
```

### 5. 公司名索引 (B-tree)

```sql
CREATE INDEX rag_embeddings_company_idx 
ON rag_embeddings 
((metadata->>'company_name'));
```

- **类型**: B-tree (表达式索引)
- **用途**: 加速按公司过滤的查询
- **性能**: 比 GIN 索引更快 (针对单个字段)

---

## 📈 数据量估算

### 存储空间计算

假设有以下数据:
- 公司数量: 10 家
- 每家公司报告: 5 份
- 每份报告页数: 100 页
- 每页分块数: 3 个

**总记录数**: 10 × 5 × 100 × 3 = **15,000 条**

**每条记录大小估算**:

| 组件 | 大小 | 说明 |
|------|------|------|
| id | 16 bytes | UUID |
| embedding | 6,144 bytes | 1536 × 4 bytes (float) |
| text | ~500 bytes | 平均文本长度 |
| metadata | ~200 bytes | JSON 元数据 |
| created_at | 8 bytes | TIMESTAMP |
| **总计** | **~6.8 KB** | 每条记录 |

**数据表大小**: 15,000 × 6.8 KB ≈ **100 MB**

**索引大小估算**:

| 索引 | 大小 | 计算 |
|------|------|------|
| 主键索引 | ~0.5 MB | 15,000 × 32 bytes |
| 向量索引 | ~92 MB | 15,000 × 6144 bytes |
| 元数据索引 | ~3 MB | 15,000 × 200 bytes |
| 公司名索引 | ~0.3 MB | 15,000 × 20 bytes |
| **总计** | **~96 MB** | |

**总存储空间**: 100 MB + 96 MB ≈ **200 MB**

### 性能基准

| 数据量 | IVFFlat 查询 | HNSW 查询 | 插入速度 |
|--------|--------------|-----------|----------|
| 1万条 | 10-20 ms | 5-10 ms | 100 条/秒 |
| 10万条 | 20-50 ms | 10-15 ms | 80 条/秒 |
| 100万条 | 50-100 ms | 15-25 ms | 50 条/秒 |

---

## 🔧 维护操作

### 日常维护

```sql
-- 1. 更新表统计信息 (每天)
ANALYZE rag_embeddings;

-- 2. 清理死元组 (每周)
VACUUM rag_embeddings;

-- 3. 完全清理 (每月,维护窗口)
VACUUM FULL rag_embeddings;

-- 4. 重建索引 (数据量变化超过50%时)
REINDEX INDEX rag_embeddings_embedding_idx;
```

### 监控查询

```sql
-- 查看表大小
SELECT 
    pg_size_pretty(pg_total_relation_size('rag_embeddings')) AS total,
    pg_size_pretty(pg_relation_size('rag_embeddings')) AS table,
    pg_size_pretty(pg_indexes_size('rag_embeddings')) AS indexes;

-- 查看索引使用情况
SELECT 
    indexname,
    idx_scan AS scans,
    idx_tup_read AS reads,
    idx_tup_fetch AS fetches
FROM pg_stat_user_indexes
WHERE tablename = 'rag_embeddings';

-- 查看死元组
SELECT 
    n_live_tup AS live_tuples,
    n_dead_tup AS dead_tuples,
    n_dead_tup::float / NULLIF(n_live_tup, 0) AS dead_ratio
FROM pg_stat_user_tables
WHERE tablename = 'rag_embeddings';
```

---

## 💡 最佳实践

### 1. 选择合适的向量维度

| 场景 | 推荐维度 | 原因 |
|------|---------|------|
| 高精度检索 | 1536-3072 | 更细粒度的语义表示 |
| 平衡性能 | 768-1536 | 性能与效果平衡 |
| 快速检索 | 384-512 | 更快的计算速度 |

### 2. 索引选择建议

| 数据规模 | 推荐索引 | 原因 |
|---------|---------|------|
| < 1万条 | 无需索引 | 顺序扫描已足够快 |
| 1-10万条 | IVFFlat | 平衡性能和内存 |
| > 10万条 | HNSW | 查询性能更优 |

### 3. 批量插入优化

```sql
-- 插入前禁用自动 VACUUM
ALTER TABLE rag_embeddings SET (autovacuum_enabled = false);

-- 批量插入 (Java 代码中使用)
-- embeddingStore.addAll(embeddings, segments);

-- 插入后重建索引
REINDEX TABLE rag_embeddings;
ANALYZE rag_embeddings;

-- 恢复自动 VACUUM
ALTER TABLE rag_embeddings SET (autovacuum_enabled = true);
```

### 4. 分区表 (大数据量场景)

```sql
-- 按公司分区
CREATE TABLE rag_embeddings_partitioned (
    id UUID,
    embedding vector(1536),
    text TEXT,
    metadata JSONB,
    created_at TIMESTAMP,
    company_name TEXT GENERATED ALWAYS AS (metadata->>'company_name') STORED
) PARTITION BY LIST (company_name);

-- 创建分区
CREATE TABLE rag_embeddings_company_a 
PARTITION OF rag_embeddings_partitioned 
FOR VALUES IN ('公司A');

CREATE TABLE rag_embeddings_company_b 
PARTITION OF rag_embeddings_partitioned 
FOR VALUES IN ('公司B');
```

---

## 📚 参考资料

- [pgvector 官方文档](https://github.com/pgvector/pgvector)
- [PostgreSQL JSONB](https://www.postgresql.org/docs/current/datatype-json.html)
- [向量索引性能对比](https://github.com/pgvector/pgvector#indexing)

---

**总结**: 本表结构设计支持高效的向量检索、灵活的元数据过滤,适合 RAG 系统的各种场景。 🚀
