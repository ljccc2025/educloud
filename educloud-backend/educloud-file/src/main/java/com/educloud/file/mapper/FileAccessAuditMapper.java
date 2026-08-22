package com.educloud.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.file.entity.FileAccessAuditEntity;
import org.apache.ibatis.annotations.Mapper;

/** 访问审计数据访问（FileAccessAuditEntity）。 */
@Mapper
public interface FileAccessAuditMapper extends BaseMapper<FileAccessAuditEntity> {
}
