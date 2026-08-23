package com.educloud.course.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * M05 任务 5：CourseErrorCode 契约测试。
 *
 * <p>断言每个 Course 域错误码的 code()（= 枚举名）、HTTP 状态与默认消息非空，
 * 保证对外错误响应稳定（API 规范第 4 节）。</p>
 */
class CourseErrorCodeTest {

    @ParameterizedTest
    @MethodSource("expectedCodes")
    void exposesStableCodeAndHttpStatus(CourseErrorCode errorCode, int expectedHttpStatus) {
        assertThat(errorCode.code()).isEqualTo(errorCode.name());
        assertThat(errorCode.httpStatus()).isEqualTo(expectedHttpStatus);
        assertThat(errorCode.defaultMessage()).isNotBlank();
    }

    @Test
    void codesAreUnique() {
        assertThat(CourseErrorCode.values())
                .extracting(CourseErrorCode::code)
                .doesNotHaveDuplicates();
    }

    private static Stream<Arguments> expectedCodes() {
        return Stream.of(
                Arguments.of(CourseErrorCode.COURSE_NOT_FOUND, 404),
                Arguments.of(CourseErrorCode.COURSE_NOT_FREE, 409),
                Arguments.of(CourseErrorCode.COURSE_OFFLINE_OR_ARCHIVED, 409),
                Arguments.of(CourseErrorCode.VERSION_NOT_DRAFT, 409),
                Arguments.of(CourseErrorCode.SUBMISSION_NOT_PENDING, 409),
                Arguments.of(CourseErrorCode.REVIEW_REJECT_REASON_REQUIRED, 400),
                Arguments.of(CourseErrorCode.NOT_ENROLLED, 403),
                Arguments.of(CourseErrorCode.COURSE_ACCESS_DENIED, 403),
                Arguments.of(CourseErrorCode.REVIEW_NOT_FOUND, 404));
    }
}
