package com.educloud.live.spi;

import com.educloud.live.config.LiveProperties;
import com.educloud.live.entity.LiveRoomEntity;
import com.educloud.live.enums.LiveProviderType;
import com.educloud.live.enums.LiveRoomStatus;
import com.educloud.live.exception.LiveErrorCode;
import com.educloud.live.exception.LiveException;
import com.educloud.live.spi.model.LiveStreamPlayUrls;
import com.educloud.live.spi.model.LiveStreamPushUrl;
import com.educloud.live.spi.plugins.MockLiveStreamProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LiveStreamProviderTest {

    private LiveProperties properties;
    private MockLiveStreamProvider mockProvider;
    private LiveStreamProviderFactory factory;

    @BeforeEach
    void setUp() {
        properties = new LiveProperties();
        properties.setEnvironment("dev");
        mockProvider = new MockLiveStreamProvider(properties);
        factory = new LiveStreamProviderFactory(List.of(mockProvider));
    }

    @Test
    void testGeneratePushUrlAndPlayUrls() {
        LiveRoomEntity room = LiveRoomEntity.builder()
                .id(101L)
                .courseId(201L)
                .teacherId(301L)
                .title("高并发架构")
                .streamKey("stream_key_test_101")
                .status(LiveRoomStatus.LIVING)
                .providerType(LiveProviderType.MOCK)
                .build();

        LiveStreamPushUrl pushUrl = mockProvider.generatePushUrl(room);
        assertNotNull(pushUrl);
        assertEquals("stream_key_test_101", pushUrl.getStreamKey());
        assertTrue(pushUrl.getPushUrl().startsWith("rtmp://live-mock.educloud.cn/live/stream_key_test_101"));
        assertTrue(pushUrl.getPushUrl().contains("sign="));
        assertTrue(pushUrl.getPushUrl().contains("expires="));

        LiveStreamPlayUrls playUrls = mockProvider.generatePlayUrls(room);
        assertNotNull(playUrls);
        assertTrue(playUrls.getFlvUrl().contains(".flv"));
        assertTrue(playUrls.getHlsUrl().contains(".m3u8"));
        assertTrue(playUrls.getWebrtcUrl().startsWith("webrtc://"));
    }

    @Test
    void testProductionGating() {
        properties.setEnvironment("prod");
        System.clearProperty("educloud.mock.stream.enabled");

        LiveRoomEntity room = LiveRoomEntity.builder()
                .streamKey("stream_key_test_prod")
                .build();

        LiveException exception = assertThrows(LiveException.class, () -> mockProvider.generatePushUrl(room));
        assertEquals(LiveErrorCode.MOCK_STREAM_DISABLED, exception.getErrorCode());
    }

    @Test
    void testFactoryRouting() {
        LiveStreamProvider provider = factory.getProvider(LiveProviderType.MOCK);
        assertNotNull(provider);
        assertEquals(LiveProviderType.MOCK, provider.getProviderType());

        assertThrows(LiveException.class, () -> factory.getProvider(LiveProviderType.ALIYUN));
    }
}
