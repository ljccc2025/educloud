package com.educloud.live.spi;

import com.educloud.live.entity.LiveRoomEntity;
import com.educloud.live.enums.LiveProviderType;
import com.educloud.live.spi.model.LiveStreamPlayUrls;
import com.educloud.live.spi.model.LiveStreamPushUrl;
import com.educloud.live.spi.model.StreamStatus;

public interface LiveStreamProvider {

    LiveProviderType getProviderType();

    LiveStreamPushUrl generatePushUrl(LiveRoomEntity room);

    LiveStreamPlayUrls generatePlayUrls(LiveRoomEntity room);

    StreamStatus queryStreamStatus(String streamKey);

    boolean banStream(String streamKey);
}
