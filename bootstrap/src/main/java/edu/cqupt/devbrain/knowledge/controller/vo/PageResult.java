package edu.cqupt.devbrain.knowledge.controller.vo;

import java.util.List;

/**
 * 简单分页结果模型，用于非 MyBatis-Plus IPage 场景的列表分页响应。
 *
 * @param records 当前页记录
 * @param total 总记录数
 * @param page 当前页码，从 1 开始
 * @param size 每页记录数
 * @param pages 总页数
 * @param <T> 记录类型
 */
public record PageResult<T>(
        List<T> records,
        long total,
        int page,
        int size,
        long pages
) {

    /**
     * 根据当前页数据和分页参数创建分页结果。
     *
     * @param records 当前页记录
     * @param total 总记录数
     * @param page 当前页码
     * @param size 每页记录数
     * @param <T> 记录类型
     * @return 分页结果
     */
    public static <T> PageResult<T> of(List<T> records, long total, int page, int size) {
        long pages = size <= 0 ? 0 : (total + size - 1) / size;
        return new PageResult<>(List.copyOf(records), total, page, size, pages);
    }
}
