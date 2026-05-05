package edu.cqupt.devbrain.rag.core.vector;

/**
 * 向量空间标识。
 *
 * @param logicalName 逻辑名称，例如 kb_employee_policy
 * @param namespace   可选命名空间，用于多环境或多租户前缀隔离
 */
public record VectorSpaceId(String logicalName, String namespace) {

    /**
     * 返回后端可识别的完整空间名。
     * namespace 为空时只返回逻辑名，避免 PgVector 这类统一表实现产生无意义前缀。
     */
    @Override
    public String toString() {
        if (namespace == null || namespace.isBlank()) {
            return logicalName;
        }
        return namespace + "." + logicalName;
    }
}
