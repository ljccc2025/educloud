package com.educloud.live.exception;

import com.educloud.common.error.ErrorCode;

public enum LiveErrorCode implements ErrorCode {
    LIVE_ROOM_NOT_FOUND(404, 404901, "直播间不存在"),
    LIVE_ROOM_STATUS_INVALID(400, 400901, "直播间状态不支持当前操作"),
    LIVE_ROOM_TIME_INVALID(400, 400902, "计划直播时间不合法，结束时间必须晚于开始时间"),
    LIVE_COURSE_NOT_OWNED(403, 403901, "无权管理非本人主讲的课程直播间"),
    COURSE_NOT_ENROLLED(403, 403902, "未报名该课程或选课已被注销，无权进入直播间"),
    LIVE_TICKET_EXPIRED_OR_INVALID(401, 401901, "直播间握手票据已过期或无效"),
    LIVE_CHAT_MUTED(403, 403903, "当前直播间处于全员禁言状态"),
    LIVE_MESSAGE_NOT_FOUND(404, 404902, "直播消息不存在"),
    LIVE_MESSAGE_RECALL_FORBIDDEN(403, 403904, "无权撤回该消息或已超过2分钟撤回时限"),
    LIVE_REPLAY_NOT_FOUND(404, 404903, "直播录制回放不存在"),
    LIVE_STREAM_PROVIDER_NOT_SUPPORTED(400, 400903, "不支持的流媒体供应商类型"),
    MOCK_STREAM_DISABLED(403, 403905, "当前生产环境禁用 Mock 模拟流媒体服务"),
    FILE_GRANT_FAILED(500, 500901, "获取回放文件下载授权失败");

    private final int httpStatus;
    private final int businessCode;
    private final String defaultMessage;

    LiveErrorCode(int httpStatus, int businessCode, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.businessCode = businessCode;
        this.defaultMessage = defaultMessage;
    }

    public int getBusinessCode() {
        return businessCode;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public int httpStatus() {
        return httpStatus;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }
}
