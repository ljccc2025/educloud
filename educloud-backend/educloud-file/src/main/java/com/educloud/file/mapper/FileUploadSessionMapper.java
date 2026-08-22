package com.educloud.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.file.entity.FileUploadSessionEntity;
import org.apache.ibatis.annotations.Mapper;

/** 上传会话数据访问（FileUploadSessionEntity）。 */
@Mapper
public interface FileUploadSessionMapper extends BaseMapper<FileUploadSessionEntity> {
}
