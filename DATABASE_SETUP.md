# 数据库配置指南

本文档详细说明如何配置 PostgreSQL + pgvector 向量数据库。

---

## 📋 目录

1. [数据库安装](#1-数据库安装)
2. [表结构说明](#2-表结构说明)
3. [初始化数据库](#3-初始化数据库)
4. [数据管理](#4-数据管理)
5. [性能优化](#5-性能优化)
6. [常见问题](#6-常见问题)

---

## 1. 数据库安装

### macOS 安装

```bash
# 1. 安装 PostgreSQL 15
brew install postgresql@15

# 2. 安装 pgvector 扩展
brew install pgvector

# 3. 启动 PostgreSQL
brew services start postgresql@15

# 4. 验证安装
psql --version
```

### Linux (Ubuntu) 安装

```bash
# 1. 安装 PostgreSQL
sudo apt update
sudo apt install postgresql postgresql-contrib

# 2. 编译安装 pgvector
sudo apt install -y postgresql-server-dev-all build-essential git
git clone https://github.com/pgvector/pgvector.git
cd pgvector
make
sudo make install

# 3. 启动服务
sudo systemctl start postgresql
sudo systemctl enable postgresql
```

### Docker 安装 (推荐)

```bash
# 拉取包含 pgvector 的镜像
docker pull ankane/pgvector

# 启动容器
docker run -d \
  --name rag-postgres \
  -e POSTGRES_PASSWORD=your_password \
  -e POSTGRES_DB=rag_db \
  -p 5432:5432 \
  ankane/pgvector
```

---

## 2. 表结构说明

### 核心表: `rag_embeddings`

| 字段名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| `id` | UUID | 主键,唯一标识符 | `a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11` |
| `embedding` | vector(1536) | 文本向量 (1536维) | `[0.123, -0.456, ...]` |
| `text` | TEXT | 原始文本内容 | `"公司2024年营收为..."` |
| `metadata` | JSONB | 元数据 (JSON格式) | 见下方详细说明 |
| `created_at` | TIMESTAMP | 创建时间 | `2024-01-15 10:30:00` |

### metadata 字段结构

```json
{
  "chunk_id": "0",           // 分块ID
  "page": "5",               // 所在页码
  "company_name": "测试公司", // 公司名称
  "sha1": "abc123...",       // 文档SHA1哈希
  "type": "markdown"         // 内容类型
}
```

### 索引说明

| 索引名 | 类型 | 字段 | 用途 |
|--------|------|------|------|
| `rag_embeddings_embedding_idx` | IVFFlat | embedding | 向量相似度检索 |
| `rag_embeddings_metadata_idx` | GIN | metadata | 元数据查询加速 |
| `rag_embeddings_company_idx` | B-tree | metadata->>'company_name' | 公司过滤加速 |

---

## 3. 初始化数据库

### 方式一: 使用 SQL 脚本 (推荐)

```bash
# 1. 创建数据库
createdb rag_db

# 2. 执行初始化脚本
cd /Users/yonghuili/IdeaProjects/RAG-cy/java-rag
psql rag_db -f sql/init_database.sql

# 3. 验证
psql rag_db -c "\dt"  # 查看表
psql rag_db -c "\dx"  # 查看扩展
```

### 方式二: 手动初始化

```bash
# 1. 连接数据库
psql rag_db

# 2. 在 psql 中执行
```

```sql
-- 启用 pgvector 扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- 创建表 (LangChain4j 会自动创建)
-- 如果配置了 create-table: true, 则跳过此步骤
CREATE TABLE IF NOT EXISTS rag_embeddings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    embedding vector(1536),
    text TEXT,
    metadata JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX rag_embeddings_embedding_idx 
ON rag_embeddings 
USING ivfflat (embedding vector_cosine_ops) 
WITH (lists = 100);

CREATE INDEX rag_embeddings_metadata_idx 
ON rag_embeddings 
USING gin (metadata);

-- 退出
\q
```

### 方式三: 自动创建 (LangChain4j)

配置 `application.yml`:

```yaml
langchain4j:
  pgvector:
    host: localhost
    port: 5432
    database: rag_db
    user: postgres
    password: your_password
    table: rag_embeddings
    dimension: 1536
    create-table: true        # ← 自动创建表
    drop-table-first: false   # ← 不删除已有数据
```

启动应用时会自动创建表和索引。

---

## 4. 数据管理

### 查看数据统计

```sql
-- 连接数据库
psql rag_db

-- 查询总数据量
SELECT COUNT(*) FROM rag_embeddings;

-- 各公司文档数量
SELECT 
    metadata->>'company_name' AS company,
    COUNT(*) AS count
FROM rag_embeddings
GROUP BY metadata->>'company_name'
ORDER BY count DESC;

-- 查看表大小
SELECT 
    pg_size_pretty(pg_total_relation_size('rag_embeddings')) AS total_size;
```

### 查看某公司的数据

```sql
SELECT 
    id,
    LEFT(text, 100) AS text_preview,
    metadata->>'company_name' AS company,
    metadata->>'page' AS page,
    metadata->>'type' AS type,
    created_at
FROM rag_embeddings
WHERE metadata->>'company_name' = '测试公司'
ORDER BY (metadata->>'page')::int
LIMIT 10;
```

### 删除数据

```sql
-- 删除指定公司的数据
DELETE FROM rag_embeddings 
WHERE metadata->>'company_name' = '测试公司';

-- 删除指定时间之前的数据
DELETE FROM rag_embeddings 
WHERE created_at < NOW() - INTERVAL '30 days';

-- 清空整个表 (慎用!)
TRUNCATE TABLE rag_embeddings;
```

### 导出数据

```bash
# 导出整个数据库
pg_dump rag_db > rag_db_backup.sql

# 导出单个表
pg_dump rag_db -t rag_embeddings > rag_embeddings_backup.sql

# 仅导出数据 (不含表结构)
pg_dump rag_db -t rag_embeddings --data-only > data_only.sql
```

### 导入数据

```bash
# 导入数据库
psql rag_db < rag_db_backup.sql

# 导入单个表
psql rag_db < rag_embeddings_backup.sql
```

---

## 5. 性能优化

### 5.1 索引优化

#### IVFFlat vs HNSW 索引

| 索引类型 | 优势 | 劣势 | 适用场景 |
|---------|------|------|---------|
| **IVFFlat** | 构建快,内存占用小 | 查询略慢 | 数据量 > 10万,频繁更新 |
| **HNSW** | 查询极快,精度高 | 构建慢,内存大 | 数据量 < 100万,读多写少 |

#### 创建 HNSW 索引

```sql
-- 删除旧索引
DROP INDEX IF EXISTS rag_embeddings_embedding_idx;

-- 创建 HNSW 索引
CREATE INDEX rag_embeddings_embedding_hnsw_idx 
ON rag_embeddings 
USING hnsw (embedding vector_cosine_ops) 
WITH (m = 16, ef_construction = 64);
```

**参数说明**:
- `m`: 最大连接数 (默认16,越大查询越快但内存占用越大)
- `ef_construction`: 构建时的搜索深度 (默认64,越大构建越慢但质量越好)

### 5.2 距离度量选择

pgvector 支持三种距离度量:

```sql
-- 余弦距离 (推荐,适合文本向量)
vector_cosine_ops

-- 欧氏距离
vector_l2_ops

-- 内积距离
vector_ip_ops
```

### 5.3 查询优化

```sql
-- 设置查询参数 (仅对当前会话有效)
SET ivfflat.probes = 10;  -- IVFFlat 搜索的列表数量

-- 或在查询中使用
SET LOCAL ivfflat.probes = 10;
SELECT * FROM rag_embeddings 
ORDER BY embedding <=> '[...]'::vector 
LIMIT 10;
```

### 5.4 维护任务

```sql
-- 更新表统计信息 (建议每天执行)
ANALYZE rag_embeddings;

-- 清理表碎片 (建议每周执行)
VACUUM rag_embeddings;

-- 完全清理 (锁表,建议在维护窗口执行)
VACUUM FULL rag_embeddings;

-- 重建索引 (数据量变化很大时)
REINDEX INDEX rag_embeddings_embedding_idx;
```

### 5.5 配置优化

编辑 PostgreSQL 配置文件 (macOS: `/opt/homebrew/var/postgresql@15/postgresql.conf`):

```ini
# 增加共享内存 (向量检索需要较多内存)
shared_buffers = 4GB

# 增加工作内存 (提升排序性能)
work_mem = 256MB

# 增加维护内存 (加速索引构建)
maintenance_work_mem = 2GB

# 增加有效缓存大小
effective_cache_size = 8GB

# 并行查询
max_parallel_workers_per_gather = 4
```

重启 PostgreSQL:
```bash
brew services restart postgresql@15
```

---

## 6. 常见问题

### Q1: pgvector 扩展未安装

**症状**:
```
ERROR: could not open extension control file "/usr/share/postgresql/15/extension/vector.control"
```

**解决**:
```bash
# macOS
brew install pgvector

# Linux
cd /tmp
git clone https://github.com/pgvector/pgvector.git
cd pgvector
make
sudo make install
```

### Q2: 向量维度不匹配

**症状**:
```
ERROR: expected 1536 dimensions, got 512
```

**解决**:
修改 `application.yml` 确保维度一致:

```yaml
langchain4j:
  openai:
    embedding-model:
      dimensions: 1536  # 必须一致
  
  pgvector:
    dimension: 1536     # 必须一致
```

### Q3: 连接被拒绝

**症状**:
```
Connection refused (Connection refused)
```

**解决**:
```bash
# 检查 PostgreSQL 是否运行
brew services list | grep postgresql

# 启动服务
brew services start postgresql@15

# 检查端口
lsof -i :5432
```

### Q4: 索引构建太慢

**解决**:
```sql
-- 增加构建索引时的内存
SET maintenance_work_mem = '2GB';

-- 或使用更小的 lists 参数
CREATE INDEX ... WITH (lists = 50);  -- 默认是 100
```

### Q5: 查询性能差

**排查步骤**:

```sql
-- 1. 检查索引是否被使用
EXPLAIN ANALYZE
SELECT * FROM rag_embeddings 
ORDER BY embedding <=> '[...]'::vector 
LIMIT 10;

-- 2. 检查表统计信息是否过期
SELECT schemaname, tablename, last_analyze 
FROM pg_stat_user_tables 
WHERE tablename = 'rag_embeddings';

-- 3. 更新统计信息
ANALYZE rag_embeddings;

-- 4. 调整 IVFFlat probes
SET ivfflat.probes = 20;  -- 增加搜索范围
```

---

## 📚 参考资料

- [pgvector GitHub](https://github.com/pgvector/pgvector)
- [PostgreSQL 官方文档](https://www.postgresql.org/docs/)
- [LangChain4j PGVector](https://docs.langchain4j.dev/integrations/embedding-stores/pgvector)

---

## 🎯 总结

### 最小化配置 (开发环境)

```bash
# 1. 安装
brew install postgresql@15 pgvector
brew services start postgresql@15

# 2. 创建数据库
createdb rag_db

# 3. 初始化
psql rag_db -f sql/init_database.sql

# 4. 配置 application.yml (自动创建表)
# create-table: true

# 5. 运行应用
mvn spring-boot:run
```

### 生产环境配置

1. ✅ 使用 Docker 部署
2. ✅ 配置 HNSW 索引
3. ✅ 定期执行 VACUUM ANALYZE
4. ✅ 配置数据库备份
5. ✅ 监控索引使用率
6. ✅ 优化 PostgreSQL 参数

**祝你配置顺利! 🚀**
