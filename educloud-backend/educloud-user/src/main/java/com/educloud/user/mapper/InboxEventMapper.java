package com.educloud.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.user.entity.InboxEventEntity;
import org.apache.ibatis.annotations.Mapper;

/** Inbox 事件数据访问（InboxEventEntity）。MyBatis-Plus 参数绑定，禁止拼接动态表名/列名（开发规范第 7 节）。 */
@Mapper
public interface InboxEventMapper extends BaseMapper<InboxEventEntity> {
}
