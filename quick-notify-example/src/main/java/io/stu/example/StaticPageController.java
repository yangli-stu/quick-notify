package io.stu.example;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 静态页面控制器 - 处理 /demo, /about 等路径
 * 从文件系统读取静态文件 (Dockerfile COPY 到 /app/BOOT-INF/classes/static/)
 */
@Controller
public class StaticPageController {

    private static final String STATIC_BASE = "/app/BOOT-INF/classes/static";

    @GetMapping(value = {"/demo", "/about", "/blog", "/docs", "/architecture"})
    @ResponseBody
    public String handleStaticPage(HttpServletRequest request) throws IOException {
        String path = request.getRequestURI();
        String fileName = path.substring(1) + ".html";
        Path filePath = Paths.get(STATIC_BASE, fileName);
        if (Files.exists(filePath)) {
            return Files.readString(filePath);
        }
        return "Not Found";
    }

    @GetMapping(value = {"/", "/index"})
    @ResponseBody
    public String handleIndex() throws IOException {
        Path filePath = Paths.get(STATIC_BASE, "index.html");
        if (Files.exists(filePath)) {
            return Files.readString(filePath);
        }
        return "Not Found";
    }

    @GetMapping("/**/*.css")
    @ResponseBody
    public String handleCss(HttpServletRequest request) throws IOException {
        String path = request.getRequestURI();
        Path filePath = Paths.get(STATIC_BASE, path.substring(1));
        if (Files.exists(filePath)) {
            return Files.readString(filePath);
        }
        return "Not Found";
    }

    @GetMapping("/**/*.js")
    @ResponseBody
    public String handleJs(HttpServletRequest request) throws IOException {
        String path = request.getRequestURI();
        Path filePath = Paths.get(STATIC_BASE, path.substring(1));
        if (Files.exists(filePath)) {
            return Files.readString(filePath);
        }
        return "Not Found";
    }
}
