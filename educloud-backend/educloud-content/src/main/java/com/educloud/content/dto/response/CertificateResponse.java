package com.educloud.content.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/** 完课证书响应（角色化动态流阶段 3）。 */
@Data
public class CertificateResponse {
    private String certNo;
    private Long courseId;
    private String courseTitle;
    private LocalDateTime issuedAt;
}
