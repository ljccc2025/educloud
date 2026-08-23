package com.educloud.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.course.entity.CourseEntity;
import org.apache.ibatis.annotations.Mapper;

/** 课程聚合根数据访问（CourseEntity）。 */
@Mapper
public interface CourseMapper extends BaseMapper<CourseEntity> {
}
