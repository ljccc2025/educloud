package com.educloud.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.file.entity.FileObjectEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

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
}
