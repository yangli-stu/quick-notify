package io.stu.common.util;

import io.stu.common.exception.SysException;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Supplier;

@Slf4j
public class RetryUtil {

    public static <T> T executeWithRetry(String taskName, Supplier<T> task, int retryInterval) {
        Exception lastEx = null;
        for (int i = 1; i <= 3; i++) {
            try {
                if (retryInterval > 0 && i > 1) {
                    Thread.sleep(retryInterval);
                }
                return task.get();
            } catch (Exception e) {
                lastEx = e;
                log.error("执行task {} 失败，当前重试：{}, msg {}", taskName, i, e.getMessage());
            }
        }

        log.error("执行task {} 异常，最终重试失败，msg {}", taskName, lastEx.getMessage(), lastEx);
        throw new SysException("BE-001");
    }

}
