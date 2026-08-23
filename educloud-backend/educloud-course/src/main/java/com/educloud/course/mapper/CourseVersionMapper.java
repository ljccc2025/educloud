package com.educloud.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.course.entity.CourseVersionEntity;
import org.apache.ibatis.annotations.Mapper;

/** 课程不可变版本数据访问（CourseVersionEntity）。 */
@Mapper
public interface CourseVersionMapper extends BaseMapper<CourseVersionEntity> {
}
