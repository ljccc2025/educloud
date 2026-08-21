package com.educloud.user.support;

import com.educloud.user.config.PasswordProperties;
import com.educloud.user.exception.UserErrorCode;
import com.educloud.common.error.BusinessException;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 密码策略校验。依据：M03 设计规格第 5 节（最小/最大长度，服务端强制）。
 */
@Component
public final class PasswordPolicy {

    private final PasswordProperties properties;

    public PasswordPolicy(PasswordProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    public void validate(String password) {
        if (password == null
                || password.length() < properties.minLength()
                || password.length() > properties.maxLength()) {
            throw new BusinessException(
                    UserErrorCode.PASSWORD_WEAK,
                    "Password must be between " + properties.minLength()
                            + " and " + properties.maxLength() + " characters");
        }
    }
}
