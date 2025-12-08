package com.example.rag.qa;

import com.example.rag.model.RetrievalResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Prompt 构建器
 * 对应 Python 中的 prompts.py
 * 
 * 核心功能:
 * 1. 根据问题类型构建不同的 system prompt
 * 2. 格式化 RAG 上下文
 * 3. 构建完整的用户提示词
 */
@Slf4j
@Component
public class PromptBuilder {
    
    /**
     * 构建 System Prompt
     * 
     * @param questionKind 问题类型: string, number, boolean, names
     * @return System Prompt
     */
    public String buildSystemPrompt(String questionKind) {
        String baseInstruction = """
            你是一个RAG(检索增强生成)问答系统。
            你的任务是仅基于公司年报中RAG检索到的相关页面内容,回答给定问题。
            
            在给出最终答案前,请详细分步思考,尤其关注问题措辞。
            - 注意:答案可能与问题表述不同。
            - 问题可能是模板生成的,有时对该公司不适用。
            """;
        
        String schemaInst = switch (questionKind) {
            case "number" -> """
                
                你的回答必须是JSON,并严格遵循如下Schema:
                {
                  "step_by_step_analysis": "详细分步推理过程,至少5步,150字以上",
                  "reasoning_summary": "简要总结分步推理过程,约50字",
                  "relevant_pages": [页码列表],
                  "final_answer": 数值或"N/A"
                }
                
                **数值提取规则:**
                - 百分比: 58.3% → 58.3
                - 负数: (2,124,837) → -2124837
                - 千为单位: 4970.5(千美元) → 4970500
                - 币种不符返回'N/A'
                - 需要计算推导则返回'N/A'
                """;
                
            case "boolean" -> """
                
                你的回答必须是JSON,并严格遵循如下Schema:
                {
                  "step_by_step_analysis": "详细分步推理过程,至少5步,150字以上",
                  "reasoning_summary": "简要总结分步推理过程,约50字",
                  "relevant_pages": [页码列表],
                  "final_answer": true 或 false
                }
                
                **布尔值规则:**
                - 问题问某事是否发生,且上下文有相关信息但未发生,则返回false
                - 上下文明确说明发生了,返回true
                """;
                
            case "names" -> """
                
                你的回答必须是JSON,并严格遵循如下Schema:
                {
                  "step_by_step_analysis": "详细分步推理过程,至少5步,150字以上",
                  "reasoning_summary": "简要总结分步推理过程,约50字",
                  "relevant_pages": [页码列表],
                  "final_answer": ["姓名1", "姓名2"] 或 "N/A"
                }
                
                **名单提取规则:**
                - 如问职位变动,仅返回职位名称,不含姓名
                - 如问姓名,仅返回上下文中的全名
                - 如问新产品,仅返回产品名
                """;
                
            default -> """
                
                你的回答必须是JSON,并严格遵循如下Schema:
                {
                  "step_by_step_analysis": "详细分步推理过程,至少5步,150字以上",
                  "reasoning_summary": "简要总结分步推理过程,约50字",
                  "relevant_pages": [页码列表],
                  "final_answer": "答案文本" 或 "N/A"
                }
                """;
        };
        
        return baseInstruction + schemaInst;
    }
    
    /**
     * 格式化检索结果为 RAG 上下文
     * 
     * @param retrievalResults 检索结果列表
     * @return 格式化的上下文字符串
     */
    public String formatRetrievalContext(List<RetrievalResult> retrievalResults) {
        if (retrievalResults == null || retrievalResults.isEmpty()) {
            return "无相关上下文";
        }
        
        StringBuilder context = new StringBuilder();
        
        for (RetrievalResult result : retrievalResults) {
            context.append(String.format(
                "Text retrieved from page %d:\n\"\"\"\n%s\n\"\"\"\n\n---\n\n",
                result.getPage(),
                result.getText()
            ));
        }
        
        return context.toString().trim();
    }
    
    /**
     * 构建用户提示词
     * 
     * @param context RAG 上下文
     * @param question 问题
     * @return 用户提示词
     */
    public String buildUserPrompt(String context, String question) {
        return String.format("""
            以下是上下文:
            \"\"\"
            %s
            \"\"\"
            
            ---
            
            以下是问题:
            "%s"
            """, context, question);
    }
}
