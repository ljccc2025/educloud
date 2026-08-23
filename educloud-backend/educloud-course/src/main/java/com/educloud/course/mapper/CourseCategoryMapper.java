package com.educloud.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.course.entity.CourseCategoryEntity;
import org.apache.ibatis.annotations.Mapper;

/** 课程分类数据访问（CourseCategoryEntity）。 */
@Mapper
public interface CourseCategoryMapper extends BaseMapper<CourseCategoryEntity> {
}
