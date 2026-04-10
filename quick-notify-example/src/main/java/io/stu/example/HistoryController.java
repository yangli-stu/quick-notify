package io.stu.example;

import io.stu.notify.NotifyManager;
import io.stu.notify.model.NotifyMessage;
import io.stu.notify.repository.NotifyMessageLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 历史消息接口
 */
@Slf4j
@RestController
@RequestMapping("/api/notify")
public class HistoryController {

    @Autowired
    private NotifyManager notifyManager;

    @GetMapping("/history")
    public WebResponse<List<NotifyMessage>> history(
            @RequestParam(name = "created", required = false) Long created,
            @RequestParam(name = "page_num", defaultValue = "0") int pageNum,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {

        if (created == null) {
            created = System.currentTimeMillis();
        }

        // 使用当前用户作为 receiver（实际项目中从认证上下文获取）
        String userId = getCurrentUserId();
        List<NotifyMessageLog> logs = notifyManager.getHistoryNotifyByCreated(userId, created, pageNum * pageSize, pageSize);

        List<NotifyMessage> messages = logs.stream()
                .map(NotifyMessageLog::toNotifyMessage)
                .collect(Collectors.toList());

        return WebResponse.success(messages);
    }

    private String getCurrentUserId() {
        // 实际项目中从 SecurityContext 或认证 Token 中获取
        return "test1";
    }

    public record WebResponse<T>(int code, T data) {
        public static <T> WebResponse<T> success(T data) {
            return new WebResponse<>(200, data);
        }
    }
}
