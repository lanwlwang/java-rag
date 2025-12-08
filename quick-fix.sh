#!/bin/bash

# ============================================
# 快速修复脚本
# ============================================
# 用途: 一键修复数据库维度问题
# 使用: bash quick-fix.sh
# ============================================

set -e  # 遇到错误立即退出

echo "======================================"
echo "   Java RAG 系统快速修复脚本"
echo "======================================"
echo ""

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 数据库配置
DB_HOST="localhost"
DB_PORT="5432"
DB_NAME="rag_db"
DB_USER="postgres"

echo -e "${YELLOW}步骤 1/4: 检查数据库连接...${NC}"
if psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -c "SELECT 1;" > /dev/null 2>&1; then
    echo -e "${GREEN}✓ 数据库连接成功${NC}"
else
    echo -e "${RED}✗ 数据库连接失败，请检查 PostgreSQL 是否运行${NC}"
    exit 1
fi

echo ""
echo -e "${YELLOW}步骤 2/4: 删除旧表...${NC}"
psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -c "DROP TABLE IF EXISTS rag_embeddings CASCADE;" > /dev/null 2>&1
echo -e "${GREEN}✓ 旧表已删除${NC}"

echo ""
echo -e "${YELLOW}步骤 3/4: 验证删除...${NC}"
TABLE_EXISTS=$(psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -t -c "SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'rag_embeddings');" | xargs)

if [ "$TABLE_EXISTS" = "f" ]; then
    echo -e "${GREEN}✓ 表已成功删除${NC}"
else
    echo -e "${RED}✗ 表删除失败${NC}"
    exit 1
fi

echo ""
echo -e "${YELLOW}步骤 4/4: 检查应用配置...${NC}"

# 检查配置文件中的维度设置
DASHSCOPE_DIM=$(grep -A 2 "dashscope:" src/main/resources/application.yml | grep "dimensions:" | awk '{print $2}')
PGVECTOR_DIM=$(grep -A 10 "pgvector:" src/main/resources/application.yml | grep "dimension:" | awk '{print $2}')

echo "  - DashScope 配置维度: $DASHSCOPE_DIM"
echo "  - PGVector 配置维度: $PGVECTOR_DIM"

if [ "$DASHSCOPE_DIM" = "1024" ] && [ "$PGVECTOR_DIM" = "1024" ]; then
    echo -e "${GREEN}✓ 配置正确 (1024 维)${NC}"
elif [ "$DASHSCOPE_DIM" = "1536" ] && [ "$PGVECTOR_DIM" = "1536" ]; then
    echo -e "${GREEN}✓ 配置正确 (1536 维)${NC}"
else
    echo -e "${RED}✗ 配置不一致！${NC}"
    echo -e "${RED}  DashScope: $DASHSCOPE_DIM, PGVector: $PGVECTOR_DIM${NC}"
    exit 1
fi

echo ""
echo -e "${GREEN}======================================"
echo -e "         修复完成！ ✓"
echo -e "======================================${NC}"
echo ""
echo "下一步操作："
echo "  1. 启动应用: mvn spring-boot:run"
echo "  2. 应用会自动创建正确维度的表"
echo "  3. 测试上传 PDF"
echo ""
echo "测试命令："
echo "  curl -X POST http://localhost:8080/api/rag/upload-pdf \\"
echo "    -F \"file=@pdf_reports/test.pdf\" \\"
echo "    -F \"companyName=测试公司\""
echo ""
