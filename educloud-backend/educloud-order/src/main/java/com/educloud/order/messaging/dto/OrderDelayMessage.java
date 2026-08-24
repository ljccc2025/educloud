package com.educloud.order.messaging.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDelayMessage implements Serializable {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderId;

    private String orderNo;
    private LocalDateTime createdAt;
}
