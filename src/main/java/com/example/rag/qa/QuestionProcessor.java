package com.example.rag.qa;

import com.example.rag.chat.ChatMemoryService;
import com.example.rag.model.Answer;
import com.example.rag.model.Question;
import com.example.rag.model.RetrievalResult;
import com.example.rag.retrieval.VectorRetriever;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 问题处理器
 * 对应 Python 中的 QuestionsProcessor 类
 * 
 * 核心功能:
 * 1. 提取问题中的公司名
 * 2. 检索相关上下文
 * 3. 调用 LLM 生成答案（支持对话历史）
 * 4. 解析和验证答案
 * 5. 支持 OpenAI 和 DashScope
 */
@Slf4j
@Service
public class QuestionProcessor {
    
    @Value("${rag.retrieval.top-k:10}")
    private int topK;
    
    @Autowired
    private VectorRetriever vectorRetriever;
    
    @Autowired
    private PromptBuilder promptBuilder;
    
    @Autowired
    private ChatModel chatModel;
    
    @Autowired(required = false)
    private ChatMemoryService chatMemoryService;
    
    private ObjectMapper objectMapper;
    
    /**
     * 初始化
     */
    @PostConstruct
    public void init() {
        this.objectMapper = new ObjectMapper();
        log.info("QuestionProcessor 初始化完成");
    }
    
    /**
     * 处理单个问题（支持对话历史）
     * 
     * @param question 问题对象
     * @param sessionId 会话 ID（可选）
     * @return 答案对象
     */
    public Answer processQuestion(Question question, String sessionId) {
        log.info("处理问题: {}, 会话: {}", question.getText(), sessionId);
        
        try {
            // 1. 提取公司名， 提取用引号包围的部分
            String companyName = extractCompanyName(question.getText());
            if (companyName == null) {
                throw new IllegalArgumentException("无法从问题中提取公司名");
            }
            
            question.setCompanyName(companyName);
            
            // 2. 检索相关上下文
            List<RetrievalResult> retrievalResults = 
                vectorRetriever.retrieveByCompanyName(companyName, question.getText(), topK);
            
            if (retrievalResults.isEmpty()) {
                throw new IllegalArgumentException("未找到相关上下文");
            }
            
            // 3. 构建 Prompt
            String systemPrompt = promptBuilder.buildSystemPrompt(question.getKind());
            String ragContext = promptBuilder.formatRetrievalContext(retrievalResults);
            String userPrompt = promptBuilder.buildUserPrompt(ragContext, question.getText());
            
            // 4. 调用 LLM（带对话历史）
            String llmResponse;
            
            if (sessionId != null && chatMemoryService != null) {
                // 使用对话历史
                llmResponse = generateWithHistory(systemPrompt, userPrompt, sessionId);
            } else {
                // 不使用对话历史
                llmResponse = chatModel.chat(systemPrompt + "\n\n" + userPrompt);
            }
            
            log.info("LLM 原始响应: {}", llmResponse);
            
            // 5. 解析答案
            Answer answer = parseAnswer(llmResponse, question.getKind());
            
            // 6. 验证页码引用
            answer.setRelevantPages(
                validatePageReferences(answer.getRelevantPages(), retrievalResults)
            );
            
            // 7. 保存 AI 回复到历史（如果有会话）
            if (sessionId != null && chatMemoryService != null) {
                chatMemoryService.addAiMessage(sessionId, llmResponse);
            }
            
            log.info("问题处理完成，最终答案: {}", answer.getFinalAnswer());
            
            return answer;
            
        } catch (Exception e) {
            log.error("处理问题失败", e);
            
            return Answer.builder()
                .stepByStepAnalysis("错误: " + e.getMessage())
                .reasoningSummary("处理失败")
                .relevantPages(List.of())
                .finalAnswer("N/A")
                .build();
        }
    }
    
    /**
     * 处理单个问题（无对话历史，兼容旧接口）
     * 
     * @param question 问题对象
     * @return 答案对象
     */
    public Answer processQuestion(Question question) {
        return processQuestion(question, null);
    }
    
    /**
     * 使用对话历史生成回复
     * 
     * @param systemPrompt 系统提示词
     * @param userPrompt 用户提示词
     * @param sessionId 会话 ID
     * @return LLM 响应
     */
    private String generateWithHistory(String systemPrompt, String userPrompt, String sessionId) {
        // 获取对话历史
        List<ChatMessage> history = chatMemoryService.getMessages(sessionId);
        
        // 如果是新会话，添加系统消息
        if (history.isEmpty()) {
            chatMemoryService.addSystemMessage(sessionId, systemPrompt);
            log.debug("会话 {} 添加系统提示词", sessionId);
        }
        
        // 添加当前用户消息
        chatMemoryService.addUserMessage(sessionId, userPrompt);
        
        // 构建完整的对话上下文
        StringBuilder fullContext = new StringBuilder();
        
        // 重新获取更新后的历史
        history = chatMemoryService.getMessages(sessionId);
        
        for (ChatMessage message : history) {
            String role = message.type().toString();
            String content = extractTextFromMessage(message);
            fullContext.append(role).append(": ").append(content).append("\n\n");
        }
        
        log.debug("对话历史长度: {} 条消息", history.size());
        
        // 调用 LLM（直接使用历史消息列表）
        Response<AiMessage> response = generateFromMessages(history);
        return response.content().text();
    }
    
