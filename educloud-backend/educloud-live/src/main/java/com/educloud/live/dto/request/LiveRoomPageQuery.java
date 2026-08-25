package com.educloud.live.dto.request;

import com.educloud.live.enums.LiveRoomStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiveRoomPageQuery {

    private Long courseId;
    private Long teacherId;
    private LiveRoomStatus status;
    @Builder.Default
    private Integer page = 1;
    @Builder.Default
    private Integer size = 20;

    public int getSafePage() {
        return page != null && page > 0 ? page : 1;
    }

    public int getSafeSize() {
        if (size == null || size <= 0) {
            return 20;
        }
        return Math.min(size, 100);
    }
}
