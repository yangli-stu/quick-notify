#!/bin/bash
#
# Quick-Notify Website 本地 CI/CD 部署脚本
# 用法: ./deploy.sh [命令]
# 命令: start | stop | restart | logs | status | clean | python
#

set -e

# 配置
IMAGE_NAME="quick-notify-website"
CONTAINER_NAME="qn-website"
PORT=8501
WEBSITE_DIR="$(cd "$(dirname "$0")" && pwd)"
DOCKERFILE="$WEBSITE_DIR/Dockerfile"
PYTHON_PID_FILE="/tmp/quick-notify-website-python.pid"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 检查 Docker 是否可用
check_docker() {
    if docker info > /dev/null 2>&1; then
        return 0
    else
        return 1
    fi
}

# 检查端口是否被占用
check_port() {
    if lsof -i :$PORT > /dev/null 2>&1; then
        return 0
    else
        return 1
    fi
}

# 获取 Python 服务 PID
get_python_pid() {
    if [ -f "$PYTHON_PID_FILE" ]; then
        cat "$PYTHON_PID_FILE"
    else
        echo ""
    fi
}

# ============ Docker 部署 ============

build_image() {
    log_info "开始构建 Docker 镜像: $IMAGE_NAME"
    cd "$WEBSITE_DIR"

    docker build \
        --tag "$IMAGE_NAME:latest" \
        -f "$DOCKERFILE" \
        "$WEBSITE_DIR"

    log_success "镜像构建完成: $IMAGE_NAME"
}

start_docker() {
    check_docker || { log_error "Docker 不可用"; exit 1; }

    # 停止旧容器
    if docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
        log_info "停止旧容器..."
        docker stop "$CONTAINER_NAME" > /dev/null 2>&1 || true
        docker rm "$CONTAINER_NAME" > /dev/null 2>&1 || true
    fi

    log_info "启动容器..."
    docker run -d \
        --name "$CONTAINER_NAME" \
        -p "$PORT:8080" \
        --restart unless-stopped \
        "$IMAGE_NAME:latest"

    sleep 2

    if curl -sf http://localhost:$PORT/ > /dev/null 2>&1; then
        log_success "Docker 部署完成: http://localhost:$PORT"
    else
        log_error "服务启动失败"
        docker logs "$CONTAINER_NAME"
        exit 1
    fi
}

stop_docker() {
    check_docker || { log_error "Docker 不可用"; exit 1; }

    if docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
        log_info "停止容器..."
        docker stop "$CONTAINER_NAME" > /dev/null 2>&1 || true
        docker rm "$CONTAINER_NAME" > /dev/null 2>&1 || true
        log_success "容器已停止"
    else
        log_warn "容器未运行"
    fi
}

# ============ Python 部署 ============

start_python() {
    # 检查端口
    if check_port; then
        # 检查是否是 Python 服务
        local pid=$(get_python_pid)
        if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
            log_warn "Python 服务已在运行 (PID: $pid)"
            return
        else
            log_error "端口 $PORT 已被占用"
            lsof -i :$PORT
            exit 1
        fi
    fi

    log_info "启动 Python HTTP 服务..."
    cd "$WEBSITE_DIR"

    nohup python3 -m http.server $PORT > /tmp/quick-notify-website-python.log 2>&1 &
    local pid=$!
    echo $pid > "$PYTHON_PID_FILE"

    sleep 2

    if curl -sf http://localhost:$PORT/ > /dev/null 2>&1; then
        log_success "Python 部署完成: http://localhost:$PORT (PID: $pid)"
    else
        log_error "服务启动失败"
        cat /tmp/quick-notify-website-python.log
        rm -f "$PYTHON_PID_FILE"
        exit 1
    fi
}

stop_python() {
    local pid=$(get_python_pid)

    if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
        log_info "停止 Python 服务 (PID: $pid)..."
        kill "$pid" 2>/dev/null || true
        sleep 1
        rm -f "$PYTHON_PID_FILE"
        log_success "Python 服务已停止"
    else
        # 尝试通过端口查找
        local port_pid=$(lsof -ti :$PORT 2>/dev/null || true)
        if [ -n "$port_pid" ]; then
            log_info "停止进程 (PID: $port_pid)..."
            kill "$port_pid" 2>/dev/null || true
            rm -f "$PYTHON_PID_FILE"
            log_success "Python 服务已停止"
        else
            log_warn "Python 服务未运行"
        fi
    fi
}

# ============ 统一操作 ============

