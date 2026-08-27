package com.educloud.content.service;

import com.educloud.common.error.BusinessException;
import com.educloud.content.entity.CourseCertificateEntity;
import com.educloud.content.exception.ContentErrorCode;
import com.educloud.content.mapper.CourseCertificateMapper;
import com.educloud.content.support.MybatisPlusTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CertificateService 单测（角色化动态流阶段 3）：
 * 幂等颁发、证书编号格式/唯一、编号碰撞重试、并发唯一约束兜底。
 */
@ExtendWith(MockitoExtension.class)
class CertificateServiceTest {

    @BeforeAll
    static void initMybatisPlus() {
        MybatisPlusTestSupport.registerTableInfo(CourseCertificateEntity.class);
    }

    @Mock
    private CourseCertificateMapper certificateMapper;

    @InjectMocks
    private CertificateService certificateService;

    @Test
    void issueCertificate_insertsNewCertificateWithGeneratedNo() {
        when(certificateMapper.selectOne(any())).thenReturn(null);
        when(certificateMapper.insert(any(CourseCertificateEntity.class))).thenReturn(1);

        CourseCertificateEntity cert = certificateService.issueCertificate(2001L, 101L, "Spring Boot 微服务实践");

        assertThat(cert).isNotNull();
        assertThat(cert.getUserId()).isEqualTo(2001L);
        assertThat(cert.getCourseId()).isEqualTo(101L);
        assertThat(cert.getCourseTitle()).isEqualTo("Spring Boot 微服务实践");
        assertThat(cert.getCertNo()).matches("CERT-\\d{8}-\\d{6}");
        assertThat(cert.getIssuedAt()).isNotNull();
        verify(certificateMapper, times(1)).insert(any(CourseCertificateEntity.class));
    }

    @Test
    void issueCertificate_isIdempotent_returnsExistingWithoutInsert() {
        CourseCertificateEntity existing = new CourseCertificateEntity();
        existing.setId(11L);
        existing.setCertNo("CERT-20260827-000001");
        existing.setUserId(2001L);
        existing.setCourseId(101L);
        existing.setCourseTitle("Spring Boot 微服务实践");
        existing.setIssuedAt(LocalDateTime.of(2026, 8, 27, 9, 0));

        when(certificateMapper.selectOne(any())).thenReturn(existing);

        CourseCertificateEntity cert = certificateService.issueCertificate(2001L, 101L, "Spring Boot 微服务实践");

        assertThat(cert).isSameAs(existing);
        verify(certificateMapper, never()).insert(any(CourseCertificateEntity.class));
    }

    @Test
    void issueCertificate_generatedCertNosAreUnique() {
        when(certificateMapper.selectOne(any())).thenReturn(null);
        when(certificateMapper.insert(any(CourseCertificateEntity.class))).thenReturn(1);

        ArgumentCaptor<CourseCertificateEntity> captor = ArgumentCaptor.forClass(CourseCertificateEntity.class);

        List<String> certNos = new ArrayList<>();
        for (long studentId = 1; studentId <= 20; studentId++) {
            CourseCertificateEntity cert = certificateService.issueCertificate(studentId, 101L, "课程A");
            certNos.add(cert.getCertNo());
        }

        verify(certificateMapper, times(20)).insert(captor.capture());
        assertThat(certNos).hasSize(20);
        assertThat(certNos).doesNotHaveDuplicates();
        assertThat(captor.getAllValues())
                .extracting(CourseCertificateEntity::getUserId)
                .containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L, 13L, 14L, 15L, 16L, 17L, 18L, 19L, 20L);
    }

    @Test
    void issueCertificate_certNoCollisionRetriesWithNewNo() {
        when(certificateMapper.selectOne(any())).thenReturn(null);
        // 第一次插入撞号（uk_cert_no），回查无已有证书 → 换新编号重试成功。
        List<String> attemptedCertNos = new ArrayList<>();
        when(certificateMapper.insert(any(CourseCertificateEntity.class))).thenAnswer(invocation -> {
            CourseCertificateEntity entity = invocation.getArgument(0);
            attemptedCertNos.add(entity.getCertNo());
            if (attemptedCertNos.size() == 1) {
                throw new DuplicateKeyException("uk_cert_no");
            }
            return 1;
        });

        CourseCertificateEntity cert = certificateService.issueCertificate(2001L, 101L, "课程A");

        verify(certificateMapper, times(2)).insert(any(CourseCertificateEntity.class));
        assertThat(attemptedCertNos).hasSize(2);
        assertThat(attemptedCertNos.get(0)).isNotEqualTo(attemptedCertNos.get(1));
        assertThat(cert.getCertNo()).isEqualTo(attemptedCertNos.get(1)).matches("CERT-\\d{8}-\\d{6}");
    }

    @Test
    void issueCertificate_concurrentDuplicateReturnsExistingCertificate() {
        CourseCertificateEntity existing = new CourseCertificateEntity();
        existing.setId(12L);
        existing.setCertNo("CERT-20260827-000002");
        existing.setUserId(2001L);
        existing.setCourseId(101L);

        // 先查无证书 → 插入撞 uk_user_course（并发已颁发）→ 回查命中已有证书。
        when(certificateMapper.selectOne(any())).thenReturn(null).thenReturn(existing);
        when(certificateMapper.insert(any(CourseCertificateEntity.class)))
                .thenThrow(new DuplicateKeyException("uk_user_course"));

        CourseCertificateEntity cert = certificateService.issueCertificate(2001L, 101L, "课程A");

        assertThat(cert).isSameAs(existing);
        verify(certificateMapper, times(1)).insert(any(CourseCertificateEntity.class));
    }

    @Test
    void getByCertNo_returnsCertificate() {
        CourseCertificateEntity cert = new CourseCertificateEntity();
        cert.setCertNo("CERT-20260827-000001");
        when(certificateMapper.selectOne(any())).thenReturn(cert);

        assertThat(certificateService.getByCertNo("CERT-20260827-000001")).isSameAs(cert);
    }

    @Test
    void getByCertNo_notFoundThrowsBusinessException() {
        when(certificateMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> certificateService.getByCertNo("CERT-404"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ContentErrorCode.CERTIFICATE_NOT_FOUND);
    }
}
