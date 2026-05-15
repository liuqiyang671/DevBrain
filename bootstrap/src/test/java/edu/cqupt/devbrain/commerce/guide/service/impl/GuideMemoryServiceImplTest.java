package edu.cqupt.devbrain.commerce.guide.service.impl;

import edu.cqupt.devbrain.commerce.guide.dao.entity.AgentMemoryDO;
import edu.cqupt.devbrain.commerce.guide.dao.mapper.AgentMemoryMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GuideMemoryServiceImplTest {

    private final AgentMemoryMapper mapper = mock(AgentMemoryMapper.class);
    private final GuideMemoryServiceImpl service = new GuideMemoryServiceImpl(mapper);

    @Test
    void upsertInsertsNewExplicitPreference() {
        when(mapper.selectOne(any())).thenReturn(null);

        service.upsert("u1", "preferred_brand", "phone", "星河", new BigDecimal("0.9000"), "explicit");

        ArgumentCaptor<AgentMemoryDO> captor = ArgumentCaptor.forClass(AgentMemoryDO.class);
        verify(mapper).insert(captor.capture());
        AgentMemoryDO memory = captor.getValue();
        assertEquals("u1", memory.getUserId());
        assertEquals("preferred_brand", memory.getMemoryType());
        assertEquals("phone", memory.getMemoryKey());
        assertEquals("星河", memory.getMemoryValue());
        assertEquals(new BigDecimal("0.9000"), memory.getConfidence());
    }

    @Test
    void upsertUpdatesExistingPreference() {
        AgentMemoryDO existing = new AgentMemoryDO();
        existing.setId("m1");
        existing.setUserId("u1");
        existing.setMemoryType("scenario");
        existing.setMemoryKey("laptop");
        existing.setMemoryValue("办公");
        when(mapper.selectOne(any())).thenReturn(existing);

        service.upsert("u1", "scenario", "laptop", "写代码", new BigDecimal("0.8000"), "explicit");

        ArgumentCaptor<AgentMemoryDO> captor = ArgumentCaptor.forClass(AgentMemoryDO.class);
        verify(mapper).updateById(captor.capture());
        AgentMemoryDO updated = captor.getValue();
        assertEquals("m1", updated.getId());
        assertEquals("写代码", updated.getMemoryValue());
        assertEquals(new BigDecimal("0.8000"), updated.getConfidence());
    }

    @Test
    void listCurrentUserMemoryFiltersByUserAndDeletedFlag() {
        service.listByUser("u1");

        verify(mapper).selectList(argThat(wrapper -> wrapper != null));
    }

    @Test
    void deleteMarksOnlyCurrentUsersMemoryDeleted() {
        service.delete("u1", "m1");

        verify(mapper).update(argThat(memory -> Integer.valueOf(1).equals(memory.getDeleted())),
                argThat(wrapper -> wrapper != null));
    }

    @Test
    void extractExplicitMemoriesFromStateOnlyPersistsClearPreferences() {
        var state = edu.cqupt.devbrain.commerce.guide.domain.GuideState.builder()
                .userId("u1")
                .slots(edu.cqupt.devbrain.commerce.guide.domain.GuideSlotState.builder()
                        .category("audio")
                        .scenario("通勤")
                        .brandPreference("声阔")
                        .budgetMax(new BigDecimal("1000"))
                        .attributes(java.util.Map.of("avoidBrand", "苹果"))
                        .build())
                .build();

        List<AgentMemoryDO> memories = service.extractMemories(state);

        assertEquals(4, memories.size());
        assertEquals(List.of("preferred_brand", "avoid_brand", "budget_range", "scenario"),
                memories.stream().map(AgentMemoryDO::getMemoryType).toList());
    }
}
