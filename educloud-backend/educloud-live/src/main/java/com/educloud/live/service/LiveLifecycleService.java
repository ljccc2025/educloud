package com.educloud.live.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.educloud.live.dto.request.LiveRoomCreateRequest;
import com.educloud.live.dto.request.LiveRoomPageQuery;
import com.educloud.live.dto.request.LiveRoomUpdateRequest;
import com.educloud.live.dto.response.LiveEndResponse;
import com.educloud.live.dto.response.LiveRoomDetailResponse;
import com.educloud.live.dto.response.LiveStartResponse;

public interface LiveLifecycleService {

    LiveRoomDetailResponse createRoom(Long currentUserId, boolean isAdmin, LiveRoomCreateRequest request);

    LiveRoomDetailResponse updateRoom(Long roomId, Long currentUserId, boolean isAdmin, LiveRoomUpdateRequest request);

    LiveStartResponse startLive(Long roomId, Long currentUserId, boolean isAdmin);

    LiveEndResponse endLive(Long roomId, Long currentUserId, boolean isAdmin);

    void cancelRoom(Long roomId, Long currentUserId, boolean isAdmin);

    LiveRoomDetailResponse getRoomDetail(Long roomId, Long currentUserId, boolean isTeacherOrAdmin);

    Page<LiveRoomDetailResponse> listRooms(LiveRoomPageQuery query, Long currentUserId);

    void toggleMute(Long roomId, Long currentUserId, boolean isTeacherOrAdmin, Boolean allowChat);
}
