#!/bin/bash
#
# Quick-Notify Website 部署脚本
# 用法: ./deploy.sh [local|prod]
#

set -e

# 配置
IMAGE_NAME="quick-notify-website"
CONTAINER_NAME="qn-website"
PORT=2025
WEBSITE_DIR="$(cd "$(dirname "$0")" && pwd)"
DOCKERFILE="$WEBSITE_DIR/Dockerfile"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 部署
deploy() {
    local env="${1:-local}"

    log_info "=== 部署 Quick-Notify Website ($env) ==="

    # 1. 停止旧容器
    log_info "停止旧容器..."
    docker stop "$CONTAINER_NAME" > /dev/null 2>&1 || true
    docker rm "$CONTAINER_NAME" > /dev/null 2>&1 || true

    # 2. 设置后端地址
    if [ "$env" = "prod" ]; then
        BACKEND_URL="https://api.quicknotify.example.com"
    else
        BACKEND_URL="http://localhost:2025"
    fi

    # 3. 本地构建 JAR
    log_info "构建后端..."
    cd "$WEBSITE_DIR/.."
    mvn clean package -DskipTests -pl quick-notify-example -am

    # 4. 构建镜像
    log_info "构建镜像 (环境: $env)..."
    cd "$WEBSITE_DIR"

    docker build \
        --tag "$IMAGE_NAME:latest" \
        -f "$DOCKERFILE" \
        "$WEBSITE_DIR/.."

    # 5. 启动容器
    log_info "启动容器..."
    docker run -d \
        --name "$CONTAINER_NAME" \
        -p "$PORT:2025" \
        --restart unless-stopped \
        "$IMAGE_NAME:latest"

    sleep 10

    if curl -sf http://localhost:$PORT/ > /dev/null 2>&1; then
        log_success "部署完成: http://localhost:$PORT"
    else
        log_error "服务启动失败"
        docker logs "$CONTAINER_NAME"
        exit 1
    fi
}

# 帮助
help() {
    echo ""
    echo "Quick-Notify Website 部署脚本"
    echo ""
    echo "用法: $0 [local|prod]"
    echo ""
    echo "参数:"
    echo "  local  - 本地部署，单体应用，端口: 2025"
    echo "  prod   - 生产部署，后端地址: https://api.quicknotify.example.com"
    echo ""
    echo "示例:"
    echo "  $0 local   # 本地部署"
    echo "  $0 prod    # 生产部署"
    echo ""
}

# 主入口
case "${1:-}" in
    local)
        deploy local
        ;;
    prod)
        deploy prod
        ;;
    help|--help|-h)
        help
        ;;
    *)
        log_error "未知参数: $1"
        echo ""
        help
        exit 1
        ;;
esac
