# PDF 解析器迁移：PDFBox → Tika

## 变更说明

已将 PDF 解析工具从 **Apache PDFBox** 迁移到 **Apache Tika**。

## 为什么使用 Tika？

### Tika 的优势

1. **🌐 更广泛的格式支持**
   - PDF, Word (doc/docx), Excel (xls/xlsx)
   - PowerPoint (ppt/pptx), HTML, XML
   - 图片（提取元数据和文本）
   - 1000+ 种文件格式

2. **📊 更好的文本提取**
   - 自动处理复杂布局
   - 更好的表格识别
   - 保留文档结构

3. **🔍 自动类型检测**
   - 无需指定文件类型
   - 基于文件内容而非扩展名

4. **📝 丰富的元数据提取**
   - 作者、标题、创建日期
   - 页数、关键词等

### Tika vs PDFBox

| 特性 | Apache Tika | Apache PDFBox |
|-----|-------------|---------------|
| 支持格式 | 1000+ | 仅 PDF |
| 易用性 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ |
| PDF 解析 | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| 表格提取 | ⭐⭐⭐⭐ | ⭐⭐⭐ |
| 按页提取 | ⭐⭐⭐ (需处理) | ⭐⭐⭐⭐⭐ |
| 元数据 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ |

## 代码变更

### 1. Maven 依赖

**移除 (pom.xml)**:
```xml
<dependency>
    <groupId>org.apache.pdfbox</groupId>
    <artifactId>pdfbox</artifactId>
    <version>3.0.1</version>
</dependency>
```

**添加 (pom.xml)**:
```xml
<dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-core</artifactId>
    <version>2.9.1</version>
</dependency>

<dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-parsers-standard-package</artifactId>
    <version>2.9.1</version>
</dependency>
```

### 2. PDFParser.java 更新

**主要变化**:
- 使用 `AutoDetectParser` 替代 `PDFBox`
- 自动提取元数据（页数、作者等）
- 文本分页策略改进

**新特性**:
```java
// 自动检测文件类型
AutoDetectParser parser = new AutoDetectParser();

// 提取元数据
Metadata metadata = new Metadata();
String pageCount = metadata.get("xmpTPg:NPages");
String author = metadata.get("Author");
String title = metadata.get("dc:title");
```

## 使用方式

### 基本使用（无变化）

```java
@Autowired
private PDFParser pdfParser;

// 解析 PDF
Document doc = pdfParser.parsePdf(pdfFile, "公司名");
```

### 扩展到其他格式

得益于 Tika 的自动检测，可以轻松扩展支持其他格式：

```java
// 支持 Word 文档
Document wordDoc = pdfParser.parsePdf(wordFile, "公司名");

// 支持 Excel 表格
Document excelDoc = pdfParser.parsePdf(excelFile, "公司名");
```

## 页面分割说明

### 挑战
Tika 默认提取整个文档文本，不直接支持按页提取。

### 解决方案
实现了智能分页策略：

1. **换页符检测**：优先使用 `\f` 换页符分割
2. **行数平均分配**：如果没有换页符，按行数平均分配到各页
3. **元数据辅助**：使用 PDF 元数据中的页数信息

### 改进建议

如果需要更精确的按页提取，可以考虑：

1. **混合方案**：使用 Tika 提取文本 + PDFBox 获取页面信息
2. **专用库**：对 PDF 使用 PDFBox，对其他格式使用 Tika
3. **自定义处理器**：编写 Tika 自定义内容处理器

## 依赖更新

运行以下命令更新依赖：

```bash
mvn clean install
```

## 测试建议

1. **测试现有 PDF**：
   ```bash
   curl -X POST http://localhost:8080/api/rag/upload-pdf \
     -F "file=@test.pdf" \
     -F "companyName=测试公司"
   ```

2. **测试新格式** (未来扩展):
   ```bash
   # Word 文档
   curl -X POST http://localhost:8080/api/rag/upload-document \
     -F "file=@report.docx" \
     -F "companyName=测试公司"
   ```

## 性能影响

- **启动时间**：略微增加（Tika 加载解析器）
- **解析速度**：与 PDFBox 相当
- **内存占用**：略微增加（支持更多格式需要更多解析器）

## 回滚方案

如果需要回滚到 PDFBox：

1. 恢复 `pom.xml` 中的依赖
2. 恢复 `PDFParser.java` 的旧版本
3. 运行 `mvn clean install`

## 未来扩展

使用 Tika 后，可以轻松添加：

- ✅ Word 文档处理
- ✅ Excel 表格处理  
- ✅ PowerPoint 演示文稿
- ✅ HTML 网页内容
- ✅ 图片 OCR (配合 Tesseract)

## 参考资料

- [Apache Tika 官网](https://tika.apache.org/)
- [Tika 支持的格式列表](https://tika.apache.org/2.9.1/formats.html)
- [Tika Java API 文档](https://tika.apache.org/2.9.1/api/)