    /**
     * 从消息列表生成回复（辅助方法）
     */
    private Response<AiMessage> generateFromMessages(List<ChatMessage> messages) {
        // 尝试调用 generate 方法（如果 ChatModel 支持）
        if (chatModel instanceof com.example.rag.ai.DashScopeChatModel dashScopeModel) {
            return dashScopeModel.generate(messages);
        } else {
            // 对于其他 ChatModel 实现，需要适配
            // 这里假设 ChatModel 有 generate(List<ChatMessage>) 方法
            // 如果没有，可能需要使用反射或其他方式
            throw new UnsupportedOperationException("不支持的 ChatModel 类型: " + chatModel.getClass().getName());
        }
    }
    
    /**
     * 从 ChatMessage 中提取文本内容
     */
    private String extractTextFromMessage(ChatMessage message) {
        if (message instanceof UserMessage userMsg) {
            return userMsg.singleText();
        } else if (message instanceof AiMessage aiMsg) {
            return aiMsg.text();
        } else if (message instanceof SystemMessage systemMsg) {
            return systemMsg.text();
        } else {
            return "";
        }
    }
    
    /**
     * 从问题中提取公司名
     * 假设公司名在引号中,如: "某某公司"
     * 
     * @param questionText 问题文本
     * @return 公司名
     */
    private String extractCompanyName(String questionText) {
        // 匹配中文引号
        Pattern pattern = Pattern.compile("[\"\"']([^\"\"']+)[\"\"']");
        Matcher matcher = pattern.matcher(questionText);
        
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        // 匹配英文引号
        pattern = Pattern.compile("\"([^\"]+)\"");
        matcher = pattern.matcher(questionText);
        
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        return null;
    }
    
    /**
     * 解析 LLM 响应为 Answer 对象
     */
    private Answer parseAnswer(String llmResponse, String kind) {
        try {
            // 尝试提取 JSON 部分
            String jsonStr = extractJson(llmResponse);
            
            // 解析 JSON
            var jsonNode = objectMapper.readTree(jsonStr);
            
            Answer.AnswerBuilder builder = Answer.builder()
                .stepByStepAnalysis(jsonNode.get("step_by_step_analysis").asText())
                .reasoningSummary(jsonNode.get("reasoning_summary").asText());
            
            // 解析 relevant_pages
            List<Integer> pages = objectMapper.convertValue(
                jsonNode.get("relevant_pages"), 
                objectMapper.getTypeFactory().constructCollectionType(List.class, Integer.class)
            );
            builder.relevantPages(pages);
            
            // 解析 final_answer (根据类型)
            var finalAnswerNode = jsonNode.get("final_answer");
            Object finalAnswer = parseFinalAnswer(finalAnswerNode, kind);
            builder.finalAnswer(finalAnswer);
            
            return builder.build();
            
        } catch (Exception e) {
            log.error("解析答案失败", e);
            
            return Answer.builder()
                .stepByStepAnalysis("解析失败: " + e.getMessage())
                .reasoningSummary("JSON 解析错误")
                .relevantPages(List.of())
                .finalAnswer("N/A")
                .build();
        }
    }
    
    /**
     * 从 LLM 响应中提取 JSON 字符串
     */
    private String extractJson(String response) {
        // 移除 Markdown 代码块标记
        response = response.replaceAll("```json\\n", "").replaceAll("```\\n", "").replaceAll("```", "");
        
        // 提取 JSON 对象
        int start = response.indexOf("{");
        int end = response.lastIndexOf("}");
        
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }
        
        return response;
    }
    
    /**
     * 根据问题类型解析最终答案
     */
    private Object parseFinalAnswer(com.fasterxml.jackson.databind.JsonNode node, String kind) {
        if (node.isNull() || node.asText().equals("N/A")) {
            return "N/A";
        }
        
        return switch (kind) {
            case "number" -> node.isNumber() ? node.asDouble() : "N/A";
            case "boolean" -> node.asBoolean();
            case "names" -> node.isArray() ? 
                objectMapper.convertValue(node, List.class) : List.of();
            default -> node.asText();
        };
    }
    
    /**
     * 验证页码引用是否在检索结果中
     */
    private List<Integer> validatePageReferences(
            List<Integer> claimedPages, 
            List<RetrievalResult> retrievalResults) {
        
        if (claimedPages == null || claimedPages.isEmpty()) {
            // 如果没有引用页码,取检索结果的前2页
            return retrievalResults.stream()
                .limit(2)
                .map(RetrievalResult::getPage)
                .distinct()
                .toList();
        }
        
        // 获取检索结果中的所有页码
        List<Integer> retrievedPages = retrievalResults.stream()
            .map(RetrievalResult::getPage)
            .distinct()
            .toList();
        
        // 过滤出真实存在的页码
        return claimedPages.stream()
            .filter(retrievedPages::contains)
            .distinct()
            .limit(8)  // 最多保留8个页码
            .toList();
    }
}
