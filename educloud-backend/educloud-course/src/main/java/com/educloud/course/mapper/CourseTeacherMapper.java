package com.educloud.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.course.entity.CourseTeacherEntity;
import org.apache.ibatis.annotations.Mapper;

/** 授课教师数据访问（CourseTeacherEntity）。 */
@Mapper
public interface CourseTeacherMapper extends BaseMapper<CourseTeacherEntity> {
}
