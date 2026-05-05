package edu.cqupt.devbrain.rag.core.vector;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 向量空间标识测试，锁定逻辑名和命名空间的字符串表示。
 */
class VectorSpaceIdTest {

    @Test
    void shouldUseLogicalNameWhenNamespaceIsBlank() {
        VectorSpaceId id = new VectorSpaceId("kb_employee_policy", null);

        assertEquals("kb_employee_policy", id.toString());
    }

    @Test
    void shouldPrefixLogicalNameWithNamespace() {
        VectorSpaceId id = new VectorSpaceId("kb_employee_policy", "prod");

        assertEquals("prod.kb_employee_policy", id.toString());
    }
}
