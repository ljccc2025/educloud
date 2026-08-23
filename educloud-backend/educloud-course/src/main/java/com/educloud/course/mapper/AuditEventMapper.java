package com.educloud.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.course.entity.AuditEventEntity;
import org.apache.ibatis.annotations.Mapper;

/** 审计事件数据访问（AuditEventEntity）。MyBatis-Plus 参数绑定，禁止拼接动态表名/列名。 */
@Mapper
public interface AuditEventMapper extends BaseMapper<AuditEventEntity> {
}
