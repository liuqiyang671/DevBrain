package edu.cqupt.devbrain.rag.core.rewrite.dao;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.cqupt.devbrain.rag.core.rewrite.dao.entity.QueryTermMappingDO;
import edu.cqupt.devbrain.rag.core.rewrite.dao.mapper.QueryTermMappingMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class QueryTermMappingPersistenceMetadataTest {

    @Test
    void mapsEntityToQueryTermMappingTable() {
        TableName tableName = QueryTermMappingDO.class.getAnnotation(TableName.class);

        assertNotNull(tableName);
        assertEquals("t_query_term_mapping", tableName.value());
    }

    @Test
    void marksDeletedAsLogicalDeleteField() throws NoSuchFieldException {
        Field deletedField = QueryTermMappingDO.class.getDeclaredField("deleted");

        assertNotNull(deletedField.getAnnotation(TableLogic.class));
    }

    @Test
    void mapperExtendsBaseMapperForQueryTermMappingEntity() {
        ParameterizedType mapperType = (ParameterizedType) QueryTermMappingMapper.class.getGenericInterfaces()[0];

        assertEquals(BaseMapper.class, mapperType.getRawType());
        assertEquals(QueryTermMappingDO.class, mapperType.getActualTypeArguments()[0]);
    }
}
