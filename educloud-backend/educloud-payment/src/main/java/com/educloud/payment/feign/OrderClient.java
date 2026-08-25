package com.educloud.payment.feign;

import com.educloud.common.api.ApiResponse;
import com.educloud.payment.feign.dto.OrderPayableSnapshotResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "educloud-order", url = "${educloud.order-service-url:}")
public interface OrderClient {

    @GetMapping("/internal/v1/orders/{orderId}/payable-snapshot")
    ApiResponse<OrderPayableSnapshotResponse> getPayableSnapshot(
            @PathVariable("orderId") Long orderId,
            @RequestHeader(value = "X-Internal-Token", required = false) String internalToken);
}
