package com.educloud.analytics.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.analytics.entity.AuditEventReadModelEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AuditEventReadModelMapper extends BaseMapper<AuditEventReadModelEntity> {

    List<AuditEventReadModelEntity> searchAuditLogs(
            @Param("keyword") String keyword,
            @Param("level") String level,
            @Param("sourceService") String sourceService,
            @Param("actorId") String actorId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    long countAuditLogs(
            @Param("keyword") String keyword,
            @Param("level") String level,
            @Param("sourceService") String sourceService,
            @Param("actorId") String actorId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );
}