deploy() {
    log_info "=== 完整部署 Quick-Notify Website ==="

    # 1. 停止所有服务
    log_info "步骤 1/3: 停止现有服务..."
    if check_docker && docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
        docker stop "$CONTAINER_NAME" > /dev/null 2>&1 || true
        docker rm "$CONTAINER_NAME" > /dev/null 2>&1 || true
        log_info "旧容器已清理"
    fi

    # 停止 Python 服务
    local py_pid=$(get_python_pid)
    if [ -n "$py_pid" ] && kill -0 "$py_pid" 2>/dev/null; then
        kill "$py_pid" 2>/dev/null || true
        log_info "Python 服务已停止"
    fi

    # 清理占用端口的进程
    local port_pid=$(lsof -ti :$PORT 2>/dev/null || true)
    if [ -n "$port_pid" ]; then
        kill "$port_pid" 2>/dev/null || true
        sleep 1
        log_info "端口 $PORT 已释放"
    fi

    # 2. 删除旧镜像
    log_info "步骤 2/3: 删除旧镜像..."
    if check_docker; then
        docker rmi "$IMAGE_NAME:latest" > /dev/null 2>&1 || true
    fi

    # 3. 启动新服务
    log_info "步骤 3/3: 启动新服务..."
    start
}

start() {
    log_info "=== 启动 Quick-Notify Website ==="

    # 优先使用 Docker
    if check_docker; then
        log_info "使用 Docker 部署..."
        if build_image; then
            start_docker
        else
            log_warn "Docker 构建失败，尝试 Python 部署..."
            start_python
        fi
    else
        log_warn "Docker 不可用，使用 Python 部署..."
        start_python
    fi
}

stop() {
    log_info "=== 停止 Quick-Notify Website ==="

    # 尝试停止 Docker
    if check_docker && docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
        stop_docker
    fi

    # 尝试停止 Python
    stop_python
}

restart() {
    stop
    sleep 1
    start
}

status() {
    echo ""
    echo "=== Quick-Notify Website 状态 ==="
    echo ""

    local running=false

    # 检查 Docker
    if check_docker && docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
        echo -e "Docker: ${GREEN}运行中${NC}"
        docker ps --filter "name=$CONTAINER_NAME" --format "  {{.Status}}"
        running=true
    else
        echo -e "Docker: ${RED}未运行${NC}"
    fi

    # 检查 Python
    local py_pid=$(get_python_pid)
    if [ -n "$py_pid" ] && kill -0 "$py_pid" 2>/dev/null; then
        echo -e "Python: ${GREEN}运行中${NC} (PID: $py_pid)"
        running=true
    elif check_port; then
        local port_pid=$(lsof -ti :$PORT 2>/dev/null || true)
        if [ -n "$port_pid" ]; then
            echo -e "Python: ${GREEN}运行中${NC} (PID: $port_pid)"
            running=true
        fi
    else
        echo -e "Python: ${RED}未运行${NC}"
    fi

    echo ""

    # 健康检查
    if curl -sf http://localhost:$PORT/ > /dev/null 2>&1; then
        echo -e "${GREEN}✓ 服务正常${NC} - http://localhost:$PORT"
    else
        echo -e "${RED}✗ 服务不可用${NC}"
    fi

    echo ""
}

logs() {
    # 优先显示 Docker 日志
    if check_docker && docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
        docker logs -f --tail 100 "$CONTAINER_NAME"
    else
        # 显示 Python 日志
        if [ -f "/tmp/quick-notify-website-python.log" ]; then
            tail -f /tmp/quick-notify-website-python.log
        else
            log_error "无可用日志"
            exit 1
        fi
    fi
}

clean() {
    log_warn "=== 清理 Quick-Notify Website ==="

    # 停止所有服务
    stop

    # 删除 Docker 镜像
    if check_docker; then
        log_info "删除 Docker 镜像..."
        docker rmi "$IMAGE_NAME:latest" > /dev/null 2>&1 || true
    fi

    # 清理日志
    rm -f /tmp/quick-notify-website-python.log
    rm -f "$PYTHON_PID_FILE"

    log_success "清理完成"
}

help() {
    echo ""
    echo "Quick-Notify Website 本地 CI/CD 部署脚本"
    echo ""
    echo "用法: $0 [命令]"
    echo ""
    echo "命令:"
    echo "  start   - 启动服务（自动选择 Docker 或 Python）"
    echo "  stop    - 停止服务"
    echo "  restart - 重启服务"
    echo "  status  - 查看状态"
    echo "  logs    - 查看日志"
    echo "  deploy  - 完整部署（清理旧容器+重新构建）"
    echo "  clean   - 清理所有资源"
    echo "  python  - 强制使用 Python 部署"
    echo "  help    - 显示帮助"
    echo ""
    echo "示例:"
    echo "  $0 start    - 启动服务"
    echo "  $0 status   - 查看状态"
    echo "  $0 logs     - 查看日志"
    echo "  $0 python   - 使用 Python 部署"
    echo ""
}

# 主入口
case "${1:-}" in
    start)
        start
        ;;
    stop)
        stop
        ;;
    restart)
        restart
        ;;
    status)
        status
        ;;
    logs)
        logs
        ;;
    clean)
        clean
        ;;
    deploy)
        deploy
        ;;
    python)
        start_python
        ;;
    help|--help|-h)
        help
        ;;
    *)
        log_error "未知命令: $1"
        echo ""
        help
        exit 1
        ;;
esac
