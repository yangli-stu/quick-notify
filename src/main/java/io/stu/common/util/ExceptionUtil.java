package io.stu.common.util;

import io.stu.common.exception.SysException;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.util.DisconnectedClientHelper;

import java.util.concurrent.TimeoutException;

@UtilityClass
public class ExceptionUtil {

    public static boolean isClientDisconnectedException(Throwable ex) {
        return DisconnectedClientHelper.isClientDisconnectedException(ex);
    }

    public static boolean isDataIntegrityViolationException(Throwable ex) {
        return ExceptionUtils.hasCause(ex, DataIntegrityViolationException.class);
    }

    public static boolean isConcurrencyFailureException(Throwable ex) {
        return ExceptionUtils.hasCause(ex, ConcurrencyFailureException.class);
    }

    public static boolean isSysException(Throwable ex) {
        return ExceptionUtils.hasCause(ex, SysException.class);
    }

    public static boolean isHttpConnectionInvalid(Throwable ex) {
        return ex.getMessage().contains("HTTP/1.1 header parser received no bytes");
    }

    public static boolean isTimeoutException(Throwable ex) {
        return ExceptionUtils.getRootCause(ex) instanceof TimeoutException || (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("request timeout"));
    }

    public static String userFriendlyTips(String code) {
        return "\nUser-Friendly Tips: [%s=%s]".formatted(code, I18n.getMessage(code));
    }
}
