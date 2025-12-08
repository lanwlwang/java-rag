# Java RAG 项目

基于 **LangChain4j** 和 **PGVector** 的检索增强生成(RAG)系统。

## 项目简介

这是一个参考 Python RAG 项目实现的 Java 版本,主要功能包括:

- 📄 PDF 文档解析与分块
- 🔢 文本向量化 (支持 OpenAI 和 DashScope)
- 💾 向量存储 (PostgreSQL + pgvector)
- 🔍 向量相似度检索
- 🤖 LLM 问答生成
- 🌐 REST API 接口

## 技术栈

- **Java 17**
- **Spring Boot 3.2.0** - Web 框架
- **LangChain4j 0.36.2** - AI 编排框架
- **PostgreSQL + pgvector** - 向量数据库
- **OpenAI API / DashScope API** - LLM 和 Embedding
- **Apache PDFBox** - PDF 解析
- **Maven** - 构建工具

## AI 模型支持

本项目支持两种 AI 提供商,可自由切换:

| 提供商 | Chat 模型 | Embedding 模型 | 说明 |
|--------|-----------|----------------|------|
| **OpenAI** | gpt-4o, gpt-4o-mini | text-embedding-3-large | 国际主流,效果优秀 |
| **DashScope** | qwen-plus, qwen-max | text-embedding-v3 | 阿里云通义千问,国内友好 |

📖 **详细配置说明**: 
- AI 模型配置: [DASHSCOPE_CONFIG.md](./DASHSCOPE_CONFIG.md)
- 数据库配置: [QUICK_START_DATABASE.md](./QUICK_START_DATABASE.md)

## 快速开始

### 1. 环境准备

#### 安装 PostgreSQL 和 pgvector

```bash
# macOS
brew install postgresql@15 pgvector
brew services start postgresql@15

# 初始化数据库 (自动创建表和索引)
cd java-rag
./scripts/setup_database.sh

# 或手动创建
createdb rag_db
psql rag_db -f sql/init_database.sql
```

💡 **提示**: 表结构会自动创建,也可以配置 `create-table: true` 让应用自动创建。

📖 **详细说明**: [数据库配置指南](./QUICK_START_DATABASE.md)

#### 配置 API Key

**方式一: 使用 OpenAI**

```bash
export OPENAI_API_KEY="sk-your-openai-key-here"
```

**方式二: 使用 DashScope (通义千问)**

```bash
export DASHSCOPE_API_KEY="sk-your-dashscope-key-here"
```

### 2. 配置项目

编辑 `src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/rag_db
    username: postgres
    password: your_password

langchain4j:
  openai:
    api-key: ${OPENAI_API_KEY}
    chat-model:
      model-name: gpt-4o-mini
    embedding-model:
      model-name: text-embedding-3-large
```

### 3. 构建运行

```bash
# 编译项目
mvn clean package

# 运行应用
mvn spring-boot:run

# 或直接运行 jar
java -jar target/java-rag-1.0.0.jar
```

应用启动后访问: http://localhost:8080/api/health

## 使用示例

### 1. 上传 PDF 文档

```bash
curl -X POST http://localhost:8080/api/upload-pdf \
  -F "file=@/path/to/report.pdf" \
  -F "companyName=中芯国际"
```

### 2. 问答查询

```bash
curl -X POST http://localhost:8080/api/ask \
  -H "Content-Type: application/json" \
  -d '{
    "question": "\"中芯国际\"2024年的营收是多少?",
    "kind": "number"
  }'
```

响应示例:

```json
{
  "stepByStepAnalysis": "1. 问题询问中芯国际2024年营收...",
  "reasoningSummary": "年报明确显示2024年营收为...",
  "relevantPages": [5, 12],
  "finalAnswer": 1234567890.5
}
```

### 3. 批量处理 PDF 目录

```bash
curl -X POST http://localhost:8080/api/process-directory \
  -H "Content-Type: application/json" \
  -d '{
    "directory": "/path/to/pdf/directory"
  }'
```

## 代码结构

```
src/main/java/com/example/rag/
├── RagApplication.java          # 应用入口
├── model/                       # 数据模型
│   ├── Document.java
│   ├── Chunk.java
│   ├── Question.java
│   ├── Answer.java
│   └── RetrievalResult.java
├── document/                    # 文档处理
│   ├── PDFParser.java          # PDF 解析
│   └── TextSplitter.java       # 文本分块
├── embedding/                   # 向量化
│   ├── EmbeddingService.java   # 向量生成
│   └── PGVectorStore.java      # 向量存储
├── retrieval/                   # 检索
│   └── VectorRetriever.java    # 向量检索
├── qa/                          # 问答
│   ├── QuestionProcessor.java  # 问题处理
│   └── PromptBuilder.java      # Prompt 构建
├── pipeline/                    # 流程编排
│   └── RAGPipeline.java        # 主流程
└── controller/                  # REST API
    └── RAGController.java
```

## 核心功能说明

### 1. PDF 解析与分块

```java
// 解析 PDF
Document document = pdfParser.parsePdf(pdfFile, companyName);

// 文本分块
document = textSplitter.splitDocument(document);
```

### 2. 向量化与存储

```java
// 向量化并存储
pgVectorStore.storeDocument(document);
```

### 3. 检索与问答

```java
// 检索相关文档
List<RetrievalResult> results = vectorRetriever.retrieveByCompanyName(
    companyName, query, topK
);

// 生成答案
Answer answer = questionProcessor.processQuestion(question);
```

## 问题类型支持

- `string` - 开放性文本问题
- `number` - 数值型问题
- `boolean` - 是非判断问题
- `names` - 名单列表问题

## 性能优化建议

1. **批量处理**: 使用 `embedTexts()` 批量生成向量
2. **连接池**: 配置 PostgreSQL 连接池大小
3. **缓存**: 对常见问题添加缓存层
4. **并发**: 使用 `@Async` 异步处理长时任务

## 与 Python 版本的对应关系

| Python 模块 | Java 模块 |
|------------|----------|
| `pipeline.py` | `RAGPipeline.java` |
| `pdf_parsing.py` | `PDFParser.java` |
| `text_splitter.py` | `TextSplitter.java` |
| `ingestion.py` | `EmbeddingService.java`, `PGVectorStore.java` |
| `retrieval.py` | `VectorRetriever.java` |
| `questions_processing.py` | `QuestionProcessor.java` |
| `prompts.py` | `PromptBuilder.java` |

## 常见问题

### 1. pgvector 扩展未启用

```sql
-- 在 PostgreSQL 中执行
CREATE EXTENSION IF NOT EXISTS vector;
```

### 2. OpenAI API 超时

```yaml
# 增加超时时间
langchain4j:
  openai:
    timeout: 60s
```

### 3. 内存不足

```bash
# 增加 JVM 内存
java -Xmx4g -jar target/java-rag-1.0.0.jar
```

## 下一步优化

- [ ] 添加 BM25 混合检索
- [ ] 实现 LLM 重排功能
- [ ] 支持多轮对话
- [ ] 添加流式输出
- [ ] 集成更多 LLM (Qwen, Gemini 等)
- [ ] 添加单元测试

## 许可证

MIT License

## 参考资料

- [LangChain4j 官方文档](https://docs.langchain4j.dev/)
- [PGVector GitHub](https://github.com/pgvector/pgvector)
- [OpenAI API 文档](https://platform.openai.com/docs)
