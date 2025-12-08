# 数据库快速上手指南 🚀

本文档提供向量数据库的快速配置步骤。

---

## 📋 一、需要创建的表

### 核心表: `rag_embeddings`

只需要创建 **一个表**,用于存储向量和元数据:

```sql
CREATE TABLE rag_embeddings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    embedding vector(1536),        -- 文本向量
    text TEXT,                      -- 原始文本
    metadata JSONB,                 -- 元数据 (公司名、页码等)
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**表结构说明**:

| 字段 | 类型 | 说明 | 示例 |
|------|------|------|------|
| `id` | UUID | 主键 | `a0eebc99-9c0b-...` |
| `embedding` | vector(1536) | 向量 (1536维) | `[0.123, -0.456, ...]` |
| `text` | TEXT | 原始文本 | `"公司2024年营收..."` |
| `metadata` | JSONB | 元数据 | `{"company_name": "测试公司", "page": "5"}` |
| `created_at` | TIMESTAMP | 创建时间 | `2024-01-15 10:30:00` |

---

## ⚡ 二、三种初始化方式

### 方式 1: 自动创建 (推荐 ✅)

**不需要手动创建表!** 只需配置 `application.yml`:

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
    drop-table-first: false   # ← 保留已有数据
```

运行应用时会自动创建表和索引。

---

### 方式 2: 执行初始化脚本

```bash
# 1. 创建数据库
createdb rag_db

# 2. 执行初始化脚本 (自动创建表和索引)
cd /Users/yonghuili/IdeaProjects/RAG-cy/java-rag
./scripts/setup_database.sh

# 或手动执行 SQL
psql rag_db -f sql/init_database.sql
```

**脚本包含**:
- ✅ 启用 pgvector 扩展
- ✅ 创建 rag_embeddings 表
- ✅ 创建向量索引 (IVFFlat)
- ✅ 创建元数据索引 (GIN)
- ✅ 创建公司名索引

---

### 方式 3: 手动创建

```bash
# 1. 连接数据库
psql rag_db

# 2. 在 psql 中执行
```

```sql
-- 启用 pgvector 扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- 创建表
CREATE TABLE rag_embeddings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    embedding vector(1536),
    text TEXT,
    metadata JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建向量索引 (加速检索)
CREATE INDEX rag_embeddings_embedding_idx 
ON rag_embeddings 
USING ivfflat (embedding vector_cosine_ops) 
WITH (lists = 100);

-- 创建元数据索引
CREATE INDEX rag_embeddings_metadata_idx 
ON rag_embeddings 
USING gin (metadata);

-- 创建公司名索引
CREATE INDEX rag_embeddings_company_idx 
ON rag_embeddings 
((metadata->>'company_name'));

-- 退出
\q
```

---

## 🔍 三、验证表是否创建成功

### 方法 1: psql 命令

```bash
# 查看表结构
psql rag_db -c "\d rag_embeddings"

# 查看索引
psql rag_db -c "\di rag_embeddings*"

# 查看扩展
psql rag_db -c "\dx"
```

### 方法 2: SQL 查询

```sql
-- 检查表是否存在
SELECT tablename FROM pg_tables WHERE tablename = 'rag_embeddings';

-- 检查 pgvector 扩展
SELECT * FROM pg_extension WHERE extname = 'vector';

-- 查看索引
SELECT indexname FROM pg_indexes WHERE tablename = 'rag_embeddings';
```

**期望输出**:

```
表名: rag_embeddings ✅
扩展: vector ✅
索引:
  - rag_embeddings_pkey (主键)
  - rag_embeddings_embedding_idx (向量索引)
  - rag_embeddings_metadata_idx (元数据索引)
  - rag_embeddings_company_idx (公司名索引)
```

---

## 📊 四、数据示例

### 插入数据 (Java 代码会自动完成)

表中的数据长这样:

| id | embedding | text | metadata | created_at |
|----|-----------|------|----------|------------|
| `a0ee...` | `[0.123, -0.456, ...]` | `"公司2024年..."` | `{"company_name": "测试公司", "page": "5"}` | `2024-01-15 10:30:00` |

### 查询数据

```sql
-- 查看总数据量
SELECT COUNT(*) FROM rag_embeddings;

-- 查看各公司的文档数量
SELECT 
    metadata->>'company_name' AS company,
    COUNT(*) AS count
FROM rag_embeddings
GROUP BY metadata->>'company_name';

-- 查看某公司的数据
SELECT 
    LEFT(text, 100) AS text_preview,
    metadata->>'page' AS page,
    created_at
FROM rag_embeddings
WHERE metadata->>'company_name' = '测试公司'
LIMIT 5;
```

