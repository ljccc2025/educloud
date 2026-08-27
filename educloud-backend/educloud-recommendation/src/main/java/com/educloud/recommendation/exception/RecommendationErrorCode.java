package com.educloud.recommendation.exception;

import com.educloud.common.error.ErrorCode;

/**
 * 推荐服务域错误码（M13 任务 7）。
 *
 * <p>通用错误（校验/未认证等）复用 {@link com.educloud.common.error.CommonErrorCode}；
 * 本枚举只承载推荐域专属语义。code() 返回枚举名，与 API 规范错误码命名一致。
 * BusinessException 构造签名以 common 源码为准（ErrorCode + message）。</p>
 */
public enum RecommendationErrorCode implements ErrorCode {

    /** context 参数不是 home/course。 */
    RECOMMENDATION_CONTEXT_INVALID(400, "Invalid recommendation context"),

    /** context=course 但未传 courseId。 */
    RECOMMENDATION_COURSE_ID_REQUIRED(400, "courseId is required in course context"),

    /** feedback action 不是 DISLIKE。 */
    RECOMMENDATION_ACTION_UNSUPPORTED(400, "Currently only DISLIKE is supported");

    private final int httpStatus;
    private final String defaultMessage;

    RecommendationErrorCode(int httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public int httpStatus() {
        return httpStatus;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }
}
