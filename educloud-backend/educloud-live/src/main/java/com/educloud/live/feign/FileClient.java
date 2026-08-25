package com.educloud.live.feign;

import com.educloud.live.feign.dto.DownloadGrantRequest;
import com.educloud.live.feign.dto.DownloadGrantResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "educloud-file", url = "${educloud.file.endpoint:}")
public interface FileClient {

    @PostMapping("/internal/v1/files/{id}/download-grants")
    DownloadGrantResponse grantSingle(
            @PathVariable("id") Long id,
            @RequestBody DownloadGrantRequest request,
            @RequestHeader(value = "X-Internal-Token", required = false) String internalToken,
            @RequestHeader(value = "X-Client-Id", defaultValue = "educloud-live") String clientId);
}
