# 系统升级指南

## 📋 本次更新内容

### 1. 修复 DashScope API 批次限制 ✅
- **问题**：批量向量化时发送超过 10 个文本导致 API 报错
- **解决**：自动拆分为每批最多 10 个文本
- **影响文件**：`DashScopeEmbeddingModel.java`

### 2. 修复数据库列名不匹配 ✅
- **问题**：表使用 `id`，LangChain4j 期望 `embedding_id`
- **解决**：更新表结构和 SQL 脚本
- **影响文件**：`sql/init_database.sql`, `sql/fix_column_name.sql`

### 3. 迁移到 Apache Tika ✅
- **原因**：支持更多文档格式，更好的文本提取
- **变化**：PDFBox → Tika
- **影响文件**：`pom.xml`, `PDFParser.java`

---

## 🚀 升级步骤

### 步骤 1: 更新 Maven 依赖

```bash
cd /Users/yonghuili/IdeaProjects/java-rag
mvn clean install
```

**预期输出**：
```
[INFO] BUILD SUCCESS
```

### 步骤 2: 修复数据库表结构

**方案 A：重命名列（保留现有数据）**

```bash
psql -U postgres -d rag_db -c "ALTER TABLE rag_embeddings RENAME COLUMN id TO embedding_id;"
```

**方案 B：重建表（清空数据）**

```bash
psql -U postgres -d rag_db << EOF
DROP TABLE IF EXISTS rag_embeddings CASCADE;
\i sql/init_database.sql
EOF
```

**验证修复**：

```bash
psql -U postgres -d rag_db -c "\d rag_embeddings"
```

应该看到 `embedding_id` 列（不是 `id`）。

### 步骤 3: 重启应用

```bash
# 如果应用正在运行，先停止
# Ctrl+C 或 kill 进程

# 启动应用
mvn spring-boot:run
```

### 步骤 4: 测试功能

**测试 1：上传单个 PDF**

```bash
curl -X POST http://localhost:8080/api/rag/upload-pdf \
  -F "file=@pdf_reports/中芯国际2024年年度报告.pdf" \
  -F "companyName=中芯国际"
```

**预期响应**：
```json
{
  "success": true,
  "message": "PDF 处理成功",
  "data": {
    "fileName": "中芯国际2024年年度报告.pdf",
    "chunks": 50
  }
}
```

**测试 2：批量处理目录**

```bash
curl -X POST http://localhost:8080/api/rag/process-directory \
  -H "Content-Type: application/json" \
  -d '{"directory": "pdf_reports"}'
```

**测试 3：查询**

```bash
curl -X POST http://localhost:8080/api/rag/query \
  -H "Content-Type: application/json" \
  -d '{
    "question": "中芯国际的营收情况如何？",
    "companyName": "中芯国际",
    "topK": 5
  }'
```

---

## 🔍 问题排查

### 问题 1: Maven 构建失败

**错误信息**：
```
Could not resolve dependencies
```

**解决方案**：
```bash
# 清理本地仓库缓存
rm -rf ~/.m2/repository/org/apache/tika
rm -rf ~/.m2/repository/org/apache/pdfbox

# 重新下载
mvn clean install -U
```

### 问题 2: 数据库连接失败

**错误信息**：
```
Connection refused
```

**解决方案**：
```bash
# 检查 PostgreSQL 是否运行
psql -U postgres -d rag_db -c "SELECT 1;"

# 如果未运行，启动 PostgreSQL
brew services start postgresql
# 或
sudo systemctl start postgresql
```

### 问题 3: Tika 解析失败

**错误信息**：
```
org.apache.tika.exception.TikaException
```

**可能原因**：
- PDF 文件损坏
- 文件太大
- 不支持的加密

**解决方案**：
```bash
# 使用 pdfinfo 检查 PDF
pdfinfo your_file.pdf

# 如果加密，先解密
qpdf --decrypt input.pdf output.pdf
```

### 问题 4: 批量处理仍然失败

**检查日志**：
```bash
tail -f logs/app.log
```

**常见原因**：
- 网络问题（DashScope API）
- API 密钥过期
- 数据库空间不足

---

## 📊 性能监控

### 查看向量存储统计

```sql
-- 连接数据库
psql -U postgres -d rag_db

-- 查看总数据量
SELECT COUNT(*) FROM rag_embeddings;

-- 查看各公司数据量
SELECT 
    metadata->>'company_name' AS company,
    COUNT(*) AS chunk_count
FROM rag_embeddings
GROUP BY metadata->>'company_name'
ORDER BY chunk_count DESC;

-- 查看表大小
SELECT 
    pg_size_pretty(pg_total_relation_size('rag_embeddings')) AS total_size,
    pg_size_pretty(pg_relation_size('rag_embeddings')) AS table_size,
    pg_size_pretty(pg_indexes_size('rag_embeddings')) AS indexes_size;
```

### 监控应用日志

```bash
# 实时查看日志
tail -f logs/app.log

# 查看错误
grep -i error logs/app.log

# 查看 Tika 相关日志
grep -i tika logs/app.log
```

---

## 🎯 验证清单

升级完成后，请逐一检查：

- [ ] Maven 构建成功
- [ ] 数据库表结构正确（`embedding_id` 列存在）
- [ ] 应用启动无错误
- [ ] 可以上传并解析 PDF
- [ ] 向量化过程无批次错误
- [ ] 数据成功存储到数据库
- [ ] 查询功能正常工作
- [ ] Tika 日志显示正确解析

---

## 📚 相关文档

- [DATABASE_FIX.md](DATABASE_FIX.md) - 数据库修复详情
- [TIKA_MIGRATION.md](TIKA_MIGRATION.md) - Tika 迁移详情
- [README.md](README.md) - 系统总体说明
- [QUICK_START_DATABASE.md](QUICK_START_DATABASE.md) - 数据库快速开始

---

## 🔄 回滚方案

如果升级后出现严重问题，可以回滚：

### 1. 回滚代码

```bash
git checkout HEAD~1 pom.xml
git checkout HEAD~1 src/main/java/com/example/rag/document/PDFParser.java
git checkout HEAD~1 src/main/java/com/example/rag/ai/DashScopeEmbeddingModel.java
```

### 2. 重新构建

```bash
mvn clean install
```

### 3. 恢复数据库（如果需要）

```bash
# 从备份恢复
psql -U postgres -d rag_db < backup.sql
```

---

## 📞 技术支持

如遇到问题，请提供以下信息：

1. 错误日志（`logs/app.log`）
2. 系统环境（Java 版本、PostgreSQL 版本）
3. 具体操作步骤
4. 测试文件信息

---

## ✨ 下一步建议

升级完成后，可以考虑：

1. **扩展文档格式支持**
   - Word 文档（.docx）
   - Excel 表格（.xlsx）
   - PowerPoint（.pptx）

2. **优化性能**
   - 添加缓存机制
   - 并行处理多个文件
   - 异步任务队列

3. **增强功能**
   - 文档版本管理
   - 增量更新
   - 搜索结果高亮

4. **监控告警**
   - 添加 Prometheus 监控
   - 配置错误告警
   - 性能指标收集
