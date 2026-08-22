package com.educloud.file.storage;

import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import io.minio.messages.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M04 任务 3：MinioStorageGateway 单元测试（mock MinioClient，不依赖真实存储）。
 *
 * <p>依据：2026-08-22-educloud-file-plan.md 任务 3 —— presignedPutUrl 断言
 * bucket/object/method=PUT/expiry；stat 存在/不存在映射；download 超限抛
 * FileTooLargeException；sha256 与 DigestInputStream 结果一致；deleteObject 调用
 * removeObject；probe 成功/失败路径（失败返回错误类别、不抛异常）。</p>
 */
class MinioStorageGatewayTest {

    private static final String BUCKET = "educloud-files";
    private static final String OBJECT = "dir/file.png";

    private MinioClient client;
    private MinioStorageGateway gateway;

    @BeforeEach
    void setUp() {
        client = mock(MinioClient.class);
        gateway = new MinioStorageGateway(client, BUCKET);
    }

    @Test
    void presignedPutUrlBuildsPutRequestWithBucketObjectAndExpiry() throws Exception {
        when(client.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("https://minio.example/educloud-files/dir/file.png?X-Amz-Signature=abc");

        String url = gateway.presignedPutUrl(BUCKET, OBJECT, "image/png", Duration.ofMinutes(5));

        assertThat(url).startsWith("https://minio.example/");
        ArgumentCaptor<GetPresignedObjectUrlArgs> captor =
                ArgumentCaptor.forClass(GetPresignedObjectUrlArgs.class);
        verify(client).getPresignedObjectUrl(captor.capture());
        GetPresignedObjectUrlArgs args = captor.getValue();
        assertThat(args.bucket()).isEqualTo(BUCKET);
        assertThat(args.object()).isEqualTo(OBJECT);
        assertThat(args.method()).isEqualTo(Method.PUT);
        assertThat(args.expiry()).isEqualTo(300);
    }

    @Test
    void statMapsExistingObject() throws Exception {
        StatObjectResponse response = mock(StatObjectResponse.class);
        when(response.size()).thenReturn(2048L);
        when(response.contentType()).thenReturn("image/png");
        when(client.statObject(any(StatObjectArgs.class))).thenReturn(response);

        StorageGateway.ObjectStat stat = gateway.stat(BUCKET, OBJECT);

        assertThat(stat.exists()).isTrue();
        assertThat(stat.size()).isEqualTo(2048L);
        assertThat(stat.contentType()).isEqualTo("image/png");
        ArgumentCaptor<StatObjectArgs> captor = ArgumentCaptor.forClass(StatObjectArgs.class);
        verify(client).statObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().object()).isEqualTo(OBJECT);
    }

    @Test
    void statMapsMissingObjectToNotExists() throws Exception {
        ErrorResponse missing = mock(ErrorResponse.class);
        when(missing.code()).thenReturn("NoSuchKey");
        ErrorResponseException missingException = mock(ErrorResponseException.class);
        when(missingException.errorResponse()).thenReturn(missing);
        when(client.statObject(any(StatObjectArgs.class))).thenThrow(missingException);

        StorageGateway.ObjectStat stat = gateway.stat(BUCKET, OBJECT);

        assertThat(stat.exists()).isFalse();
        assertThat(stat.size()).isZero();
        assertThat(stat.contentType()).isNull();
    }

    @Test
    void statPropagatesNonNotFoundErrors() throws Exception {
        ErrorResponse denied = mock(ErrorResponse.class);
        when(denied.code()).thenReturn("AccessDenied");
        ErrorResponseException deniedException = mock(ErrorResponseException.class);
        when(deniedException.errorResponse()).thenReturn(denied);
        when(client.statObject(any(StatObjectArgs.class))).thenThrow(deniedException);

        assertThatThrownBy(() -> gateway.stat(BUCKET, OBJECT))
                .isInstanceOf(FileStorageException.class)
                .hasCause(deniedException);
    }

