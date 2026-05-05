package edu.cqupt.devbrain.framework.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import edu.cqupt.devbrain.framework.database.MyMetaObjectHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 数据库配置类
 * <p>
 * 负责注册 MyBatis-Plus 的核心插件和处理器，包括：
 * <ul>
 *   <li>分页拦截器 - 基于 PostgreSQL 数据库方言实现物理分页</li>
 *   <li>元数据自动填充处理器 - 在插入和更新操作时自动填充公共字段</li>
 * </ul>
 * </p>
 */
@Configuration
public class DataBaseConfiguration {

    /**
     * 注册 MyBatis-Plus 分页拦截器
     * <p>
     * 使用 PostgreSQL 数据库方言，自动将逻辑分页转换为物理分页 SQL。
     * 通过 LIMIT/OFFSET 语法实现高效分页查询。
     * </p>
     *
     * @return MybatisPlusInterceptor 分页拦截器实例
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
        return interceptor;
    }
    /**
     * 注册 MyBatis-Plus 元数据自动填充处理器
     * <p>
     * 在实体对象插入和更新时，自动填充 createTime、updateTime、deleted 等公共字段，
     * 避免手动设置这些重复性字段。
     * </p>
     *
     * @return MetaObjectHandler 自动填充处理器实例
     */
    @Bean
    public MetaObjectHandler myMetaObjectHandler() {
        return new MyMetaObjectHandler();
    }
}
