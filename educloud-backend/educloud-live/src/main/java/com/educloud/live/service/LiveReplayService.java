package com.educloud.live.service;

import com.educloud.live.dto.response.LiveReplayResponse;

import java.util.List;

public interface LiveReplayService {

    LiveReplayResponse getReplayDetail(Long replayId, Long currentUserId, boolean isTeacherOrAdmin);

    List<LiveReplayResponse> listReplaysByRoom(Long roomId, Long currentUserId, boolean isTeacherOrAdmin);

    void deleteReplay(Long replayId, Long currentUserId, boolean isAdmin);
}
