package com.educloud.live.spi.plugins;

import com.educloud.live.config.LiveProperties;
import com.educloud.live.entity.LiveRoomEntity;
import com.educloud.live.enums.LiveProviderType;
import com.educloud.live.exception.LiveErrorCode;
import com.educloud.live.exception.LiveException;
import com.educloud.live.spi.LiveStreamProvider;
import com.educloud.live.spi.model.LiveStreamPlayUrls;
import com.educloud.live.spi.model.LiveStreamPushUrl;
import com.educloud.live.spi.model.StreamStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
public class MockLiveStreamProvider implements LiveStreamProvider {

    private final LiveProperties liveProperties;

    private static boolean isProduction(String env) {
        return "prod".equalsIgnoreCase(env) || "production".equalsIgnoreCase(env);
    }

    private void checkProductionGating() {
        if (isProduction(liveProperties.getEnvironment())
                && !"true".equalsIgnoreCase(System.getProperty("educloud.mock.stream.enabled"))) {
            throw new LiveException(LiveErrorCode.MOCK_STREAM_DISABLED);
        }
    }

    @Override
    public LiveProviderType getProviderType() {
        return LiveProviderType.MOCK;
    }

    @Override
    public LiveStreamPushUrl generatePushUrl(LiveRoomEntity room) {
        checkProductionGating();
        String streamKey = room.getStreamKey();
        LiveProperties.MockStreamProperties mock = liveProperties.getStream().getMock();
        long expireSeconds = mock.getTokenExpireSeconds();
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(expireSeconds);
        long expireEpoch = expiresAt.toEpochSecond(ZoneOffset.ofHours(8));
        String token = calculateSignature(streamKey, mock.getSecretKey(), expireEpoch);

        String pushUrl = String.format("%s/%s?sign=%s&expires=%d",
                mock.getPushBaseUrl(), streamKey, token, expireEpoch);

        return LiveStreamPushUrl.builder()
                .pushUrl(pushUrl)
                .streamKey(streamKey)
                .token(token)
                .expiresAt(expiresAt)
                .build();
    }

    @Override
    public LiveStreamPlayUrls generatePlayUrls(LiveRoomEntity room) {
        checkProductionGating();
        String streamKey = room.getStreamKey();
        LiveProperties.MockStreamProperties mock = liveProperties.getStream().getMock();
        long expireSeconds = mock.getTokenExpireSeconds();
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(expireSeconds);
        long expireEpoch = expiresAt.toEpochSecond(ZoneOffset.ofHours(8));
        String token = calculateSignature(streamKey, mock.getSecretKey(), expireEpoch);

        String flvUrl = String.format("%s/%s.flv?sign=%s&expires=%d",
                mock.getPlayFlvBaseUrl(), streamKey, token, expireEpoch);
        String hlsUrl = String.format("%s/%s.m3u8?sign=%s&expires=%d",
                mock.getPlayHlsBaseUrl(), streamKey, token, expireEpoch);
        String webrtcUrl = String.format("%s/%s?sign=%s&expires=%d",
                mock.getPlayWebrtcBaseUrl(), streamKey, token, expireEpoch);

        return LiveStreamPlayUrls.builder()
                .flvUrl(flvUrl)
                .hlsUrl(hlsUrl)
                .webrtcUrl(webrtcUrl)
                .expiresAt(expiresAt)
                .build();
    }

    @Override
    public StreamStatus queryStreamStatus(String streamKey) {
        checkProductionGating();
        return StreamStatus.builder()
                .active(true)
                .bitrateKbps(2500)
                .fps(30)
                .onlineViewers(10)
                .checkedAt(LocalDateTime.now())
                .build();
    }

    @Override
    public boolean banStream(String streamKey) {
        checkProductionGating();
        return true;
    }

    private static String calculateSignature(String streamKey, String secretKey, long expireEpoch) {
        try {
            String raw = streamKey + "-" + secretKey + "-" + expireEpoch;
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 not available", e);
        }
    }
}