---

## 🗑️ 五、数据清理

### 删除指定公司的数据

```sql
DELETE FROM rag_embeddings 
WHERE metadata->>'company_name' = '测试公司';
```

### 清空整个表

```sql
TRUNCATE TABLE rag_embeddings;
```

### 删除表和数据库

```bash
# 删除表
psql rag_db -c "DROP TABLE IF EXISTS rag_embeddings CASCADE;"

# 删除数据库
dropdb rag_db
```

---

## ⚙️ 六、配置说明

### application.yml 配置

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/rag_db
    username: postgres
    password: your_password     # ← 改成你的密码

langchain4j:
  pgvector:
    host: localhost
    port: 5432
    database: rag_db
    user: postgres
    password: your_password     # ← 改成你的密码
    table: rag_embeddings       # ← 表名
    dimension: 1536             # ← 向量维度 (与 embedding 模型一致)
    create-table: true          # ← 自动创建表
    drop-table-first: false     # ← 不删除已有数据
```

**重要**: `dimension` 必须与 Embedding 模型的维度一致:

| 模型 | 维度 |
|------|------|
| OpenAI text-embedding-3-large | 1536 或 3072 |
| DashScope text-embedding-v3 | 1536 |

---

## 🎯 七、完整流程 (新手)

```bash
# 1. 安装 PostgreSQL
brew install postgresql@15 pgvector
brew services start postgresql@15

# 2. 初始化数据库 (自动创建表)
cd /Users/yonghuili/IdeaProjects/RAG-cy/java-rag
./scripts/setup_database.sh

# 3. 修改配置文件
vim src/main/resources/application.yml
# 改: password: your_password

# 4. 运行应用 (会自动使用表)
mvn spring-boot:run

# 5. 验证数据 (运行后)
psql rag_db -c "SELECT COUNT(*) FROM rag_embeddings;"
```

---

## 📚 八、详细文档

- 📖 **[DATABASE_SETUP.md](./DATABASE_SETUP.md)** - 完整安装和配置指南
- 📐 **[DATABASE_SCHEMA.md](./docs/DATABASE_SCHEMA.md)** - 表结构详解
- 🛠️ **[sql/init_database.sql](./sql/init_database.sql)** - SQL 初始化脚本
- 🔧 **[scripts/setup_database.sh](./scripts/setup_database.sh)** - 自动初始化脚本

---

## ❓ 九、常见问题

### Q: 需要创建多少个表?

**A**: 只需要 **1 个表** (`rag_embeddings`),所有数据都存在这一个表里。

### Q: 必须手动创建表吗?

**A**: **不需要!** 配置 `create-table: true` 后会自动创建。

### Q: 向量维度怎么确定?

**A**: 根据你使用的 Embedding 模型:
- OpenAI text-embedding-3-large: **1536** 维
- DashScope text-embedding-v3: **1536** 维

在 `application.yml` 中配置:
```yaml
langchain4j:
  openai:
    embedding-model:
      dimensions: 1536  # 与 pgvector.dimension 一致
  pgvector:
    dimension: 1536     # 与 embedding 模型一致
```

### Q: 表已存在,怎么重新创建?

**A**: 
```sql
-- 删除旧表
DROP TABLE IF EXISTS rag_embeddings CASCADE;

-- 重新运行应用或执行 SQL 脚本
```

或者设置:
```yaml
drop-table-first: true  # 每次启动都重建 (开发环境)
```

### Q: 数据存在哪里?

**A**: 
- 数据库: `rag_db`
- 表名: `rag_embeddings`
- 位置: PostgreSQL 数据目录 (macOS: `/opt/homebrew/var/postgresql@15/`)

---

## ✅ 十、检查清单

配置完成后,确认以下项目:

- [ ] PostgreSQL 已安装并运行
- [ ] pgvector 扩展已安装
- [ ] 数据库 `rag_db` 已创建
- [ ] `vector` 扩展已启用
- [ ] 表 `rag_embeddings` 已创建 (或配置自动创建)
- [ ] `application.yml` 中数据库密码已配置
- [ ] `dimension` 配置与 Embedding 模型一致

**全部完成后,运行应用即可开始使用! 🎉**

---

## 🆘 需要帮助?

- 查看详细文档: [DATABASE_SETUP.md](./DATABASE_SETUP.md)
- 查看表结构: [DATABASE_SCHEMA.md](./docs/DATABASE_SCHEMA.md)
- 查看 SQL 脚本: [init_database.sql](./sql/init_database.sql)

**Have fun! 🚀**
