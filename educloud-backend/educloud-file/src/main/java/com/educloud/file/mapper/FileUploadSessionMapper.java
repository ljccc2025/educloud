package com.educloud.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.file.entity.FileUploadSessionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/** 上传会话数据访问（FileUploadSessionEntity）。 */
@Mapper
public interface FileUploadSessionMapper extends BaseMapper<FileUploadSessionEntity> {

    /**
     * 行级锁读取会话：complete 状态机内锁定会话行，防止并发 complete/过期竞态。
     *
     * <p>依据：M04 设计规格第 7.1 节与计划任务 4 —— 必须在事务内调用方生效。</p>
     */
    @Select("SELECT * FROM file_upload_session WHERE id=#{id} FOR UPDATE")
    FileUploadSessionEntity selectByIdForUpdate(Long id);
}

