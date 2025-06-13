package io.stu.common.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;

@Slf4j
@UtilityClass
public class I18n {

    private static volatile MessageSource messageSource;

    public static String getMessage(String code) {
        return getMessage(code, (Object)null);
    }

    public static String getMessage(String code, Object... args) {
        return getMessage(code, args, LocaleContextHolder.getLocale());
    }

    public static String getMessage(String code, Object[] args, Locale locale) {
        try {
            if (messageSource == null) {
                synchronized (I18n.class) {
                    if (messageSource == null) {
                        messageSource = SpringContextUtil.getBean(MessageSource.class);
                    }
                }
            }
            return messageSource.getMessage(code, args, locale);
        } catch (Exception ex) {
            log.error("后端没有提供异常码 {} 的文案，请更新后端异常码配置, 返回 [{}] 的文案",
                code, "BE-001");
            return getMessage("BE-001");
        }
    }
}
