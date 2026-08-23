package com.educloud.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.course.entity.CourseAuditSubmissionEntity;
import org.apache.ibatis.annotations.Mapper;

/** 审核提交数据访问（CourseAuditSubmissionEntity）。 */
@Mapper
public interface CourseAuditSubmissionMapper extends BaseMapper<CourseAuditSubmissionEntity> {
}
