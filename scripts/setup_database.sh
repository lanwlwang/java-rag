#!/bin/bash

# ============================================
# RAG 数据库快速初始化脚本
# ============================================

set -e  # 遇到错误立即退出

# 配置参数
DB_NAME="rag_db"
DB_USER="postgres"
DB_HOST="localhost"
DB_PORT="5432"

echo "=========================================="
echo "RAG 向量数据库初始化脚本"
echo "=========================================="
echo ""

# 检查 PostgreSQL 是否运行
echo "📌 [1/5] 检查 PostgreSQL 服务..."
if ! pg_isready -h $DB_HOST -p $DB_PORT > /dev/null 2>&1; then
    echo "❌ PostgreSQL 未运行!"
    echo "请先启动 PostgreSQL:"
    echo "  macOS: brew services start postgresql@15"
    echo "  Linux: sudo systemctl start postgresql"
    exit 1
fi
echo "✅ PostgreSQL 服务正常"
echo ""

# 检查数据库是否存在
echo "📌 [2/5] 检查数据库是否存在..."
if psql -h $DB_HOST -p $DB_PORT -U $DB_USER -lqt | cut -d \| -f 1 | grep -qw $DB_NAME; then
    echo "⚠️  数据库 '$DB_NAME' 已存在"
    read -p "是否重新初始化? (会清空数据) [y/N]: " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo "删除旧数据库..."
        dropdb -h $DB_HOST -p $DB_PORT -U $DB_USER $DB_NAME
        echo "创建新数据库..."
        createdb -h $DB_HOST -p $DB_PORT -U $DB_USER $DB_NAME
    fi
else
    echo "创建数据库 '$DB_NAME'..."
    createdb -h $DB_HOST -p $DB_PORT -U $DB_USER $DB_NAME
fi
echo "✅ 数据库准备完成"
echo ""

# 检查 pgvector 扩展
echo "📌 [3/5] 检查 pgvector 扩展..."
if ! psql -h $DB_HOST -p $DB_PORT -U $DB_USER $DB_NAME -c "CREATE EXTENSION IF NOT EXISTS vector;" > /dev/null 2>&1; then
    echo "❌ pgvector 扩展未安装!"
    echo "请先安装 pgvector:"
    echo "  macOS: brew install pgvector"
    echo "  Linux: 参考 https://github.com/pgvector/pgvector"
    exit 1
fi
echo "✅ pgvector 扩展已启用"
echo ""

# 执行 SQL 初始化脚本
echo "📌 [4/5] 执行初始化脚本..."
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
SQL_FILE="$SCRIPT_DIR/../sql/init_database.sql"

if [ ! -f "$SQL_FILE" ]; then
    echo "❌ 找不到 SQL 文件: $SQL_FILE"
    exit 1
fi

psql -h $DB_HOST -p $DB_PORT -U $DB_USER $DB_NAME -f "$SQL_FILE" > /dev/null 2>&1
echo "✅ 数据库表和索引创建完成"
echo ""

# 验证表结构
echo "📌 [5/5] 验证表结构..."
TABLE_EXISTS=$(psql -h $DB_HOST -p $DB_PORT -U $DB_USER $DB_NAME -tAc "SELECT COUNT(*) FROM information_schema.tables WHERE table_name='rag_embeddings';")

if [ "$TABLE_EXISTS" -eq "1" ]; then
    echo "✅ 表 'rag_embeddings' 创建成功"
    
    # 显示表结构
    echo ""
    echo "表结构:"
    psql -h $DB_HOST -p $DB_PORT -U $DB_USER $DB_NAME -c "\d rag_embeddings"
    
    echo ""
    echo "索引列表:"
    psql -h $DB_HOST -p $DB_PORT -U $DB_USER $DB_NAME -c "\di rag_embeddings*"
else
    echo "❌ 表创建失败"
    exit 1
fi

echo ""
echo "=========================================="
echo "✅ 数据库初始化完成!"
echo "=========================================="
echo ""
echo "数据库信息:"
echo "  名称: $DB_NAME"
echo "  主机: $DB_HOST:$DB_PORT"
echo "  用户: $DB_USER"
echo "  表名: rag_embeddings"
echo ""
echo "下一步:"
echo "  1. 修改 application.yml 配置数据库密码"
echo "  2. 运行 Java 应用: mvn spring-boot:run"
echo ""
echo "常用命令:"
echo "  连接数据库: psql $DB_NAME"
echo "  查看数据:   psql $DB_NAME -c 'SELECT COUNT(*) FROM rag_embeddings;'"
echo ""
