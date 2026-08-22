package com.educloud.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.file.entity.FileObjectEntity;
import org.apache.ibatis.annotations.Mapper;

/** 文件对象（聚合根）数据访问（FileObjectEntity）。 */
@Mapper
public interface FileObjectMapper extends BaseMapper<FileObjectEntity> {
}
