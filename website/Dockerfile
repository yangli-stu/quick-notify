# Quick-Notify Website Dockerfile
FROM nginx:alpine

# 复制网站文件
COPY . /usr/share/nginx/html

# 复制自定义 Nginx 配置
COPY nginx.conf /etc/nginx/nginx.conf

EXPOSE ${PORT:-8501}

CMD ["nginx", "-g", "daemon off;"]
