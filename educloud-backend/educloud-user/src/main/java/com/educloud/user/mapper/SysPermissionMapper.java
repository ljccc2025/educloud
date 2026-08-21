package com.educloud.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.user.entity.SysPermissionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 权限目录数据访问（SysPermissionEntity）。 */
@Mapper
public interface SysPermissionMapper extends BaseMapper<SysPermissionEntity> {

    /** 用户全量权限码（去重）；调用方负责 64 上限校验（Gateway 硬上限，设计规格第 6 节）。 */
    @Select("SELECT DISTINCT p.code FROM sys_permission p "
            + "JOIN sys_role_permission rp ON rp.permission_id = p.id "
            + "JOIN sys_user_role ur ON ur.role_id = rp.role_id "
            + "WHERE ur.user_id = #{userId}")
    List<String> selectCodesByUserId(@Param("userId") Long userId);
}
