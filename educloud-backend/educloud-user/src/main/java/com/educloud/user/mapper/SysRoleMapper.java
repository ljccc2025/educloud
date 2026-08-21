package com.educloud.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.user.entity.SysRoleEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 角色数据访问（SysRoleEntity）。 */
@Mapper
public interface SysRoleMapper extends BaseMapper<SysRoleEntity> {

    /** 用户激活角色 code 列表（RBAC 摘要进 JWT，设计规格第 6 节）。 */
    @Select("SELECT r.code FROM sys_role r JOIN sys_user_role ur ON ur.role_id = r.id "
            + "WHERE ur.user_id = #{userId} AND r.status = 'ACTIVE'")
    List<String> selectCodesByUserId(@Param("userId") Long userId);
}
