package com.educloud.course.service;

import com.educloud.common.error.BusinessException;
import com.educloud.common.error.CommonErrorCode;
import com.educloud.course.config.CourseFileProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * ServiceTokenService 单元测试（M05 任务 12）：以 HTTP Basic client_credentials 调 User
 * /internal/v1/service-tokens（M03 规格 §8），解析 access_token/expires_in；非 2xx →
 * DEPENDENCY_UNAVAILABLE。
 */
class ServiceTokenServiceTest {

    private static final String TOKEN_ENDPOINT = "http://127.0.0.1:8082";

    private MockRestServiceServer server;
    private ServiceTokenService service;

    @BeforeEach
    void setUp() {
        CourseFileProperties properties = new CourseFileProperties(
                "http://127.0.0.1:8087", "educloud-course", "s3cret", true,
                Duration.ofSeconds(3), TOKEN_ENDPOINT);
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new ServiceTokenService(properties, builder.baseUrl(TOKEN_ENDPOINT).build());
    }

    @Test
    void issuesTokenWithBasicAuthAndFormParams() {
        server.expect(requestTo(TOKEN_ENDPOINT + "/internal/v1/service-tokens"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Basic " + java.util.Base64.getEncoder()
                        .encodeToString("educloud-course:s3cret".getBytes())))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("grant_type=client_credentials"),
                                org.hamcrest.Matchers.containsString("audience=educloud-file"),
                                // FormUrlEncoded 编码 scope 中的冒号。
                                org.hamcrest.Matchers.containsString("scope=file%3Ainternal"))))
                .andRespond(withSuccess(
                        "{\"access_token\":\"tok-1\",\"token_type\":\"Bearer\",\"expires_in\":300}",
                        MediaType.APPLICATION_JSON));

        ServiceTokenService.IssueResult result = service.issue(
                "educloud-course", "s3cret", "educloud-file", List.of("file:internal"), null, null);

        assertThat(result.accessToken()).isEqualTo("tok-1");
        assertThat(result.expiresIn()).isEqualTo(300);
        server.verify();
    }

    @Test
    void mapsNon2xxToDependencyUnavailable() {
        server.expect(requestTo(TOKEN_ENDPOINT + "/internal/v1/service-tokens"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> service.issue(
                "educloud-course", "s3cret", "educloud-file", List.of("file:internal"), null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(CommonErrorCode.DEPENDENCY_UNAVAILABLE);
        server.verify();
    }
}
