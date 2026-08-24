package com.educloud.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("cart_item")
public class CartItemEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long studentId;

    private Long courseId;

    private Boolean selected;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
