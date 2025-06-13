package io.stu.common;

import io.stu.common.exception.SysException;
import io.stu.common.util.I18n;
import lombok.Data;

import java.io.Serializable;

@Data
public class WebResponse<T> implements Serializable {

    public static final String DEFAULT_SUCCESS_CODE = "0";

    private String code;

    private T data;

    private String message;

    private WebResponse(String code, T data, String message) {
        this.code = code;
        this.data = data;
        this.message = message;
    }

    public static <T> WebResponse<T> error(String code) {
        if (code.equals(DEFAULT_SUCCESS_CODE)) {
            throw new IllegalArgumentException();
        }
        return new WebResponse<>(code, null, I18n.getMessage(code));
    }

    public static <T> WebResponse<T> error(SysException exception) {
        if (exception.getCode().equals(DEFAULT_SUCCESS_CODE)) {
            throw new IllegalArgumentException();
        }
        return new WebResponse<>(exception.getCode(), null, exception.getMessage());
    }

    public static <T> WebResponse<T> success() {
        return success(null);
    }

    public static <T> WebResponse<T> success(T obj) {
        return new WebResponse<>(DEFAULT_SUCCESS_CODE, obj, "Succeed");
    }
}
