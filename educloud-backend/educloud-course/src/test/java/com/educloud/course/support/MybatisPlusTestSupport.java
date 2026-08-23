package com.educloud.course.support;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;

/**
 * 纯 Mockito 单测的 MyBatis-Plus TableInfo 注册支持（质量审查收敛为共享类）。
 *
 * <p>纯 Mockito 单测没有 MyBatis 运行期 Mapper 注册，LambdaWrapper 渲染列名依赖
 * TableInfo 缓存；真实运行期由 Mapper 注册提供。测试 @BeforeAll 调用
 * {@link #registerTableInfo(Class...)} 显式注册实体（与真实运行期行为一致）。</p>
 */
public final class MybatisPlusTestSupport {

    private MybatisPlusTestSupport() {
    }

    public static void registerTableInfo(Class<?>... entityTypes) {
        MybatisConfiguration configuration = new MybatisConfiguration();
        for (Class<?> entityType : entityTypes) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), entityType);
        }
    }
}
