package com.educloud.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.order.entity.CartItemEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CartItemMapper extends BaseMapper<CartItemEntity> {
}
