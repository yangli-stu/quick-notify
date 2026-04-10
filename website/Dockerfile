# Quick-Notify Website Dockerfile
FROM nginx:alpine

# 复制网站文件
COPY . /usr/share/nginx/html

# 复制自定义 Nginx 配置
COPY nginx.conf /etc/nginx/nginx.conf

EXPOSE 8501

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD wget -qO- http://localhost:8501/health || exit 1

CMD ["nginx", "-g", "daemon off;"]
