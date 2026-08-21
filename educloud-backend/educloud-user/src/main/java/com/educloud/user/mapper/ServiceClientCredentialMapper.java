package com.educloud.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.user.entity.ServiceClientCredentialEntity;
import org.apache.ibatis.annotations.Mapper;

/** 服务客户端凭据数据访问（ServiceClientCredentialEntity）。MyBatis-Plus 参数绑定，禁止拼接动态表名/列名（开发规范第 7 节）。 */
@Mapper
public interface ServiceClientCredentialMapper extends BaseMapper<ServiceClientCredentialEntity> {
}
