package com.educloud.common.error;

import java.util.List;
import java.util.Objects;

public record ValidationErrorDetails(List<FieldViolation> violations) implements ErrorDetails {

    public ValidationErrorDetails {
        violations = List.copyOf(Objects.requireNonNull(violations, "violations"));
    }
}
