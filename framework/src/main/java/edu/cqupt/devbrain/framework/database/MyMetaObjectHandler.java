package edu.cqupt.devbrain.framework.database;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;

import java.util.Date;

/**
 * MyBatis-Plus 元数据自动填充处理器
 * <p>
 * 实现 {@link MetaObjectHandler} 接口，在数据库插入和更新操作时自动填充公共字段：
 * <ul>
 *   <li>插入时：自动填充 createTime（创建时间）、updateTime（更新时间）、deleted（逻辑删除标记，默认0）</li>
 *   <li>更新时：自动填充 updateTime（更新时间）</li>
 * </ul>
 * 使用 strictInsertFill/strictUpdateFill 方法确保仅在字段值为 null 时才进行填充，
 * 避免覆盖用户显式设置的值。
 * </p>
 */
public class MyMetaObjectHandler implements MetaObjectHandler {

    /**
     * 插入操作自动填充
     * <p>
     * 在实体对象插入数据库时，自动填充以下字段：
     * <ul>
     *   <li>createTime - 创建时间，取当前系统时间</li>
     *   <li>updateTime - 更新时间，取当前系统时间</li>
     *   <li>deleted - 逻辑删除标记，默认值为 0（未删除）</li>
     * </ul>
     * </p>
     *
     * @param metaObject 元数据对象，包含实体类的字段信息
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        strictInsertFill(metaObject, "createTime", Date::new, Date.class);
        strictInsertFill(metaObject, "updateTime", Date::new, Date.class);
        strictInsertFill(metaObject, "deleted", () -> 0, Integer.class);
    }

    /**
     * 更新操作自动填充
     * <p>
     * 在实体对象更新数据库时，自动填充 updateTime（更新时间）为当前系统时间。
     * </p>
     *
     * @param metaObject 元数据对象，包含实体类的字段信息
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updateTime", Date::new, Date.class);
    }
}
