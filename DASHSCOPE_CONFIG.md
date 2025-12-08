# DashScope (通义千问) 配置指南

本项目已集成 **阿里云 DashScope (通义千问)** 支持,可以自由切换 OpenAI 和 DashScope 两种 AI 提供商。

---

## 📋 一、API Key 配置

### 方式 1: 环境变量配置 (推荐)

#### macOS/Linux

```bash
# OpenAI
export OPENAI_API_KEY="sk-your-openai-key-here"

# DashScope (通义千问)
export DASHSCOPE_API_KEY="sk-your-dashscope-key-here"

# 运行应用
cd java-rag
mvn spring-boot:run
```

#### 永久配置 (写入 shell 配置文件)

```bash
# 编辑 ~/.zshrc 或 ~/.bash_profile
echo 'export OPENAI_API_KEY="sk-xxx"' >> ~/.zshrc
echo 'export DASHSCOPE_API_KEY="sk-xxx"' >> ~/.zshrc

# 重新加载配置
source ~/.zshrc
```

### 方式 2: 直接修改配置文件

编辑 `src/main/resources/application.yml`:

```yaml
langchain4j:
  openai:
    api-key: sk-your-openai-key-here
    
  dashscope:
    api-key: sk-your-dashscope-key-here
```

⚠️ **注意**: 不建议在生产环境将 API Key 写死在配置文件中,推荐使用环境变量。

---

## 🔄 二、切换 AI 提供商

### 使用 DashScope (通义千问)

编辑 `src/main/resources/application.yml`:

```yaml
rag:
  provider: dashscope  # 使用通义千问
```

### 使用 OpenAI

```yaml
rag:
  provider: openai  # 使用 OpenAI
```

---

## 🎯 三、模型配置

### DashScope 可用模型

#### Chat 模型

```yaml
langchain4j:
  dashscope:
    chat-model:
      # 可选模型:
      # - qwen-turbo: 通义千问超大规模语言模型,支持中英文,超快响应
      # - qwen-plus: 通义千问增强版,性能更强
      # - qwen-max: 通义千问超大规模语言模型,效果最佳
      # - qwen-long: 超长上下文(最多1000万tokens)
      model-name: qwen-plus
      
      temperature: 0.5      # 温度,控制随机性 (0.0-1.0)
      max-tokens: 2000      # 最大生成 token 数
```

#### Embedding 模型

```yaml
langchain4j:
  dashscope:
    embedding-model:
      # 可选模型:
      # - text-embedding-v1: 通用文本向量,维度 1536
      # - text-embedding-v2: 升级版,性能更好
      # - text-embedding-v3: 最新版本 (推荐)
      model-name: text-embedding-v3
      
      dimensions: 1536      # 向量维度
```

### OpenAI 可用模型

```yaml
langchain4j:
  openai:
    chat-model:
      # 可选模型:
      # - gpt-4o: GPT-4 Omni,最新旗舰模型
      # - gpt-4o-mini: GPT-4 Omni mini,性价比高
      # - gpt-4-turbo: GPT-4 Turbo
      # - gpt-3.5-turbo: 经济实惠
      model-name: gpt-4o-mini
      
    embedding-model:
      # 可选模型:
      # - text-embedding-3-large: 3072 维,效果最佳
      # - text-embedding-3-small: 1536 维,经济实惠
      # - text-embedding-ada-002: 1536 维,旧版本
      model-name: text-embedding-3-large
      dimensions: 1536
```

---

## 🚀 四、完整配置示例

### 示例 1: 使用 DashScope

```yaml
# src/main/resources/application.yml
rag:
  provider: dashscope

langchain4j:
  dashscope:
    api-key: ${DASHSCOPE_API_KEY}
    chat-model:
      model-name: qwen-plus
      temperature: 0.5
      max-tokens: 2000
    embedding-model:
      model-name: text-embedding-v3
      dimensions: 1536

  pgvector:
    dimension: 1536  # 必须与 embedding 维度一致
```

### 示例 2: 使用 OpenAI

```yaml
rag:
  provider: openai

langchain4j:
  openai:
    api-key: ${OPENAI_API_KEY}
    chat-model:
      model-name: gpt-4o-mini
      temperature: 0.5
      max-tokens: 2000
    embedding-model:
      model-name: text-embedding-3-large
      dimensions: 1536

  pgvector:
    dimension: 1536
```

---

## 🔑 五、获取 API Key

### OpenAI API Key

