package com.educloud.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.file.entity.FileBindingEntity;
import org.apache.ibatis.annotations.Mapper;

/** 业务绑定数据访问（FileBindingEntity）。 */
@Mapper
public interface FileBindingMapper extends BaseMapper<FileBindingEntity> {
}
