package com.educloud.payment.service;

import com.educloud.payment.enums.PaymentChannel;

import java.util.Map;

public interface PaymentCallbackService {

    String handleCallback(PaymentChannel channel, Map<String, String> headers, Map<String, String> params, String rawBody);
}