1. 访问 [OpenAI Platform](https://platform.openai.com/)
2. 注册/登录账号
3. 进入 **API Keys** 页面
4. 点击 **Create new secret key**
5. 复制生成的 Key (格式: `sk-xxx`)

### DashScope API Key

1. 访问 [阿里云 DashScope](https://dashscope.aliyun.com/)
2. 注册/登录阿里云账号
3. 进入 **控制台** → **API-KEY 管理**
4. 点击 **创建新的API-KEY**
5. 复制生成的 Key (格式: `sk-xxx`)

---

## 💰 六、费用对比

### DashScope (通义千问) 定价

| 模型 | 输入价格 | 输出价格 | 说明 |
|------|----------|----------|------|
| qwen-turbo | ¥0.3/百万tokens | ¥0.6/百万tokens | 响应快,性价比高 |
| qwen-plus | ¥0.8/百万tokens | ¥2/百万tokens | 平衡性能与成本 |
| qwen-max | ¥20/百万tokens | ¥60/百万tokens | 最强效果 |
| text-embedding-v3 | ¥0.7/百万tokens | - | Embedding 模型 |

### OpenAI 定价

| 模型 | 输入价格 | 输出价格 |
|------|----------|----------|
| gpt-4o-mini | $0.15/百万tokens | $0.60/百万tokens |
| gpt-4o | $2.5/百万tokens | $10/百万tokens |
| text-embedding-3-large | $0.13/百万tokens | - |

**建议**: 
- 开发测试: 使用 **qwen-turbo** 或 **gpt-4o-mini**
- 生产环境: 使用 **qwen-plus** 或 **gpt-4o**

---

## 🧪 七、测试配置

### 测试 DashScope 连接

```bash
# 运行项目
mvn spring-boot:run

# 健康检查
curl http://localhost:8080/api/health

# 测试问答 (确保已上传 PDF)
curl -X POST http://localhost:8080/api/ask \
  -H "Content-Type: application/json" \
  -d '{
    "question": "\"测试公司\"2024年的营收是多少?",
    "kind": "number"
  }'
```

### 查看日志

应用启动时会显示使用的提供商:

```
INFO  - 初始化 ChatLanguageModel, 提供商: dashscope
INFO  - 创建 DashScope ChatModel: qwen-plus
INFO  - 初始化 EmbeddingModel, 提供商: dashscope
INFO  - 创建 DashScope EmbeddingModel: text-embedding-v3
```

---

## ❓ 八、常见问题

### Q1: API Key 无效

**症状**: 
```
java.lang.IllegalStateException: DASHSCOPE_API_KEY 未配置
```

**解决**: 
1. 检查环境变量是否设置: `echo $DASHSCOPE_API_KEY`
2. 重新启动终端并设置环境变量
3. 确认 Key 格式正确 (以 `sk-` 开头)

### Q2: 向量维度不匹配

**症状**: 
```
ERROR - Vector dimension mismatch: expected 1536, got 1024
```

**解决**: 
确保 `pgvector.dimension` 与 `embedding-model.dimensions` 一致:

```yaml
langchain4j:
  dashscope:
    embedding-model:
      dimensions: 1536  # 必须一致

  pgvector:
    dimension: 1536    # 必须一致
```

### Q3: 模型不存在

**症状**: 
```
ApiException: Model qwen-xxx does not exist
```

**解决**: 
检查模型名称拼写,参考 [DashScope 文档](https://help.aliyun.com/zh/dashscope/)。

### Q4: 切换提供商后错误

**症状**: 
切换 `provider` 后仍使用旧模型。

**解决**: 
1. 清理编译: `mvn clean`
2. 重启应用: `mvn spring-boot:run`

---

## 📚 九、参考资料

- [DashScope 官方文档](https://help.aliyun.com/zh/dashscope/)
- [DashScope Java SDK](https://github.com/aliyun/alibabacloud-dashscope-java-sdk)
- [LangChain4j 文档](https://docs.langchain4j.dev/)
- [OpenAI API 文档](https://platform.openai.com/docs/)

---

## 🎉 十、总结

现在你的项目支持:
- ✅ **OpenAI** (GPT-4, GPT-3.5)
- ✅ **DashScope** (通义千问 Qwen)
- ✅ **灵活切换** (只需修改一行配置)
- ✅ **统一接口** (无需改动业务代码)

开始使用:

```bash
# 1. 设置 API Key
export DASHSCOPE_API_KEY="your-key-here"

# 2. 修改配置文件
# rag.provider: dashscope

# 3. 运行应用
mvn spring-boot:run
```

**Enjoy coding! 🚀**
