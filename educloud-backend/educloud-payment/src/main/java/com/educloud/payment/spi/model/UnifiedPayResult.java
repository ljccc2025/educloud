package com.educloud.payment.spi.model;

import com.educloud.payment.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnifiedPayResult {
    private boolean success;
    private PaymentStatus status;
    private String channelTradeNo;
    private String payUrl;
    private String qrCode;
    private String rawResponse;
    private String errorCode;
    private String errorMessage;
    private Map<String, Object> extraData;
}
