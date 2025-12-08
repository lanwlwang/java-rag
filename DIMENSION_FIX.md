# 向量维度不匹配修复指南

## 🔴 错误信息

```
ERROR: expected 1536 dimensions, not 1024
```

## 📊 问题分析

### 根本原因

| 组件 | 期望维度 | 实际维度 | 状态 |
|------|---------|---------|------|
| 数据库表 (rag_embeddings) | 1536 | - | ❌ |
| DashScope text-embedding-v3 | - | 1024 | ✅ |
| 配置文件 (旧) | 1536 | - | ❌ |
| 配置文件 (新) | 1024 | - | ✅ |

### 不同模型的维度

**DashScope (阿里云通义千问):**
- `text-embedding-v3`: **1024 维** ← 当前使用
- `text-embedding-v2`: 1536 维
- `text-embedding-v1`: 1536 维

**OpenAI:**
- `text-embedding-3-large`: 1536 维
- `text-embedding-3-small`: 1536 维  
- `text-embedding-ada-002`: 1536 维

## ✅ 已完成修复

### 1. 更新配置文件

`src/main/resources/application.yml` 已更新：

```yaml
langchain4j:
  dashscope:
    embedding-model:
      dimensions: 1024  # 修改为 1024

  pgvector:
    dimension: 1024     # 修改为 1024
```

## 🔧 数据库修复方案

### 方案 1: 重建表（推荐，快速简单）

**适用场景**: 测试环境或数据不重要

```bash
# 删除旧表，让 LangChain4j 自动创建正确的表
psql -U postgres -d rag_db << EOF
DROP TABLE IF EXISTS rag_embeddings CASCADE;
EOF
```

**然后重启应用**，LangChain4j 会自动创建 1024 维的表。

### 方案 2: 修改表结构（保留数据）

**适用场景**: 表为空或需要保留结构

```bash
# 修改向量列的维度
psql -U postgres -d rag_db << EOF
ALTER TABLE rag_embeddings 
ALTER COLUMN embedding TYPE vector(1024);
EOF
```

⚠️ **注意**: 如果表中已有数据，此命令会失败。必须先清空数据或删除表。

### 方案 3: 备份后重建（生产环境）

**适用场景**: 生产环境，需要保留数据

```bash
# 1. 备份现有数据
psql -U postgres -d rag_db << EOF
CREATE TABLE rag_embeddings_backup AS SELECT * FROM rag_embeddings;
EOF

# 2. 删除旧表
psql -U postgres -d rag_db << EOF
DROP TABLE IF EXISTS rag_embeddings CASCADE;
EOF

# 3. 重启应用（自动创建正确维度的表）

# 4. 如果需要，从备份恢复元数据（注意：向量数据需要重新生成）
# 因为维度不同，向量数据无法直接恢复
```

## 🚀 快速修复步骤

### 步骤 1: 删除旧表

```bash
psql -U postgres -d rag_db -c "DROP TABLE IF EXISTS rag_embeddings CASCADE;"
```

### 步骤 2: 验证删除

```bash
psql -U postgres -d rag_db -c "\dt rag_embeddings"
```

应该显示 "Did not find any relation named "rag_embeddings"."

### 步骤 3: 重启应用

```bash
# 停止当前应用 (Ctrl+C)

# 重新编译并启动
mvn clean spring-boot:run
```

### 步骤 4: 验证表创建

启动日志中应该看到：

```
PGVector 存储初始化完成
```

检查表结构：

```bash
psql -U postgres -d rag_db -c "\d rag_embeddings"
```

应该看到 `embedding | vector(1024)`

### 步骤 5: 测试上传

```bash
curl -X POST http://localhost:8080/api/rag/upload-pdf \
  -F "file=@pdf_reports/test.pdf" \
  -F "companyName=测试公司"
```

应该成功返回，无维度错误。

## 🔍 问题验证

### 检查当前表结构

```sql
-- 查看向量列定义
SELECT 
    column_name,
    data_type,
    udt_name
FROM information_schema.columns 
WHERE table_name = 'rag_embeddings' 
AND column_name = 'embedding';
```

**期望输出**:
```
 column_name | data_type | udt_name 
-------------+-----------+----------
 embedding   | USER-DEFINED | vector
```

### 查看详细表结构

```bash
psql -U postgres -d rag_db -c "\d+ rag_embeddings"
```

应该看到 `embedding | vector(1024)`

### 测试向量插入

```sql
-- 测试插入 1024 维向量
INSERT INTO rag_embeddings (embedding, text, metadata)
VALUES (
    array_fill(0.1, ARRAY[1024])::vector,
    '测试文本',
    '{"test": true}'::jsonb
);
```

成功则说明维度正确。

## 📋 完整修复检查清单

- [x] 更新 `application.yml` 配置（1536 → 1024）
- [ ] 删除旧的数据库表
- [ ] 重启应用
- [ ] 验证表自动创建（1024 维）
- [ ] 测试 PDF 上传
- [ ] 验证向量存储成功
- [ ] 测试查询功能

## 🎯 不同场景的配置

### 场景 1: 使用 DashScope text-embedding-v3（当前）

```yaml
langchain4j:
  dashscope:
    embedding-model:
      model-name: text-embedding-v3
      dimensions: 1024
  pgvector:
    dimension: 1024
```

数据库表：`vector(1024)`

### 场景 2: 使用 DashScope text-embedding-v2

```yaml
langchain4j:
  dashscope:
    embedding-model:
      model-name: text-embedding-v2
      dimensions: 1536
  pgvector:
    dimension: 1536
```

数据库表：`vector(1536)`

### 场景 3: 使用 OpenAI

```yaml
rag:
  provider: openai

langchain4j:
  openai:
    embedding-model:
      model-name: text-embedding-3-large
      dimensions: 1536
  pgvector:
    dimension: 1536
```

数据库表：`vector(1536)`

## ⚠️ 常见错误

### 错误 1: 表已存在且有数据

**错误信息**:
```
ERROR: cannot alter type of a column used in a trigger definition
```

**解决**: 必须删除表或清空数据后再修改。

### 错误 2: 配置不一致

**症状**: 应用启动成功，但上传时报维度错误

**检查**:
```bash
# 检查配置
grep -A 2 "dimensions:" src/main/resources/application.yml

# 检查表结构  
psql -U postgres -d rag_db -c "\d rag_embeddings" | grep embedding
```

确保三者一致：
1. `dashscope.embedding-model.dimensions`
2. `pgvector.dimension`  
3. 数据库表 `embedding` 列类型

### 错误 3: 模型返回维度与配置不符

**症状**: 配置正确，但仍报错

**原因**: 可能使用了错误的模型名称

**检查**: 查看 DashScope 文档确认模型名称和维度
- https://help.aliyun.com/document_detail/2712560.html

## 📚 参考资料

- [DashScope Embedding 文档](https://help.aliyun.com/document_detail/2712560.html)
- [pgvector 文档](https://github.com/pgvector/pgvector)
- [LangChain4j PgVector 文档](https://docs.langchain4j.dev/integrations/embedding-stores/pgvector)

## 💡 最佳实践

1. **配置一致性**: 确保所有维度配置相同
   - `application.yml` 中的 `dimensions` 
   - `application.yml` 中的 `dimension`
   - 数据库表中的向量维度

2. **环境隔离**: 测试环境和生产环境使用独立数据库

3. **文档清晰**: 在配置文件中注释当前使用的模型和维度

4. **验证测试**: 修改配置后，先测试单个文件上传

5. **监控日志**: 启动时检查维度相关的日志信息

---

**修复完成后，系统将正常工作！** 🎉
