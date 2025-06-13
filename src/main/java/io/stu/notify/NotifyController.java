package io.stu.notify;

import io.stu.common.WebResponse;
import io.stu.notify.model.NotifyMessageLog;
import io.stu.notify.stomp.NotifyMessage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@Tag(name = "消息通知相关接口")
@Slf4j
@Validated
@RestController
public class NotifyController {

    @Autowired
    private NotifyManager notifyManager;

    @Operation(summary = "获取历史消息（按创建时间排序）")
    @GetMapping("/api/notify/history")
    public WebResponse<Page<NotifyMessage>> historyMsg(
        @RequestParam(name = "created", required = false) Long created,
        @RequestParam(name = "page_num", defaultValue = "0") @Min(0) @Max(10) int pageNumber,
        @RequestParam(name = "page_size", defaultValue = "30") @Min(1) @Max(30) int pageSize) {
        if (created == null) {
            created = Instant.now().toEpochMilli();
        }
        val result = notifyManager.getHistoryNotifyByCreated(uid(), created, PageRequest.of(pageNumber, pageSize));
        return WebResponse.success(result.map(NotifyMessageLog::convert));
    }

    @Operation(summary = "设置消息为已读")
    @PostMapping("/api/notify/viewed")
    public WebResponse<Void> viewedMsg(@RequestBody List<String> msgIds) {
        notifyManager.markMessagesAsRead(uid(), msgIds);
        return WebResponse.success();
    }

    @Operation(summary = "删除消息")
    @DeleteMapping("/api/notify/delete")
    public WebResponse<Void> deleteMsg(@RequestBody List<String> msgIds) {
        notifyManager.deleteAllById(uid(), msgIds);
        return WebResponse.success();
    }

    // 从认证上下中获取用户id
    private static String uid() {
        return "test1";
    }
}