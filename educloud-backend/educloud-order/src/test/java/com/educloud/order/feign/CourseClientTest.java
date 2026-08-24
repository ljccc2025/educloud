package com.educloud.order.feign;

import com.educloud.common.api.ApiResponse;
import com.educloud.order.feign.dto.CourseSalesSnapshotDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CourseClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deserializesCourseSalesSnapshotDto() throws Exception {
        String json = """
                {
                    "id": 9001,
                    "title": "Spring Cloud 微服务实战",
                    "coverFileId": 8001,
                    "price": 199.00,
                    "lifecycleStatus": "PUBLISHED",
                    "isOnSale": true,
                    "enrolled": false
                }
                """;

        CourseSalesSnapshotDto dto = objectMapper.readValue(json, CourseSalesSnapshotDto.class);

        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo(9001L);
        assertThat(dto.getTitle()).isEqualTo("Spring Cloud 微服务实战");
        assertThat(dto.getCoverFileId()).isEqualTo(8001L);
        assertThat(dto.getPrice()).isEqualByComparingTo("199.00");
        assertThat(dto.getStatus()).isEqualTo("PUBLISHED");
        assertThat(dto.getIsOnSale()).isTrue();
        assertThat(dto.getEnrolled()).isFalse();
        assertThat(dto.isPurchasable()).isTrue();
    }

    @Test
    void serializesSnowflakeIdAsString() throws Exception {
        CourseSalesSnapshotDto dto = CourseSalesSnapshotDto.builder()
                .id(9001L)
                .title("微服务实战")
                .coverFileId(8001L)
                .price(new BigDecimal("99.00"))
                .status("PUBLISHED")
                .isOnSale(true)
                .build();

        String json = objectMapper.writeValueAsString(dto);
        assertThat(json).contains("\"id\":\"9001\"");
        assertThat(json).contains("\"coverFileId\":\"8001\"");
    }

    @Test
    void isPurchasableReturnsFalseWhenNotPublishedOrNotOnSale() {
        CourseSalesSnapshotDto offlineCourse = CourseSalesSnapshotDto.builder()
                .id(9001L)
                .status("OFFLINE")
                .isOnSale(true)
                .build();
        assertThat(offlineCourse.isPurchasable()).isFalse();

        CourseSalesSnapshotDto notOnSaleCourse = CourseSalesSnapshotDto.builder()
                .id(9001L)
                .status("PUBLISHED")
                .isOnSale(false)
                .build();
        assertThat(notOnSaleCourse.isPurchasable()).isFalse();
    }

    @Test
    void mocksFeignCourseClientCall() {
        CourseClient client = mock(CourseClient.class);
        CourseSalesSnapshotDto snapshot = CourseSalesSnapshotDto.builder()
                .id(9001L)
                .title("微服务架构")
                .price(new BigDecimal("199.00"))
                .status("PUBLISHED")
                .isOnSale(true)
                .build();

        when(client.getCourseDetail(9001L)).thenReturn(
                new ApiResponse<>("SUCCESS", "OK", snapshot, "req-1", Instant.now()));

        ApiResponse<CourseSalesSnapshotDto> response = client.getCourseDetail(9001L);
        assertThat(response).isNotNull();
        assertThat(response.data()).isNotNull();
        assertThat(response.data().getId()).isEqualTo(9001L);
        assertThat(response.data().isPurchasable()).isTrue();
    }
}
