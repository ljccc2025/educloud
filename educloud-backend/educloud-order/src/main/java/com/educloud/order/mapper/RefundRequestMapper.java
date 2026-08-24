package com.educloud.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.order.entity.RefundRequestEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RefundRequestMapper extends BaseMapper<RefundRequestEntity> {

    @Select("SELECT * FROM refund_request WHERE id = #{id} FOR UPDATE")
    RefundRequestEntity selectByIdForUpdate(@Param("id") Long id);
}
