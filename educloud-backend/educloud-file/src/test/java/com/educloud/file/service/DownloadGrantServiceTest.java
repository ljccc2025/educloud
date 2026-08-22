package com.educloud.file.service;

import com.educloud.common.error.BusinessException;
import com.educloud.common.error.CommonErrorCode;
import com.educloud.file.config.FileProperties;
import com.educloud.file.entity.FileBindingEntity;
import com.educloud.file.entity.FileObjectEntity;
import com.educloud.file.exception.FileAccessDeniedException;
import com.educloud.file.exception.GrantPurposeNotAllowedException;
import com.educloud.file.mapper.FileBindingMapper;
import com.educloud.file.mapper.FileObjectMapper;
import com.educloud.file.observability.FileMetrics;
import com.educloud.file.service.DownloadGrantService.BatchGrantResult;
import com.educloud.file.service.DownloadGrantService.BatchItem;
import com.educloud.file.service.DownloadGrantService.BatchItemResult;
import com.educloud.file.service.DownloadGrantService.GrantBatchRequest;
import com.educloud.file.service.DownloadGrantService.GrantResult;
import com.educloud.file.service.DownloadGrantService.GrantSingleRequest;
import com.educloud.file.service.DownloadGrantService.GrantStatus;
import com.educloud.file.storage.StorageGateway;
import com.educloud.file.support.FileAccessAuditWriter;
import com.educloud.file.support.GrantPurposePolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M04 任务 6：内部下载授权服务单元测试（mock Mapper/StorageGateway/审计写入，不依赖真实 DB/存储）。
 *
 * <p>依据：2026-08-22-educloud-file-plan.md 任务 6 与设计规格 6.2/7.3 节 ——
 * 精确绑定（ownerService+ownerType+ownerId 且 unbound_at IS NULL）+ 文件 AVAILABLE 才 GRANTED；
 * 未绑定/不可用 → UNAVAILABLE；owner 伪造（存在绑定行但 owner 不匹配）→ 整批 FileAccessDeniedException
 * + GRANT_BATCH_DENIED 审计；purpose 越权拒绝；TTL 超过 max-ttl 钳制；批量 ≤100 且 requestKey 去重。</p>
 */
@ExtendWith(MockitoExtension.class)
class DownloadGrantServiceTest {

