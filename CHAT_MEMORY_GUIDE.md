# 对话记忆功能使用指南

## 📋 功能概述

系统现已支持 **对话记忆**（Chat Memory）功能，允许进行多轮对话，AI 能记住之前的问答内容，提供更连贯的对话体验。

## ✨ 核心特性

1. **会话管理** - 支持多用户、多会话并行
2. **历史记录** - 自动保存对话历史（用户问题 + AI 回复）
3. **消息窗口** - 自动限制历史长度，避免上下文过长
4. **自动过期** - 超时未使用的会话自动清理
5. **兼容旧接口** - 不提供会话 ID 时按无记忆模式工作

---

## 🚀 快速开始

### 1. 创建新会话

```bash
curl -X POST http://localhost:8087/api/chat/new
```

**响应**：
```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "message": "新会话创建成功"
}
```

### 2. 使用会话进行对话

**第一轮对话**：
```bash
curl -X POST http://localhost:8087/api/ask \
  -H "Content-Type: application/json" \
  -d '{
    "question": "\"中芯国际\"2024年的营收是多少？",
    "kind": "number",
    "sessionId": "550e8400-e29b-41d4-a716-446655440000"
  }'
```

**响应**：
```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "answer": {
    "stepByStepAnalysis": "...",
    "reasoningSummary": "根据财报数据...",
    "relevantPages": [5, 12],
    "finalAnswer": 450.5
  }
}
```

**第二轮对话（AI 会记住第一轮）**：
```bash
curl -X POST http://localhost:8087/api/ask \
  -H "Content-Type: application/json" \
  -d '{
    "question": "相比去年增长了多少？",
    "kind": "number",
    "sessionId": "550e8400-e29b-41d4-a716-446655440000"
  }'
```

💡 **注意**：第二个问题没有提到"中芯国际"和"营收"，但 AI 能理解上下文。

---

## 📚 API 详解

### 1. `POST /api/ask` - 问答（支持记忆）

**请求体**：
```json
{
  "question": "问题文本",
  "kind": "string|number|boolean|names",
  "sessionId": "可选，会话ID"
}
```

**说明**：
- `sessionId` 为空或不提供：每次都是新对话（无记忆）
- `sessionId` 有效：使用对话历史

**响应**：
```json
{
  "sessionId": "会话ID",
  "answer": {
    "stepByStepAnalysis": "分析过程",
    "reasoningSummary": "推理总结",
    "relevantPages": [1, 2, 3],
    "finalAnswer": "答案"
  }
}
```

### 2. `POST /api/chat/new` - 创建新会话

**响应**：
```json
{
  "sessionId": "新生成的会话ID",
  "message": "新会话创建成功"
}
```

### 3. `DELETE /api/chat/{sessionId}/clear` - 清空会话历史

保留会话但清空历史记录。

**请求**：
```bash
curl -X DELETE http://localhost:8087/api/chat/550e8400-xxx/clear
```

**响应**：
```json
{
  "sessionId": "550e8400-xxx",
  "message": "会话历史已清空"
}
```

### 4. `DELETE /api/chat/{sessionId}` - 删除会话

完全删除会话。

**请求**：
```bash
curl -X DELETE http://localhost:8087/api/chat/550e8400-xxx
```

### 5. `GET /api/chat/stats` - 会话统计

**响应**：
```json
{
  "activeSessions": 5
}
```

---

## 🎯 使用场景

### 场景 1：多轮问答

```bash
# 第1轮：询问公司信息
curl -X POST http://localhost:8087/api/ask \
  -H "Content-Type: application/json" \
  -d '{
    "question": "\"腾讯\"的主营业务是什么？",
    "sessionId": "abc-123"
  }'

# 第2轮：追问细节（AI 知道"它"指腾讯）
curl -X POST http://localhost:8087/api/ask \
  -H "Content-Type: application/json" \
  -d '{
    "question": "它在游戏领域的收入占比多少？",
    "sessionId": "abc-123"
  }'

# 第3轮：对比（AI 记得前面的数据）
curl -X POST http://localhost:8087/api/ask \
  -H "Content-Type: application/json" \
  -d '{
    "question": "和去年相比呢？",
    "sessionId": "abc-123"
  }'
```

