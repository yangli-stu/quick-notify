# Quick-Notify Website Dockerfile
FROM nginx:1.25-alpine

# 复制网站文件到 nginx html 目录
COPY . /usr/share/nginx/html

# 复制自定义 Nginx 配置
COPY nginx.conf /etc/nginx/nginx.conf

EXPOSE 8080

CMD ["nginx", "-g", "daemon off;"]
