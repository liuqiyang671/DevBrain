package edu.cqupt.devbrain.framework.distributedid;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.springframework.stereotype.Component;

/**
 * 自定义分布式 ID 生成器
 * <p>
 * 实现 MyBatis-Plus 的 {@link IdentifierGenerator} 接口，基于 Hutool 的 Snowflake（雪花）算法生成全局唯一 ID，
 * 替换 MyBatis-Plus 默认的 ID 生成策略。
 * </p>
 * <p>
 * 雪花算法生成的 ID 具有以下特性：
 * <ul>
 *   <li>全局唯一 - 在分布式环境下保证 ID 不重复</li>
 *   <li>趋势递增 - ID 整体呈递增趋势，有利于数据库索引性能</li>
 *   <li>包含时间信息 - 可以从 ID 中解析出生成时间</li>
 * </ul>
 * </p>
 */
@Component
public class CustomIdentifierGenerator implements IdentifierGenerator {

    /**
     * 生成下一个分布式唯一 ID（数值类型）
     * <p>
     * 使用 Hutool 的雪花算法生成 Long 类型的全局唯一 ID。
     * MyBatis-Plus 在插入实体对象时会调用此方法获取主键 ID。
     * </p>
     *
     * @param entity 实体对象
     * @return 全局唯一的 Long 类型 ID
     */
    @Override
    public Number nextId(Object entity) {
        return IdUtil.getSnowflakeNextId();
    }

    /**
     * 生成下一个分布式唯一 ID（字符串类型）
     * <p>
     * 使用 Hutool 的雪花算法生成字符串形式的全局唯一 ID。
     * 适用于需要字符串类型主键的场景。
     * </p>
     *
     * @param entity 实体对象
     * @return 全局唯一的字符串类型 ID
     */
    @Override
    public String nextUUID(Object entity) {
        return IdUtil.getSnowflakeNextIdStr();
    }
}
