package com.educloud.file.exception;

import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.error.BusinessException;
import com.educloud.common.error.CommonErrorCode;
import com.educloud.common.error.ErrorCode;
import com.educloud.common.error.FieldViolation;
import com.educloud.common.error.ValidationErrorDetails;
import com.educloud.common.web.RequestContext;
import com.educloud.file.storage.FileStorageException;
import com.educloud.file.storage.FileTooLargeException;
import com.educloud.file.storage.FileTypeNotAllowedException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * File 服务统一异常出口（任务 7）。
 *
 * <p>把模块内部异常映射为 {@link FileErrorCode} 的 ApiResponse 信封响应
 * （code/message/data/requestId/timestamp + X-Request-Id 响应头），message 一律用
 * 错误码的英文 defaultMessage，不向客户端泄漏内部路径、堆栈或底层驱动细节。
 * 与 common 的 {@code GlobalExceptionHandler} 并存：域内异常在此收敛，
 * 校验/兜底语义与 common 保持一致（重复注册时响应结构完全相同）。</p>
 */
@RestControllerAdvice
public final class FileExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileExceptionHandler.class);

    private final ApiResponseFactory responses;

    public FileExceptionHandler(ApiResponseFactory responses) {
        this.responses = Objects.requireNonNull(responses, "responses");
    }

    /**
     * File 域内部异常统一映射。storage 包子类（FileTypeNotAllowedException、
     * FileTooLargeException）继承 FileStorageException，instanceof 分支必须
     * 子类在前，避免被父类分支抢先归为 FILE_STORAGE_UNAVAILABLE。
     */
    @ExceptionHandler({
            UploadSessionExpiredException.class,
            UploadSessionNotFoundException.class,
            UploadNotVerifiedException.class,
            UploadSessionStateException.class,
            UploadSessionAccessDeniedException.class,
            FileTypeNotAllowedException.class,
            FileTooLargeException.class,
            FileStorageException.class,
            FileNotFoundException.class,
            FileNotAvailableException.class,
            FileBoundException.class,
            FileAccessDeniedException.class,
            GrantPurposeNotAllowedException.class,
            VersionConflictException.class })
    public ResponseEntity<ApiResponse<Void>> handleFileDomainException(RuntimeException exception) {
        FileErrorCode errorCode = toFileErrorCode(exception);
        return respond(errorCode, errorCode.defaultMessage(), null);
    }

    /**
     * BusinessException 直通（如服务层直接抛出 FileErrorCode，含 STORAGE_TEST_RATE_LIMITED），
     * 与 common GlobalExceptionHandler 的语义一致，保证 standalone 场景下同样收敛。
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusinessException(BusinessException exception) {
        return respond(exception.errorCode(), exception.getMessage(), exception.details());
    }

    /** 方法级安全/权限校验（@PreAuthorize 等）抛出的 Spring Security 拒绝。 */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException exception) {
        return respond(
                FileErrorCode.FILE_ACCESS_DENIED,
                FileErrorCode.FILE_ACCESS_DENIED.defaultMessage(),
                null);
    }

    /** @Valid 请求体验证失败 → 400 VALIDATION_FAILED（复用 common 的 FieldViolation 结构）。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<ValidationErrorDetails>> handleBodyValidation(
            MethodArgumentNotValidException exception) {
        List<FieldViolation> violations = exception.getBindingResult().getFieldErrors().stream()
                .map(FileExceptionHandler::toViolation)
                .sorted(Comparator.comparing(FieldViolation::field).thenComparing(FieldViolation::code))
                .toList();
        return respond(
                CommonErrorCode.VALIDATION_FAILED,
                CommonErrorCode.VALIDATION_FAILED.defaultMessage(),
                new ValidationErrorDetails(violations));
    }

    /** 兜底：500 不泄漏细节，完整堆栈只进服务端日志。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) {
        ApiResponse<Void> body = responses.error(
                CommonErrorCode.INTERNAL_ERROR,
                CommonErrorCode.INTERNAL_ERROR.defaultMessage(),
                null);
        LOGGER.error("Unhandled request failure requestId={}", body.requestId(), exception);
        return ResponseEntity.status(CommonErrorCode.INTERNAL_ERROR.httpStatus())
                .header(RequestContext.REQUEST_ID_HEADER, body.requestId())
                .body(body);
    }

    private static FileErrorCode toFileErrorCode(RuntimeException exception) {
        if (exception instanceof UploadSessionExpiredException) {
            return FileErrorCode.UPLOAD_SESSION_EXPIRED;
        }
        if (exception instanceof UploadSessionNotFoundException) {
            return FileErrorCode.UPLOAD_SESSION_NOT_FOUND;
        }
        if (exception instanceof UploadNotVerifiedException) {
            return FileErrorCode.UPLOAD_NOT_VERIFIED;
        }
        // 会话状态不允许目标操作：无专属状态错误码，按任务提示复用 409 UPLOAD_NOT_VERIFIED 冲突语义。
        if (exception instanceof UploadSessionStateException) {
            return FileErrorCode.UPLOAD_NOT_VERIFIED;
        }
        // 会话归属校验失败属越权，语义上归 403（与 UploadSessionAccessDeniedException javadoc 一致）。
        if (exception instanceof UploadSessionAccessDeniedException) {
            return FileErrorCode.FILE_ACCESS_DENIED;
        }
        // 子类优先于父类 FileStorageException（见类注释）。
        if (exception instanceof FileTypeNotAllowedException) {
            return FileErrorCode.FILE_TYPE_NOT_ALLOWED;
        }
        if (exception instanceof FileTooLargeException) {
            return FileErrorCode.FILE_TOO_LARGE;
        }
        if (exception instanceof FileStorageException) {
            return FileErrorCode.FILE_STORAGE_UNAVAILABLE;
        }
        if (exception instanceof FileNotFoundException) {
            return FileErrorCode.FILE_NOT_FOUND;
        }
        // 对象存在但状态不可用（UPLOADING/QUARANTINED/DELETED）：对外按“不存在/不可见”处理，
        // 不暴露内部状态机细节。
        if (exception instanceof FileNotAvailableException) {
            return FileErrorCode.FILE_NOT_FOUND;
        }
        if (exception instanceof FileBoundException) {
            return FileErrorCode.FILE_BOUND;
        }
        if (exception instanceof FileAccessDeniedException) {
            return FileErrorCode.FILE_ACCESS_DENIED;
        }
        if (exception instanceof GrantPurposeNotAllowedException) {
            return FileErrorCode.GRANT_PURPOSE_NOT_ALLOWED;
        }
        if (exception instanceof VersionConflictException) {
            return FileErrorCode.VERSION_CONFLICT;
        }
        throw new IllegalArgumentException(
                "Unmapped file exception: " + exception.getClass().getName());
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
