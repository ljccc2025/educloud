package com.educloud.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.user.entity.SysUserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/** 账号数据访问（SysUserEntity）。MyBatis-Plus 参数绑定，禁止拼接动态表名/列名（开发规范第 7 节）。 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUserEntity> {

    /**
     * 按登录名查找：username/email/phone 任一匹配。
     * 依据：M03 设计规格第 5 节（登录名解析；统一失败语义由 AuthenticationService 保证，不在此区分）。
     * 注意：utf8mb4_0900_ai_ci 排序规则为大小写不敏感匹配。
     */
    @Select("SELECT * FROM sys_user WHERE username = #{loginName} OR email = #{loginName} OR phone = #{loginName} LIMIT 1")
    SysUserEntity selectByLoginName(String loginName);

    @Select("SELECT * FROM sys_user WHERE email = #{email} LIMIT 1")
    SysUserEntity selectByEmail(String email);

    @Select("SELECT * FROM sys_user WHERE phone = #{phone} LIMIT 1")
    SysUserEntity selectByPhone(String phone);
}
