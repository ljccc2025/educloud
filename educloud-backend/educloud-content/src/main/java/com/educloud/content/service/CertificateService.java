package com.educloud.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.educloud.common.error.BusinessException;
import com.educloud.content.entity.CourseCertificateEntity;
import com.educloud.content.exception.ContentErrorCode;
import com.educloud.content.mapper.CourseCertificateMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 完课证书服务（角色化动态流阶段 3，规格 §6）：
 * <ul>
 *   <li>{@link #issueCertificate}：幂等颁发（先查 {@code uk_user_course}，存在直接返回；
 *       并发冲突由唯一约束兜底，捕获 {@link DuplicateKeyException} 后回查已有证书返回）。</li>
 *   <li>证书编号：{@code CERT-{yyyyMMdd}-{6位随机}}，编号碰撞（{@code uk_cert_no}）时换号重试。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class CertificateService {

    private static final Logger log = LoggerFactory.getLogger(CertificateService.class);
    private static final DateTimeFormatter CERT_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    /** 证书编号冲突重试上限（6 位随机后缀碰撞概率极低，超限说明存在异常）。 */
    private static final int MAX_CERT_NO_ATTEMPTS = 5;

    private final CourseCertificateMapper certificateMapper;

    /**
     * 幂等颁发完课证书：同一学员同一课程仅一张。
     *
     * @return 新颁发或已存在的证书实体
     */
    public CourseCertificateEntity issueCertificate(Long studentId, Long courseId, String courseTitle) {
        CourseCertificateEntity existing = findCertificate(studentId, courseId);
        if (existing != null) {
            return existing;
        }

        CourseCertificateEntity certificate = new CourseCertificateEntity();
        certificate.setUserId(studentId);
        certificate.setCourseId(courseId);
        certificate.setCourseTitle(courseTitle);
        certificate.setIssuedAt(LocalDateTime.now());

        for (int attempt = 1; attempt <= MAX_CERT_NO_ATTEMPTS; attempt++) {
            certificate.setCertNo(generateCertNo(certificate.getIssuedAt()));
            try {
                certificateMapper.insert(certificate);
                log.info("Course certificate issued: certNo={}, studentId={}, courseId={}",
                        certificate.getCertNo(), studentId, courseId);
                return certificate;
            } catch (DuplicateKeyException duplicate) {
                // uk_user_course 冲突（并发重复颁发）→ 回查已有证书直接返回；
                // 否则为 uk_cert_no 编号碰撞 → 换号重试。
                existing = findCertificate(studentId, courseId);
                if (existing != null) {
                    return existing;
                }
                log.warn("Certificate number collision, retrying: certNo={}, attempt={}",
                        certificate.getCertNo(), attempt);
            }
        }
        throw new IllegalStateException(
                "Failed to allocate unique certificate number after " + MAX_CERT_NO_ATTEMPTS
                        + " attempts: studentId=" + studentId + ", courseId=" + courseId);
    }

    /** 查询学员某课程的证书；未颁发返回 null。 */
    public CourseCertificateEntity findCertificate(Long studentId, Long courseId) {
        return certificateMapper.selectOne(
                new LambdaQueryWrapper<CourseCertificateEntity>()
                        .eq(CourseCertificateEntity::getUserId, studentId)
                        .eq(CourseCertificateEntity::getCourseId, courseId));
    }

    /** 学员的全部证书（按颁发时间倒序）。 */
    public List<CourseCertificateEntity> listCertificates(Long studentId) {
        return certificateMapper.selectList(
                new LambdaQueryWrapper<CourseCertificateEntity>()
                        .eq(CourseCertificateEntity::getUserId, studentId)
                        .orderByDesc(CourseCertificateEntity::getIssuedAt));
    }

    /** 按证书编号查询证书详情；不存在抛 {@link ContentErrorCode#CERTIFICATE_NOT_FOUND}。 */
    public CourseCertificateEntity getByCertNo(String certNo) {
        CourseCertificateEntity certificate = certificateMapper.selectOne(
                new LambdaQueryWrapper<CourseCertificateEntity>()
                        .eq(CourseCertificateEntity::getCertNo, certNo));
        if (certificate == null) {
            throw new BusinessException(ContentErrorCode.CERTIFICATE_NOT_FOUND,
                    "Certificate not found: " + certNo);
        }
        return certificate;
    }

    /** 证书编号：CERT-{yyyyMMdd}-{6位随机}。 */
    private String generateCertNo(LocalDateTime issuedAt) {
        int suffix = ThreadLocalRandom.current().nextInt(1_000_000);
        return "CERT-" + CERT_DATE_FORMAT.format(issuedAt) + "-" + String.format("%06d", suffix);
    }
}
