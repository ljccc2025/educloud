package com.educloud.live.mapper;

import com.educloud.live.entity.LiveRoomEntity;
import com.educloud.live.enums.LiveProviderType;
import com.educloud.live.enums.LiveRoomStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class LiveRoomMapperTest {

    @Test
    void testLiveRoomEntityBuild() {
        LiveRoomEntity room = LiveRoomEntity.builder()
                .id(1001L)
                .courseId(2001L)
                .teacherId(3001L)
                .title("微服务架构实战")
                .description("Spring Boot + K8s")
                .scheduledStartAt(LocalDateTime.now().plusHours(1))
                .scheduledEndAt(LocalDateTime.now().plusHours(3))
                .status(LiveRoomStatus.CREATED)
                .providerType(LiveProviderType.MOCK)
                .streamKey("stream_key_test_1001")
                .allowChat(true)
                .version(0L)
                .deleted(0)
                .build();

        assertNotNull(room);
        assertEquals(1001L, room.getId());
        assertEquals(LiveRoomStatus.CREATED, room.getStatus());
        assertEquals(LiveProviderType.MOCK, room.getProviderType());
        assertTrue(room.getAllowChat());
    }
}
