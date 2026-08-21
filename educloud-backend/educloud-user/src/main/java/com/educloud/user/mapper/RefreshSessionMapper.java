package com.educloud.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.user.entity.RefreshSessionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;

/** Refresh 会话数据访问（refresh_session）。轮换事务使用行锁（SELECT ... FOR UPDATE）。 */
@Mapper
public interface RefreshSessionMapper extends BaseMapper<RefreshSessionEntity> {

    @Select("SELECT * FROM refresh_session WHERE session_token_hash = #{tokenHash} LIMIT 1")
    RefreshSessionEntity selectByTokenHash(@Param("tokenHash") String tokenHash);

    /** 轮换事务行锁读取：同一 token 的并发刷新只有首个能取得锁（安全设计第 3.2 节）。 */
    @Select("SELECT * FROM refresh_session WHERE session_token_hash = #{tokenHash} FOR UPDATE")
    RefreshSessionEntity selectByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Select("SELECT * FROM refresh_session WHERE family_id = #{familyId}")
    List<RefreshSessionEntity> selectByFamilyId(@Param("familyId") String familyId);

    @Select("SELECT DISTINCT family_id FROM refresh_session WHERE user_id = #{userId} AND status = 'ACTIVE'")
    List<String> selectActiveFamilyIdsByUserId(@Param("userId") Long userId);

    /** 原子迁移 ACTIVE → ROTATED；返回 0 表示父行已被并发消费。 */
    @Update("UPDATE refresh_session SET status = 'ROTATED', consumed_at = #{consumedAt} "
            + "WHERE id = #{id} AND status = 'ACTIVE'")
    int markRotated(@Param("id") Long id, @Param("consumedAt") Instant consumedAt);

    @Update("UPDATE refresh_session SET status = 'REVOKED', revoked_at = #{revokedAt}, "
            + "revoke_reason = #{reason} WHERE family_id = #{familyId} AND status <> 'REVOKED'")
    int revokeFamily(@Param("familyId") String familyId, @Param("revokedAt") Instant revokedAt,
            @Param("reason") String reason);
}