    @Test
    void downloadReturnsObjectBytesWithinLimit() throws Exception {
        byte[] content = "hello minio".getBytes(StandardCharsets.UTF_8);
        stubObjectContent(content);

        byte[] downloaded = gateway.download(BUCKET, OBJECT, 1024);

        assertThat(downloaded).isEqualTo(content);
        ArgumentCaptor<GetObjectArgs> captor = ArgumentCaptor.forClass(GetObjectArgs.class);
        verify(client).getObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().object()).isEqualTo(OBJECT);
    }

    @Test
    void downloadThrowsFileTooLargeWhenObjectExceedsMaxBytes() throws Exception {
        byte[] content = "0123456789".getBytes(StandardCharsets.UTF_8);
        stubObjectContent(content);

        assertThatThrownBy(() -> gateway.download(BUCKET, OBJECT, 3))
                .isInstanceOf(FileTooLargeException.class);
    }

    @Test
    void sha256MatchesDigestOfObjectContent() throws Exception {
        byte[] content = "sha256 content 你好".getBytes(StandardCharsets.UTF_8);
        stubObjectContent(content);

        String expected = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(content));

        assertThat(gateway.sha256(BUCKET, OBJECT, 1024)).isEqualTo(expected);
    }

    @Test
    void sha256ThrowsFileTooLargeWhenObjectExceedsMaxBytes() throws Exception {
        byte[] content = "0123456789".getBytes(StandardCharsets.UTF_8);
        stubObjectContent(content);

        assertThatThrownBy(() -> gateway.sha256(BUCKET, OBJECT, 3))
                .isInstanceOf(FileTooLargeException.class);
    }

    @Test
    void deleteObjectCallsRemoveObjectWithTarget() throws Exception {
        gateway.deleteObject(BUCKET, OBJECT);

        ArgumentCaptor<RemoveObjectArgs> captor = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(client).removeObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().object()).isEqualTo(OBJECT);
    }

    @Test
    void probeReportsOkWhenTemporaryObjectRoundTripSucceeds() throws Exception {
        ObjectWriteResponse putResponse = mock(ObjectWriteResponse.class);
        StatObjectResponse statResponse = mock(StatObjectResponse.class);
        when(client.putObject(any(PutObjectArgs.class))).thenReturn(putResponse);
        when(client.statObject(any(StatObjectArgs.class))).thenReturn(statResponse);
        when(statResponse.size()).thenReturn(16L);

        StorageGateway.StorageProbeResult result = gateway.probe();

        assertThat(result.ok()).isTrue();
        assertThat(result.errorCategory()).isNull();
        ArgumentCaptor<RemoveObjectArgs> captor = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(client).removeObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().object()).startsWith("probe/");
    }

    @Test
    void probeReportsConnectionFailureWithoutThrowing() throws Exception {
        when(client.putObject(any(PutObjectArgs.class)))
                .thenThrow(new IOException("connection refused"));

        StorageGateway.StorageProbeResult result = gateway.probe();

        assertThat(result.ok()).isFalse();
        assertThat(result.errorCategory()).isEqualTo("CONNECTION");
        verify(client, never()).removeObject(any(RemoveObjectArgs.class));
    }

    @Test
    void probeReportsIoErrorCategoryWithoutThrowing() throws Exception {
        ErrorResponse denied = mock(ErrorResponse.class);
        when(denied.code()).thenReturn("AccessDenied");
        ErrorResponseException deniedException = mock(ErrorResponseException.class);
        when(deniedException.errorResponse()).thenReturn(denied);
        when(client.putObject(any(PutObjectArgs.class))).thenThrow(deniedException);

        StorageGateway.StorageProbeResult result = gateway.probe();

        assertThat(result.ok()).isFalse();
        assertThat(result.errorCategory()).isEqualTo("IO");
        verify(client, never()).removeObject(any(RemoveObjectArgs.class));
    }

    /** 让 mock 的 GetObjectResponse 按顺序吐出 content 字节，读尽后返回 -1。 */
    private void stubObjectContent(byte[] content) throws Exception {
        GetObjectResponse response = mock(GetObjectResponse.class);
        when(client.getObject(any(GetObjectArgs.class))).thenReturn(response);
        AtomicInteger position = new AtomicInteger();
        when(response.read(any(byte[].class), anyInt(), anyInt())).thenAnswer(invocation -> {
            byte[] buffer = invocation.getArgument(0);
            int offset = invocation.getArgument(1);
            int length = invocation.getArgument(2);
            int remaining = content.length - position.get();
            if (remaining <= 0) {
                return -1;
            }
            int count = Math.min(length, remaining);
            System.arraycopy(content, position.get(), buffer, offset, count);
            position.addAndGet(count);
            return count;
        });
    }
}
