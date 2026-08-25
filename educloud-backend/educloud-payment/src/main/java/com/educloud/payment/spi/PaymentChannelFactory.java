package com.educloud.payment.spi;

import com.educloud.payment.enums.PaymentChannel;
import com.educloud.payment.exception.PaymentBizException;
import com.educloud.payment.exception.PaymentErrorCode;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class PaymentChannelFactory {

    private final Map<PaymentChannel, PaymentChannelPlugin> pluginMap = new EnumMap<>(PaymentChannel.class);

    public PaymentChannelFactory(List<PaymentChannelPlugin> plugins) {
        if (plugins != null) {
            for (PaymentChannelPlugin plugin : plugins) {
                pluginMap.put(plugin.getChannel(), plugin);
            }
        }
    }

    public PaymentChannelPlugin getPlugin(PaymentChannel channel) {
        if (channel == null) {
            throw new PaymentBizException(PaymentErrorCode.CHANNEL_NOT_SUPPORTED, "Payment channel cannot be null");
        }
        PaymentChannelPlugin plugin = pluginMap.get(channel);
        if (plugin == null) {
            throw new PaymentBizException(PaymentErrorCode.CHANNEL_NOT_SUPPORTED, "Unsupported payment channel: " + channel);
        }
        return plugin;
    }
}
