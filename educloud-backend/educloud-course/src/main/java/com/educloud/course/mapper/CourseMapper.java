package com.educloud.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.course.entity.CourseEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 课程聚合根数据访问（CourseEntity）。 */
@Mapper
public interface CourseMapper extends BaseMapper<CourseEntity> {

    /**
     * 锁定课程根行（SELECT ... FOR UPDATE）：审批/驳回/撤回等根状态切换前加行锁，
     * 与乐观锁（@Version）配合保证并发提交/审批原子性（规格 §7 版本乐观锁）。
     */
    @Select("SELECT * FROM course WHERE id = #{id} FOR UPDATE")
    CourseEntity selectByIdForUpdate(@Param("id") Long id);
}
