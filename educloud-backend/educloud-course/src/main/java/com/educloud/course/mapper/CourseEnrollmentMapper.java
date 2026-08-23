package com.educloud.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.course.entity.CourseEnrollmentEntity;
import org.apache.ibatis.annotations.Mapper;

/** 选课数据访问（CourseEnrollmentEntity）。 */
@Mapper
public interface CourseEnrollmentMapper extends BaseMapper<CourseEnrollmentEntity> {
}