    private static final String OWNER_SERVICE = "educloud-user";
    private static final String OWNER_TYPE = "USER_PROFILE";
    private static final String OWNER_ID = "u-42";
    private static final long FILE_ID = 1001L;
    private static final long SUBJECT_USER_ID = 7L;
    private static final String BUCKET = "educloud-files";
    private static final String OBJECT_KEY = "educloud-files/user-42/20260822/abc.png";
    private static final Instant NOW = Instant.parse("2026-08-22T11:30:00Z");
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);
    private static final Duration MAX_TTL = Duration.ofMinutes(15);

    @Mock
    private FileBindingMapper bindingMapper;
    @Mock
    private FileObjectMapper objectMapper;
    @Mock
    private StorageGateway storageGateway;
    @Mock
    private FileAccessAuditWriter auditWriter;
    @Mock
    private FileMetrics metrics;

    private DownloadGrantService service;
    private FileProperties properties;

    @BeforeEach
    void setUp() {
        properties = fileProperties();
        GrantPurposePolicy purposePolicy =
                new GrantPurposePolicy(properties.downloadGrant().purposes());
        service = new DownloadGrantService(
                bindingMapper, objectMapper, storageGateway, purposePolicy, auditWriter,
                properties, Clock.fixed(NOW, ZoneOffset.UTC), metrics);
    }

    @Test
    void grantSingleReturnsPresignedGetUrlWhenExactBindingAndFileAvailable() {
        when(bindingMapper.findActiveByOwner(FILE_ID, OWNER_SERVICE, OWNER_TYPE, OWNER_ID))
                .thenReturn(activeBinding(OWNER_ID));
        when(objectMapper.selectById(FILE_ID)).thenReturn(availableFile());
        when(storageGateway.presignedGetUrl(BUCKET, OBJECT_KEY, DEFAULT_TTL))
                .thenReturn("https://minio.example/educloud-files/abc.png?X-Amz-Signature=s1");

        GrantResult result = service.grantSingle(
                OWNER_SERVICE, singleRequest("USER", SUBJECT_USER_ID, OWNER_TYPE, OWNER_ID, "PROFILE_AVATAR", null));

        assertThat(result.status()).isEqualTo(GrantStatus.GRANTED);
        assertThat(result.url()).isEqualTo("https://minio.example/educloud-files/abc.png?X-Amz-Signature=s1");
        assertThat(result.expiresAt()).isEqualTo(NOW.plus(DEFAULT_TTL));
        verify(storageGateway).presignedGetUrl(BUCKET, OBJECT_KEY, DEFAULT_TTL);
        verify(metrics).recordGrantGranted();
    }

    @Test
    void grantSingleReturnsUnavailableWhenBindingExistsButFileNotAvailable() {
        when(bindingMapper.findActiveByOwner(FILE_ID, OWNER_SERVICE, OWNER_TYPE, OWNER_ID))
                .thenReturn(activeBinding(OWNER_ID));
        FileObjectEntity file = availableFile();
        file.setStatus("UPLOADING");
        when(objectMapper.selectById(FILE_ID)).thenReturn(file);

        GrantResult result = service.grantSingle(
                OWNER_SERVICE, singleRequest("USER", SUBJECT_USER_ID, OWNER_TYPE, OWNER_ID, "PROFILE_AVATAR", null));

        assertThat(result.status()).isEqualTo(GrantStatus.UNAVAILABLE);
        assertThat(result.url()).isNull();
        assertThat(result.expiresAt()).isNull();
        verify(storageGateway, never()).presignedGetUrl(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void grantSingleReturnsUnavailableWhenNoBinding() {
        when(bindingMapper.findActiveByOwner(FILE_ID, OWNER_SERVICE, OWNER_TYPE, OWNER_ID))
                .thenReturn(null);
        when(bindingMapper.findActiveByFileId(FILE_ID)).thenReturn(List.of());

        GrantResult result = service.grantSingle(
                OWNER_SERVICE, singleRequest("USER", SUBJECT_USER_ID, OWNER_TYPE, OWNER_ID, "PROFILE_AVATAR", null));

        assertThat(result.status()).isEqualTo(GrantStatus.UNAVAILABLE);
        assertThat(result.url()).isNull();
        assertThat(result.expiresAt()).isNull();
        verify(objectMapper, never()).selectById(FILE_ID);
        verify(storageGateway, never()).presignedGetUrl(anyString(), anyString(), any(Duration.class));
        verify(metrics).recordGrantDenied();
    }

    @Test
    void grantSingleRejectsOwnerMismatch() {
        when(bindingMapper.findActiveByOwner(FILE_ID, OWNER_SERVICE, OWNER_TYPE, OWNER_ID))
                .thenReturn(null);
        // 文件存在绑定行但 owner 不匹配（同 service+type、不同 ownerId）→ 视为伪造
        when(bindingMapper.findActiveByFileId(FILE_ID))
                .thenReturn(List.of(activeBinding("u-999")));

        assertThatThrownBy(() -> service.grantSingle(
                OWNER_SERVICE, singleRequest("USER", SUBJECT_USER_ID, OWNER_TYPE, OWNER_ID, "PROFILE_AVATAR", null)))
                .isInstanceOf(FileAccessDeniedException.class);

        verify(auditWriter).writeGrantSingle(FILE_ID, SUBJECT_USER_ID, false);
        verify(storageGateway, never()).presignedGetUrl(anyString(), anyString(), any(Duration.class));
        verify(metrics).recordGrantDenied();
    }

    @Test
    void grantSingleRejectsPurposeViolations() {
        // ANONYMOUS 仅限 PUBLIC_CATALOG
        assertThatThrownBy(() -> service.grantSingle(
                OWNER_SERVICE, singleRequest("ANONYMOUS", null, OWNER_TYPE, OWNER_ID, "PROFILE_AVATAR", null)))
                .isInstanceOf(GrantPurposeNotAllowedException.class);
        // USER 必须携带 subjectUserId
        assertThatThrownBy(() -> service.grantSingle(
                OWNER_SERVICE, singleRequest("USER", null, OWNER_TYPE, OWNER_ID, "PROFILE_AVATAR", null)))
                .isInstanceOf(GrantPurposeNotAllowedException.class);
        // 未知 purpose
        assertThatThrownBy(() -> service.grantSingle(
                OWNER_SERVICE, singleRequest("USER", SUBJECT_USER_ID, OWNER_TYPE, OWNER_ID, "INTERNAL_ONLY", null)))
                .isInstanceOf(GrantPurposeNotAllowedException.class);
        verify(bindingMapper, never()).findActiveByOwner(anyLong(), anyString(), anyString(), anyString());
        // 越权/伪造拒绝均需 GRANT_SINGLE FAILURE 审计（ANONYMOUS 与缺失 subjectUserId 为 null 主体）
        verify(auditWriter).writeGrantSingle(FILE_ID, SUBJECT_USER_ID, false);
        verify(auditWriter, org.mockito.Mockito.times(2)).writeGrantSingle(FILE_ID, null, false);
    }

    @Test
    void grantSingleClampsRequestedTtlToMaxTtl() {
        when(bindingMapper.findActiveByOwner(FILE_ID, OWNER_SERVICE, OWNER_TYPE, OWNER_ID))
                .thenReturn(activeBinding(OWNER_ID));
        when(objectMapper.selectById(FILE_ID)).thenReturn(availableFile());
        when(storageGateway.presignedGetUrl(BUCKET, OBJECT_KEY, MAX_TTL))
                .thenReturn("https://minio.example/educloud-files/abc.png?X-Amz-Signature=s1");

        GrantResult result = service.grantSingle(
                OWNER_SERVICE, singleRequest("USER", SUBJECT_USER_ID, OWNER_TYPE, OWNER_ID,
                        "PROFILE_AVATAR", Duration.ofMinutes(30)));

        assertThat(result.status()).isEqualTo(GrantStatus.GRANTED);
        assertThat(result.expiresAt()).isEqualTo(NOW.plus(MAX_TTL));
        verify(storageGateway).presignedGetUrl(BUCKET, OBJECT_KEY, MAX_TTL);
    }

    @Test
    void grantBatchRejectsOversizedAndDuplicateItemsWith400ValidationError() {
        List<BatchItem> tooMany = IntStream.range(0, 101)
                .mapToObj(i -> new BatchItem("k" + i, FILE_ID + (long) i, OWNER_TYPE, OWNER_ID))
                .toList();
        assertThatThrownBy(() -> service.grantBatch(OWNER_SERVICE,
                new GrantBatchRequest("USER", SUBJECT_USER_ID, "PROFILE_AVATAR", null, tooMany)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(CommonErrorCode.VALIDATION_FAILED);

        assertThatThrownBy(() -> service.grantBatch(OWNER_SERVICE,
                new GrantBatchRequest("USER", SUBJECT_USER_ID, "PROFILE_AVATAR", null,
                        List.of(
                                new BatchItem("k1", FILE_ID, OWNER_TYPE, OWNER_ID),
                                new BatchItem("k1", FILE_ID + 1, OWNER_TYPE, OWNER_ID)))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(CommonErrorCode.VALIDATION_FAILED);

        assertThatThrownBy(() -> service.grantBatch(OWNER_SERVICE,
                new GrantBatchRequest("USER", SUBJECT_USER_ID, "PROFILE_AVATAR",
                        Duration.ofSeconds(-1), List.of(new BatchItem("k1", FILE_ID, OWNER_TYPE, OWNER_ID)))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(CommonErrorCode.VALIDATION_FAILED);
    }

    @Test
    void grantBatchDeniesEntireBatchOnForgeryAndWritesAudit() {
        BatchItem good = new BatchItem("k-good", FILE_ID, OWNER_TYPE, OWNER_ID);
        BatchItem forged = new BatchItem("k-forged", FILE_ID, OWNER_TYPE, "u-999");
        // 正常项在第二遍才会消费 URL/文件状态；伪造先行抛出，故只 stub 绑定校验。
        when(bindingMapper.findActiveByOwner(FILE_ID, OWNER_SERVICE, OWNER_TYPE, OWNER_ID))
                .thenReturn(activeBinding(OWNER_ID));
        // 伪造项：无精确绑定（ownerId 不匹配），但文件存在绑定行 → 视为伪造
        when(bindingMapper.findActiveByOwner(FILE_ID, OWNER_SERVICE, OWNER_TYPE, "u-999"))
                .thenReturn(null);
        when(bindingMapper.findActiveByFileId(FILE_ID)).thenReturn(List.of(activeBinding(OWNER_ID)));

        assertThatThrownBy(() -> service.grantBatch(OWNER_SERVICE,
                new GrantBatchRequest("USER", SUBJECT_USER_ID, "PROFILE_AVATAR", null,
                        List.of(good, forged))))
                .isInstanceOf(FileAccessDeniedException.class);

        verify(storageGateway, never()).presignedGetUrl(anyString(), anyString(), any(Duration.class));
        verify(auditWriter).writeGrantBatchDenied(FILE_ID, SUBJECT_USER_ID);
        verify(metrics).recordGrantDenied();
    }

    @Test
    void grantBatchPurposeViolationWritesAuditWithSentinelFileId() {
        assertThatThrownBy(() -> service.grantBatch(OWNER_SERVICE,
                new GrantBatchRequest("ANONYMOUS", null, "PROFILE_AVATAR", null,
                        List.of(new BatchItem("k1", FILE_ID, OWNER_TYPE, OWNER_ID)))))
                .isInstanceOf(GrantPurposeNotAllowedException.class);

        verify(auditWriter).writeGrantBatchDenied(0L, null);
        verify(bindingMapper, never()).findActiveByOwner(anyLong(), anyString(), anyString(), anyString());
        verify(metrics).recordGrantDenied();
    }

    @Test
    void grantBatchReturnsMixedGrantedAndUnavailable() {
        BatchItem granted = new BatchItem("k1", FILE_ID, OWNER_TYPE, OWNER_ID);
        BatchItem noBinding = new BatchItem("k2", FILE_ID + 1, OWNER_TYPE, "u-999");
        BatchItem notAvailable = new BatchItem("k3", FILE_ID + 2, OWNER_TYPE, "u-777");

        when(bindingMapper.findActiveByOwner(FILE_ID, OWNER_SERVICE, OWNER_TYPE, OWNER_ID))
                .thenReturn(activeBinding(OWNER_ID));
        when(objectMapper.selectById(FILE_ID)).thenReturn(availableFile());
        when(storageGateway.presignedGetUrl(BUCKET, OBJECT_KEY, DEFAULT_TTL))
                .thenReturn("https://minio.example/educloud-files/abc.png?X-Amz-Signature=s1");

        when(bindingMapper.findActiveByOwner(FILE_ID + 1, OWNER_SERVICE, OWNER_TYPE, "u-999"))
                .thenReturn(null);
        when(bindingMapper.findActiveByFileId(FILE_ID + 1)).thenReturn(List.of());

        when(bindingMapper.findActiveByOwner(FILE_ID + 2, OWNER_SERVICE, OWNER_TYPE, "u-777"))
                .thenReturn(activeBinding("u-777"));
        FileObjectEntity quarantined = availableFile();
        quarantined.setId(FILE_ID + 2);
        quarantined.setStatus("QUARANTINED");
        when(objectMapper.selectById(FILE_ID + 2)).thenReturn(quarantined);

        BatchGrantResult result = service.grantBatch(OWNER_SERVICE,
                new GrantBatchRequest("USER", SUBJECT_USER_ID, "PROFILE_AVATAR", null,
                        List.of(granted, noBinding, notAvailable)));

        assertThat(result.items()).hasSize(3);
        assertThat(result.items()).extracting(BatchItemResult::requestKey)
                .containsExactly("k1", "k2", "k3");
        assertThat(result.items()).extracting(BatchItemResult::fileId)
                .containsExactly(FILE_ID, FILE_ID + 1, FILE_ID + 2);
        assertThat(result.items().get(0).status()).isEqualTo(GrantStatus.GRANTED);
        assertThat(result.items().get(0).url()).isEqualTo("https://minio.example/educloud-files/abc.png?X-Amz-Signature=s1");
        assertThat(result.items().get(0).expiresAt()).isEqualTo(NOW.plus(DEFAULT_TTL));
        assertThat(result.items().get(1).status()).isEqualTo(GrantStatus.UNAVAILABLE);
        assertThat(result.items().get(1).url()).isNull();
        assertThat(result.items().get(1).expiresAt()).isNull();
        assertThat(result.items().get(2).status()).isEqualTo(GrantStatus.UNAVAILABLE);
        assertThat(result.items().get(2).url()).isNull();
        assertThat(result.items().get(2).expiresAt()).isNull();
        verify(auditWriter, never()).writeGrantBatchDenied(anyLong(), anyLong());
        verify(metrics).recordGrantGranted();
        verify(metrics, org.mockito.Mockito.times(2)).recordGrantDenied();
    }

    @Test
    void grantWritesGrantSingleAuditForGrantedAndUnavailable() {
        when(bindingMapper.findActiveByFileId(FILE_ID)).thenReturn(List.of());

        when(bindingMapper.findActiveByOwner(FILE_ID, OWNER_SERVICE, OWNER_TYPE, OWNER_ID))
                .thenReturn(activeBinding(OWNER_ID));
        when(objectMapper.selectById(FILE_ID)).thenReturn(availableFile());
        when(storageGateway.presignedGetUrl(BUCKET, OBJECT_KEY, DEFAULT_TTL))
                .thenReturn("https://minio.example/educloud-files/abc.png?X-Amz-Signature=s1");

        service.grantSingle(OWNER_SERVICE,
                singleRequest("USER", SUBJECT_USER_ID, OWNER_TYPE, OWNER_ID, "PROFILE_AVATAR", null));
        verify(auditWriter).writeGrantSingle(FILE_ID, SUBJECT_USER_ID, true);

        // 未绑定（无任何活跃绑定行）→ UNAVAILABLE 审计 FAILURE
        service.grantSingle(OWNER_SERVICE,
                singleRequest("USER", SUBJECT_USER_ID, OWNER_TYPE, "u-404", "PROFILE_AVATAR", null));
        verify(auditWriter).writeGrantSingle(FILE_ID, SUBJECT_USER_ID, false);
    }

    private GrantSingleRequest singleRequest(
            String subjectType, Long subjectUserId, String ownerType, String ownerId,
            String purpose, Duration requestedTtl) {
        return new GrantSingleRequest(
                subjectType, subjectUserId, ownerType, ownerId, FILE_ID, purpose, requestedTtl);
    }

    private FileObjectEntity availableFile() {
        FileObjectEntity file = new FileObjectEntity();
        file.setId(FILE_ID);
        file.setObjectKey(OBJECT_KEY);
        file.setBucket(BUCKET);
        file.setStatus("AVAILABLE");
        file.setVersion(1);
        return file;
    }

    private FileBindingEntity activeBinding(String ownerId) {
        FileBindingEntity binding = new FileBindingEntity();
        binding.setId(9L);
        binding.setFileId(FILE_ID);
        binding.setOwnerService(OWNER_SERVICE);
        binding.setOwnerType(OWNER_TYPE);
        binding.setOwnerId(ownerId);
        binding.setBoundAt(NOW);
        binding.setUnboundAt(null);
        return binding;
    }

    private FileProperties fileProperties() {
        return new FileProperties(
                new FileProperties.Storage("http://127.0.0.1:9000", "ak", "sk", BUCKET),
                new FileProperties.Upload(
                        10485760,
                        List.of("image/jpeg", "image/png", "image/webp", "image/gif", "application/pdf"),
                        Duration.ofMinutes(5),
                        Duration.ofMinutes(15)),
                new FileProperties.DownloadGrant(DEFAULT_TTL, MAX_TTL, List.of("PROFILE_AVATAR", "PUBLIC_CATALOG")),
                new FileProperties.Cleanup(Duration.ofHours(24), Duration.ofMinutes(15), 50),
                new FileProperties.StorageTest(1, Duration.ofMinutes(1)),
                new FileProperties.Internal("bootstrap", List.of("user-service"), "educloud-file"),
                new FileProperties.Jwt("file:/jwks.json", "https://issuer.educloud.local", "educloud-api"),
                    "local");
    }
}
