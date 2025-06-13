package io.stu.common.exception;

import io.stu.common.util.I18n;
import lombok.Getter;

@Getter
public class SysException extends RuntimeException {

    private static final String DEFAULT_CODE = "BE-000";

    private final String code;

    public SysException(Throwable cause) {
        super(I18n.getMessage(DEFAULT_CODE), cause);
        this.code = DEFAULT_CODE;
    }

    public SysException(String code, Throwable cause) {
        super(I18n.getMessage(code), cause);
        this.code = code;
    }

    public SysException(String code) {
        super(I18n.getMessage(code));
        this.code = code;
    }

    public SysException(String code, Object... args) {
        super(I18n.getMessage(code, args));
        this.code = code;
    }

    public SysException(String code, String message) {
        super(message);
        this.code = code;
    }

    public static SysException notFound(Object arg) {
        return new SysException("BE-999", "Not found %s".formatted(arg));
    }
}
