package com.educloud.live.spi;

import com.educloud.live.enums.LiveProviderType;
import com.educloud.live.exception.LiveErrorCode;
import com.educloud.live.exception.LiveException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class LiveStreamProviderFactory {

    private final Map<LiveProviderType, LiveStreamProvider> providerMap = new EnumMap<>(LiveProviderType.class);

    public LiveStreamProviderFactory(List<LiveStreamProvider> providers) {
        if (providers != null) {
            for (LiveStreamProvider provider : providers) {
                providerMap.put(provider.getProviderType(), provider);
            }
        }
    }

    public LiveStreamProvider getProvider(LiveProviderType type) {
        if (type == null) {
            type = LiveProviderType.MOCK;
        }
        LiveStreamProvider provider = providerMap.get(type);
        if (provider == null) {
            throw new LiveException(LiveErrorCode.LIVE_STREAM_PROVIDER_NOT_SUPPORTED,
                    "不支持的流媒体供应商: " + type);
        }
        return provider;
    }
}