### 场景 2：单次问答（无记忆）

```bash
# 不提供 sessionId，每次都是独立问答
curl -X POST http://localhost:8087/api/ask \
  -H "Content-Type: application/json" \
  -d '{
    "question": "\"阿里巴巴\"2024年营收？",
    "kind": "number"
  }'
```

**响应会包含新生成的 sessionId**，但下次不使用它就不会有记忆。

### 场景 3：重置对话

```bash
# 开始新的话题前清空历史
curl -X DELETE http://localhost:8087/api/chat/abc-123/clear

# 现在可以开始新的话题
curl -X POST http://localhost:8087/api/ask \
  -H "Content-Type: application/json" \
  -d '{
    "question": "\"华为\"的研发投入是多少？",
    "sessionId": "abc-123"
  }'
```

---

## ⚙️ 配置说明

在 `application.yml` 中配置：

```yaml
rag:
  chat:
    memory:
      max-messages: 20           # 每个会话保留的最大消息数
      session-timeout: 1800000   # 会话超时（毫秒，30分钟）
```

**参数说明**：

- **max-messages**：
  - 包括系统消息、用户消息、AI 回复
  - 20 条 ≈ 保留最近 6-7 轮对话
  - 超过限制时自动删除最早的消息

- **session-timeout**：
  - 超过这个时间未使用的会话会被清理
  - 默认 30 分钟 = 1800000 毫秒

---

## 🔍 工作原理

### 对话流程

```
1. 用户发送问题 + sessionId
       ↓
2. 系统检查会话是否存在
   - 存在：加载历史
   - 不存在：创建新会话
       ↓
3. 将用户问题添加到历史
       ↓
4. 构建完整上下文：
   系统提示 + 历史对话 + 当前问题 + RAG 检索结果
       ↓
5. 调用 LLM 生成回复
       ↓
6. 将 AI 回复保存到历史
       ↓
7. 返回答案 + sessionId
```

### 消息结构

```
会话历史示例：

[SystemMessage] "你是一个财报分析助手..."
[UserMessage]   "\"腾讯\"的营收是多少？"
[AiMessage]     "根据财报，腾讯2024年营收为..."
[UserMessage]   "它的净利润呢？"
[AiMessage]     "腾讯2024年净利润为..."
```

LLM 收到的是完整的对话历史，所以能理解"它"指的是"腾讯"。

---

## 📊 性能考虑

### 内存使用

- 每个会话约占用 **5-50 KB**（取决于对话长度）
- 1000 个活跃会话约占用 **5-50 MB**
- 自动清理机制避免内存泄漏

### Token 消耗

- 每轮对话会发送完整历史给 LLM
- 历史越长，Token 消耗越多
- 建议 `max-messages` 设置为 10-30

### 响应延迟

- 带历史的请求比无历史略慢（多了历史加载）
- 增加约 10-50ms（取决于历史长度）

---

## 🛠️ 故障排查

### 问题 1：会话不存在

**错误**：日志显示 "会话不存在，自动创建"

**原因**：
- sessionId 错误
- 会话已过期被清理

**解决**：
- 创建新会话重新开始
- 增加 `session-timeout` 配置

### 问题 2：上下文太长

**现象**：LLM 返回错误或截断

**原因**：历史消息 + RAG 上下文超过 LLM 限制

**解决**：
```yaml
rag:
  chat:
    memory:
      max-messages: 10  # 减少历史长度
```

### 问题 3：AI 回答不连贯

**原因**：
- sessionId 不一致（每次用了不同的 ID）
- 会话被清空

**解决**：
- 确保同一对话线程使用同一个 sessionId
- 检查会话是否超时

---

## 🧪 测试示例

### Python 测试脚本

