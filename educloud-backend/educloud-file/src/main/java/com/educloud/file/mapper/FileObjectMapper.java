package com.educloud.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.file.entity.FileObjectEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;
import java.util.List;

/** 文件对象（聚合根）数据访问（FileObjectEntity）。 */
@Mapper
public interface FileObjectMapper extends BaseMapper<FileObjectEntity> {

    /**
     * 行级锁读取文件根：绑定/解绑/删除事务内锁定 file_object 行，配合
     * {@code @Version} 乐观锁拦截器在 update 时生成 WHERE version=旧 的并发保护。
     *
     * <p>依据：M04 设计规格第 5 节 —— 必须在事务内调用方生效。</p>
     */
    @Select("SELECT * FROM file_object WHERE id=#{id} FOR UPDATE")
    FileObjectEntity selectByIdForUpdate(Long id);

    /**
     * 清理候选：AVAILABLE 且上传时间早于保留期截止时间的文件，按上传时间升序限量返回。
     *
     * <p>任务 12 未绑定文件清理的批次入口；真实删除前由服务层在事务内二次确认
     * 活跃绑定并走乐观锁（updateById 0 行即跳过）。</p>
     */
    @Select("SELECT * FROM file_object WHERE status='AVAILABLE' AND uploaded_at < #{retentionTime}"
            + " ORDER BY uploaded_at ASC LIMIT #{limit}")
    List<FileObjectEntity> selectUnboundCandidates(Instant retentionTime, int limit);
}
