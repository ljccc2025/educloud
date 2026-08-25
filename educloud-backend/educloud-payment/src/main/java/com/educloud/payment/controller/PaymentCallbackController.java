package com.educloud.payment.controller;

import com.educloud.payment.enums.PaymentChannel;
import com.educloud.payment.service.PaymentCallbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/payment-callbacks")
@RequiredArgsConstructor
public class PaymentCallbackController {

    private final PaymentCallbackService callbackService;

    @PostMapping(value = "/ALIPAY", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> handleAlipayCallback(
            @RequestHeader Map<String, String> headers,
            @RequestParam Map<String, String> params) {
        log.info("Received Alipay callback params: {}", params);
        String response = callbackService.handleCallback(PaymentChannel.ALIPAY, headers, params, null);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/WECHAT", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> handleWeChatCallback(
            @RequestHeader Map<String, String> headers,
            @RequestBody String rawBody) {
        log.info("Received WeChat callback headers: {}, body: {}", headers, rawBody);
        String response = callbackService.handleCallback(PaymentChannel.WECHAT, headers, null, rawBody);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(response);
    }

    @PostMapping(value = "/MOCK")
    public ResponseEntity<String> handleMockCallback(
            @RequestHeader Map<String, String> headers,
            @RequestParam(required = false) Map<String, String> params,
            @RequestBody(required = false) String rawBody) {
        log.info("Received Mock callback headers: {}, params: {}, body: {}", headers, params, rawBody);
        String response = callbackService.handleCallback(PaymentChannel.MOCK, headers, params, rawBody);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(response);
    }
}
