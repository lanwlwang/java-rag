package com.example.rag.document;

import com.example.rag.model.Document;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF 解析器 (使用 Apache Tika)
 * 对应 Python 中的 PDFParser 类
 * 
 * 核心功能:
 * 1. 解析 PDF 文件
 * 2. 提取文本内容
 * 3. 生成文档元信息 (SHA1、文件名等)
 * 
 * 优势:
 * - 支持更多文档格式 (PDF, Word, Excel, PPT 等)
 * - 更好的文本提取质量
 * - 自动检测文件类型
 */
@Slf4j
@Component
public class PDFParser {
    
    /**
     * 解析 PDF 文件
     * 
     * @param pdfFile PDF 文件
     * @param companyName 公司名称
     * @return 文档对象
     */
    public Document parsePdf(File pdfFile, String companyName) throws IOException {
        log.info("开始解析 PDF (使用 Tika): {}", pdfFile.getName());
        
        // 计算文件 SHA1
        String sha1 = calculateSHA1(pdfFile);
        
        // 创建元信息
        Document.MetaInfo metaInfo = Document.MetaInfo.builder()
            .sha1(sha1)
            .companyName(companyName)
            .fileName(pdfFile.getName())
            .build();
        
        // 解析 PDF 内容
        List<Document.Page> pages = extractPagesWithTika(pdfFile);
        
        Document.Content content = Document.Content.builder()
            .pages(pages)
            .chunks(new ArrayList<>())  // 初始为空，后续分块时填充
            .build();
        
        Document document = Document.builder()
            .metaInfo(metaInfo)
            .content(content)
            .build();
        
        log.info("PDF 解析完成，共 {} 页", pages.size());
        return document;
    }
    
    /**
     * 使用 Tika 提取 PDF 文本 (按页分割)
     * 
     * @param pdfFile PDF 文件
     * @return 页面列表
     */
    private List<Document.Page> extractPagesWithTika(File pdfFile) throws IOException {
        List<Document.Page> pages = new ArrayList<>();
        
        try (FileInputStream inputStream = new FileInputStream(pdfFile)) {
            // 创建 Tika 解析器
            AutoDetectParser parser = new AutoDetectParser();
            BodyContentHandler handler = new BodyContentHandler(-1); // 无限制大小
            Metadata metadata = new Metadata();
            ParseContext context = new ParseContext();
            
            // 解析整个文档
            parser.parse(inputStream, handler, metadata, context);
            
            // 获取完整文本
            String fullText = handler.toString();
            
            // 获取总页数
            String pageCountStr = metadata.get("xmpTPg:NPages");
            if (pageCountStr == null) {
                pageCountStr = metadata.get("pdf:Page-Count");
            }
            if (pageCountStr == null) {
                pageCountStr = metadata.get("meta:page-count");
            }
            
            int totalPages = 1; // 默认至少 1 页
            if (pageCountStr != null) {
                try {
                    totalPages = Integer.parseInt(pageCountStr);
                } catch (NumberFormatException e) {
                    log.warn("无法解析页数，使用默认值 1: {}", pageCountStr);
                }
            }
            
            // 由于 Tika 不直接支持按页提取，我们将文本平均分割
            // 这是一个简化方案，如果需要精确按页提取，可以使用 PDFBox 或其他专门的 PDF 库
            if (totalPages > 1) {
                pages = splitTextIntoPages(fullText, totalPages);
            } else {
                // 只有一页
                Document.Page page = Document.Page.builder()
                    .page(1)
                    .text(fullText)
                    .build();
                pages.add(page);
            }
            
            log.debug("Tika 提取到元数据: Title={}, Author={}, Pages={}", 
                metadata.get("dc:title"), 
                metadata.get("Author"),
                pageCountStr);
            
        } catch (Exception e) {
            log.error("Tika 解析 PDF 失败", e);
            throw new IOException("解析 PDF 失败: " + e.getMessage(), e);
        }
        
        return pages;
    }
    
    /**
     * 将全文本分割成多页
     * 这是一个简化实现，按字符数平均分配
     * 
     * @param fullText 完整文本
     * @param totalPages 总页数
     * @return 页面列表
     */
    private List<Document.Page> splitTextIntoPages(String fullText, int totalPages) {
        List<Document.Page> pages = new ArrayList<>();
        
        // 尝试通过换页符分割 (某些 PDF 提取时会保留)
        String[] sections = fullText.split("\\f"); // \f 是换页符
        
        if (sections.length > 1 && sections.length <= totalPages * 2) {
            // 如果有换页符且数量合理，使用换页符分割
            for (int i = 0; i < sections.length && i < totalPages; i++) {
                if (sections[i].trim().isEmpty()) {
                    continue;
                }
                Document.Page page = Document.Page.builder()
                    .page(i + 1)
                    .text(sections[i].trim())
                    .build();
                pages.add(page);
            }
        } else {
            // 否则按行数平均分配
            String[] lines = fullText.split("\\n");
            int linesPerPage = Math.max(1, lines.length / totalPages);
            
            for (int pageNum = 1; pageNum <= totalPages; pageNum++) {
                int startLine = (pageNum - 1) * linesPerPage;
                int endLine = Math.min(pageNum * linesPerPage, lines.length);
                
                StringBuilder pageText = new StringBuilder();
                for (int i = startLine; i < endLine; i++) {
                    pageText.append(lines[i]).append("\n");
                }
                
                Document.Page page = Document.Page.builder()
                    .page(pageNum)
                    .text(pageText.toString().trim())
                    .build();
                pages.add(page);
            }
        }
        
        return pages;
    }
    
    /**
     * 计算文件的 SHA1 哈希值
     * 
     * @param file 文件
     * @return SHA1 字符串
     */
    private String calculateSHA1(File file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] fileBytes = Files.readAllBytes(file.toPath());
            byte[] hashBytes = digest.digest(fileBytes);
            
            // 转换为十六进制字符串
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
            
        } catch (NoSuchAlgorithmException | IOException e) {
            log.error("计算 SHA1 失败", e);
            return "";
        }
    }
}
