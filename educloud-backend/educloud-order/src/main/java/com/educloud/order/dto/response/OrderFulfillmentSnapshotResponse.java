package com.educloud.order.dto.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderFulfillmentSnapshotResponse {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderId;

    private String status;
    private Integer aggregateVersion;
    private List<OrderItemResponse> items;
}
