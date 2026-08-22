package com.educloud.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.file.entity.FileUploadSessionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;
import java.util.List;

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

    /**
     * 过期清理候选：PENDING 且整体到期时间早于 now 的会话，按到期时间升序限量返回。
     *
     * <p>任务 12 过期会话清理的批次入口；服务层在事务内幂等置 EXPIRED，并顺带清理
     * 无对应 AVAILABLE file_object 的 MinIO 孤儿对象。</p>
     */
    @Select("SELECT * FROM file_upload_session WHERE status='PENDING' AND expires_at < #{now}"
            + " ORDER BY expires_at ASC LIMIT #{limit}")
    List<FileUploadSessionEntity> selectExpired(Instant now, int limit);
}

