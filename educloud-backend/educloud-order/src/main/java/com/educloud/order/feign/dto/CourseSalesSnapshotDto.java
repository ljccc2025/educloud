package com.educloud.order.feign.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CourseSalesSnapshotDto {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String title;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long coverFileId;

    private BigDecimal price;

    @JsonAlias({"lifecycleStatus", "courseStatus"})
    private String status;

    @JsonAlias({"onSale"})
    private Boolean isOnSale;

    private Boolean enrolled;

    public boolean isPurchasable() {
        return "PUBLISHED".equalsIgnoreCase(status) && (isOnSale == null || Boolean.TRUE.equals(isOnSale));
    }
}
