package com.educloud.user.support;

import com.educloud.common.web.RequestContext;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 请求 ID 解析：优先取 RequestContextFilter 写入的 attribute（直连时由 common 生成），
 * 其次取 X-Request-Id 头（Gateway 转发注入），最后兜底 "unavailable"（保证审计列非空）。
 * 依据：M01 common RequestContextFilter（生成 requestId 写 attribute/响应头，不回写请求头）。
 */
public final class RequestIds {

    private RequestIds() {
    }

    public static String from(HttpServletRequest request) {
        Object attribute = request.getAttribute(RequestContext.REQUEST_ID_ATTRIBUTE);
        if (attribute instanceof String text && !text.isBlank()) {
            return text;
        }
        String header = request.getHeader(RequestContext.REQUEST_ID_HEADER);
        return header == null || header.isBlank() ? "unavailable" : header;
    }
}
