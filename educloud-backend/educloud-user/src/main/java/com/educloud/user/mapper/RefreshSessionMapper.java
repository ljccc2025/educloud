package com.educloud.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.user.entity.RefreshSessionEntity;
import org.apache.ibatis.annotations.Mapper;

/** Refresh 会话数据访问（RefreshSessionEntity）。MyBatis-Plus 参数绑定，禁止拼接动态表名/列名（开发规范第 7 节）。 */
@Mapper
public interface RefreshSessionMapper extends BaseMapper<RefreshSessionEntity> {
}
