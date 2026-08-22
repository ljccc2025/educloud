package com.educloud.file.storage;

import com.educloud.file.testcontainers.TestContainerImages;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M04 任务 3：MinioStorageGateway 集成测试（Testcontainers minio/minio）。
 *
 * <p>验证真实链路：presigned PUT URL → 客户端 PUT 上传字节 → stat 元信息 →
 * sha256 与上传内容一致 → download 回读一致 → delete 后 stat 不存在。
 * 本地无 Docker 时该测试跳过（由 VM 以 -Pintegration 补跑），编译不受影响。</p>
 */
class MinioStorageGatewayIT {

    private static final String ROOT_USER = "minioadmin";
    private static final String ROOT_PASSWORD = "minioadmin";

    private static GenericContainer<?> minioContainer;
    private static String endpoint;

    @BeforeAll
    static void startMinio() {
        Assumptions.assumeTrue(
                DockerClientFactory.instance().isDockerAvailable(),
                "Docker 不可用，跳过 MinIO 集成测试（由具备 Docker 的 VM 补跑）");
        minioContainer = new GenericContainer<>(TestContainerImages.minio())
                .withEnv("MINIO_ROOT_USER", ROOT_USER)
                .withEnv("MINIO_ROOT_PASSWORD", ROOT_PASSWORD)
                .withCommand("server", "/data")
                .withExposedPorts(9000);
        minioContainer.start();
        endpoint = "http://" + minioContainer.getHost() + ":" + minioContainer.getMappedPort(9000);
    }

    @AfterAll
    static void stopMinio() {
        if (minioContainer != null) {
            minioContainer.stop();
        }
    }

    @Test
    void presignedPutStatSha256AndDeleteRoundTrip() throws Exception {
        MinioClient client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(ROOT_USER, ROOT_PASSWORD)
                .build();
        String bucket = "educloud-files-it-" + UUID.randomUUID();
        ensureBucket(client, bucket);
        MinioStorageGateway gateway = new MinioStorageGateway(client, bucket);

        byte[] payload = "educloud minio storage round trip 你好"
                .getBytes(StandardCharsets.UTF_8);
        String objectKey = "it/round-trip-" + UUID.randomUUID() + ".bin";
        String contentType = "application/octet-stream";

        String putUrl = gateway.presignedPutUrl(bucket, objectKey, contentType, Duration.ofMinutes(5));
        assertThat(putUrl).startsWith(endpoint + "/" + bucket + "/" + objectKey)
                .contains("X-Amz-Signature");

        HttpResponse<byte[]> putResponse = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(putUrl))
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(payload))
                        .header("Content-Type", contentType)
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(putResponse.statusCode())
                .as("presigned PUT 应返回 200")
                .isEqualTo(200);

        StorageGateway.ObjectStat stat = gateway.stat(bucket, objectKey);
        assertThat(stat.exists()).isTrue();
        assertThat(stat.size()).isEqualTo(payload.length);
        assertThat(stat.contentType()).isEqualTo(contentType);

        String expectedSha256 = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(payload));
        assertThat(gateway.sha256(bucket, objectKey, payload.length + 1))
                .isEqualTo(expectedSha256);

        assertThat(gateway.download(bucket, objectKey, payload.length)).isEqualTo(payload);

        gateway.deleteObject(bucket, objectKey);
        assertThat(gateway.stat(bucket, objectKey).exists()).isFalse();
    }

    @Test
    void probeReportsOkAgainstRealMinio() throws Exception {
        MinioClient client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(ROOT_USER, ROOT_PASSWORD)
                .build();
        String bucket = "educloud-files-it-" + UUID.randomUUID();
        ensureBucket(client, bucket);
        MinioStorageGateway gateway = new MinioStorageGateway(client, bucket);

        StorageGateway.StorageProbeResult result = gateway.probe();

        assertThat(result.ok()).isTrue();
        assertThat(result.errorCategory()).isNull();
    }

    private static void ensureBucket(MinioClient client, String bucket) throws Exception {
        if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }
}
