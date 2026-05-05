package edu.cqupt.devbrain.knowledge.dao.typehandler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * PostgreSQL JSONB 字段与 Java String 的轻量映射。
 * <p>
 * 将 PostgreSQL 的 JSONB 类型以 {@code Types.OTHER} 方式写入，
 * 读取时通过 {@code toString()} 还原为 JSON 字符串。
 * 配合 MyBatis {@code @TableField(typeHandler = ...)} 注解使用。
 */
@MappedTypes(String.class)
@MappedJdbcTypes(JdbcType.OTHER)
public class JsonbStringTypeHandler extends BaseTypeHandler<String> {

    /**
     * 设置非空参数，以 OTHER 类型写入 PostgreSQL JSONB 字段。
     */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setObject(i, parameter, Types.OTHER);
    }

    /**
     * 从 ResultSet 按列名读取 JSONB 值并转为字符串。
     */
    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        Object value = rs.getObject(columnName);
        return value == null ? null : value.toString();
    }

    /**
     * 从 ResultSet 按列索引读取 JSONB 值并转为字符串。
     */
    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        Object value = rs.getObject(columnIndex);
        return value == null ? null : value.toString();
    }

    /**
     * 从 CallableStatement 按列索引读取 JSONB 值并转为字符串。
     */
    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        Object value = cs.getObject(columnIndex);
        return value == null ? null : value.toString();
    }
}
