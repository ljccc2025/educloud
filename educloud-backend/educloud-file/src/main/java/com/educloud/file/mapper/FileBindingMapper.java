package com.educloud.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.file.entity.FileBindingEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 业务绑定数据访问（FileBindingEntity）。 */
@Mapper
public interface FileBindingMapper extends BaseMapper<FileBindingEntity> {

    /** 文件当前未解绑的全部活跃绑定（unbound_at IS NULL）。 */
    @Select("SELECT * FROM file_binding WHERE file_id=#{fileId} AND unbound_at IS NULL")
    List<FileBindingEntity> findActiveByFileId(Long fileId);

    /** 按属主唯一键（uk_file_binding）查询当前活跃绑定；无则返回 null。 */
    @Select("SELECT * FROM file_binding WHERE file_id=#{fileId} AND owner_service=#{ownerService}"
            + " AND owner_type=#{ownerType} AND owner_id=#{ownerId} AND unbound_at IS NULL")
    FileBindingEntity findActiveByOwner(Long fileId, String ownerService, String ownerType, String ownerId);

    /** 文件当前活跃绑定数（unbound_at IS NULL），供删除前判活。 */
    @Select("SELECT COUNT(*) FROM file_binding WHERE file_id=#{fileId} AND unbound_at IS NULL")
    long countActiveByFileId(Long fileId);

    /** 按属主唯一键查询任一条绑定记录（含历史解绑），供删除前校验“曾绑定”归属。 */
    @Select("SELECT * FROM file_binding WHERE file_id=#{fileId} AND owner_service=#{ownerService}"
            + " AND owner_type=#{ownerType} AND owner_id=#{ownerId} ORDER BY id DESC LIMIT 1")
    FileBindingEntity findByOwner(Long fileId, String ownerService, String ownerType, String ownerId);
}
