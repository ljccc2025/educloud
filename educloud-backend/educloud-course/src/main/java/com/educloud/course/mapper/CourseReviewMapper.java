package com.educloud.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.course.entity.CourseReviewEntity;
import org.apache.ibatis.annotations.Mapper;

/** 评价数据访问（CourseReviewEntity）。 */
@Mapper
public interface CourseReviewMapper extends BaseMapper<CourseReviewEntity> {
}