```python
import requests
import json

BASE_URL = "http://localhost:8087/api"

# 1. 创建会话
response = requests.post(f"{BASE_URL}/chat/new")
session_id = response.json()["sessionId"]
print(f"会话ID: {session_id}")

# 2. 第一轮对话
response = requests.post(
    f"{BASE_URL}/ask",
    json={
        "question": '"中芯国际"的营收是多少？',
        "kind": "number",
        "sessionId": session_id
    }
)
print("第1轮:", response.json()["answer"]["finalAnswer"])

# 3. 第二轮对话（追问）
response = requests.post(
    f"{BASE_URL}/ask",
    json={
        "question": "净利润呢？",  # AI 知道指的是中芯国际
        "kind": "number",
        "sessionId": session_id
    }
)
print("第2轮:", response.json()["answer"]["finalAnswer"])

# 4. 清空历史
requests.delete(f"{BASE_URL}/chat/{session_id}/clear")
print("历史已清空")
```

### JavaScript 测试

```javascript
const BASE_URL = "http://localhost:8087/api";

async function testChat() {
  // 创建会话
  const newSession = await fetch(`${BASE_URL}/chat/new`, {
    method: "POST"
  }).then(r => r.json());
  
  const sessionId = newSession.sessionId;
  console.log("会话ID:", sessionId);
  
  // 第一轮
  const answer1 = await fetch(`${BASE_URL}/ask`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      question: '"阿里巴巴"的员工人数是多少？',
      sessionId: sessionId
    })
  }).then(r => r.json());
  
  console.log("第1轮:", answer1.answer.finalAnswer);
  
  // 第二轮（追问）
  const answer2 = await fetch(`${BASE_URL}/ask`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      question: "研发人员占比多少？",
      sessionId: sessionId
    })
  }).then(r => r.json());
  
  console.log("第2轮:", answer2.answer.finalAnswer);
}

testChat();
```

---

## 📖 最佳实践

1. **会话管理**
   - 前端保存 sessionId（localStorage/cookie）
   - 每个用户使用独立的 sessionId
   - 定期清理无用会话

2. **对话设计**
   - 首轮问题要明确（包含公司名、具体指标）
   - 后续可以使用代词（"它"、"这个"）
   - 必要时重新创建会话避免上下文混乱

3. **性能优化**
   - 不是所有问答都需要记忆
   - 简单查询可以不使用 sessionId
   - 合理设置 `max-messages` 平衡效果和成本

4. **用户体验**
   - 提供"清空历史"按钮
   - 显示对话轮次数
   - 提示会话即将过期

---

## 🔄 与其他系统集成

### Web 应用

```javascript
// 初始化
let currentSessionId = localStorage.getItem('chatSessionId');

// 发送消息
async function sendMessage(question) {
  if (!currentSessionId) {
    const newSession = await createNewSession();
    currentSessionId = newSession.sessionId;
    localStorage.setItem('chatSessionId', currentSessionId);
  }
  
  const response = await fetch('/api/ask', {
    method: 'POST',
    body: JSON.stringify({
      question: question,
      sessionId: currentSessionId
    })
  });
  
  return response.json();
}

// 重置对话
function resetChat() {
  currentSessionId = null;
  localStorage.removeItem('chatSessionId');
}
```

### 移动应用

```kotlin
// Android 示例
class ChatRepository {
    private var sessionId: String? = null
    
    suspend fun ask(question: String): Answer {
        if (sessionId == null) {
            sessionId = createNewSession()
        }
        
        return apiService.ask(
            QuestionRequest(
                question = question,
                sessionId = sessionId
            )
        )
    }
    
    fun clearHistory() {
        sessionId?.let { 
            apiService.clearSession(it) 
        }
    }
}
```

---

## 📝 总结

对话记忆功能让 RAG 系统具备了 **多轮对话** 能力，用户可以：

✅ 进行连续提问而无需重复上下文  
✅ 使用代词和简化的表达  
✅ 获得更自然的对话体验  
✅ 灵活控制会话生命周期

立即试用，让你的 RAG 系统更智能！🚀
