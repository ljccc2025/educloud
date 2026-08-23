package com.educloud.course.exception;

import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.error.BusinessException;
import com.educloud.common.error.CommonErrorCode;
import com.educloud.common.error.ErrorCode;
import com.educloud.common.error.FieldViolation;
import com.educloud.common.error.ValidationErrorDetails;
import com.educloud.common.web.RequestContext;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Course 服务域异常出口（M05 任务 5，质量审查加固）。
 *
 * <p>把模块异常映射为 {@link ErrorCode}（CourseErrorCode 或通用 CommonErrorCode）的
 * ApiResponse 信封响应（code/message/data/requestId/timestamp + X-Request-Id 响应头），
 * 不向客户端泄漏内部路径、堆栈或底层细节。message 规则：BusinessException 直通其
 * message（默认即错误码 defaultMessage）；其余分支一律用错误码 defaultMessage。
 * 与 common 的 {@code GlobalExceptionHandler} 并存：校验语义与 common 保持一致
 * （重复注册时响应结构完全相同，对齐 educloud-file）。</p>
 *
 * <p>排序：Spring 异常解析为「首个匹配的 advice 获胜」（跨 advice 不做特异度比较）。
 * 因此本域 advice 显式声明 {@code @Order(Ordered.LOWEST_PRECEDENCE - 1)} 先于未声明
 * 顺序的 common {@code GlobalExceptionHandler}（默认 ≈ LOWEST_PRECEDENCE）执行——
 * 若 common 靠前，其 {@code Exception} 兜底会抢先吞掉本域的 BusinessException /
 * AccessDeniedException 并误报 500（file 模块同类共存问题即因此产生，见设计文档 §15）。
 * 本 advice 只声明域专属处理器（不声明 Exception 兜底），故坏 JSON（HttpMessageNotReadable）、
 * 缺参/类型不匹配、404/405 与未知异常 500 等 common 已映射的共享错误在本 advice 无匹配时
 * 仍由 common 的 400/404/405/500 语义处理。注意：{@code LOWEST_PRECEDENCE + 1} 在 int 上
 * 溢出为 {@code Integer.MIN_VALUE}（最高优先级），不可用于表达「更靠后」。</p>
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE - 1)
public final class CourseExceptionHandler {

    private final ApiResponseFactory responses;

    public CourseExceptionHandler(ApiResponseFactory responses) {
        this.responses = Objects.requireNonNull(responses, "responses");
    }

    /** BusinessException 直通其 message（默认即 defaultMessage；服务层可覆盖），与 common GlobalExceptionHandler 语义一致。 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusinessException(BusinessException exception) {
        return respond(exception.errorCode(), exception.getMessage(), exception.details());
    }

    /** 方法级安全/权限校验（@PreAuthorize 等）抛出的 Spring Security 拒绝 → 403 COURSE_ACCESS_DENIED。 */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException exception) {
        return respond(
                CourseErrorCode.COURSE_ACCESS_DENIED,
                CourseErrorCode.COURSE_ACCESS_DENIED.defaultMessage(),
                null);
    }

    /** @Valid 请求体验证失败 → 400 VALIDATION_FAILED（复用 common 的 FieldViolation 结构）。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<ValidationErrorDetails>> handleBodyValidation(
            MethodArgumentNotValidException exception) {
        List<FieldViolation> violations = exception.getBindingResult().getFieldErrors().stream()
                .map(CourseExceptionHandler::toViolation)
                .sorted(Comparator.comparing(FieldViolation::field).thenComparing(FieldViolation::code))
                .toList();
        return respond(
                CommonErrorCode.VALIDATION_FAILED,
                CommonErrorCode.VALIDATION_FAILED.defaultMessage(),
                new ValidationErrorDetails(violations));
    }

    private <T> ResponseEntity<ApiResponse<T>> respond(ErrorCode code, String message, T details) {
        ApiResponse<T> body = responses.error(code, message, details);
        return ResponseEntity.status(code.httpStatus())
                .header(RequestContext.REQUEST_ID_HEADER, body.requestId())
                .body(body);
    }

    private static FieldViolation toViolation(FieldError error) {
        String code = error.getCode() == null ? "Invalid" : error.getCode();
        String message = error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage();
        return new FieldViolation(error.getField(), code, message);
    }
}
