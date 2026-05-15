package edu.cqupt.devbrain.commerce.evaluation.service.impl;

import edu.cqupt.devbrain.commerce.evaluation.dao.entity.EvaluationDatasetDO;
import edu.cqupt.devbrain.commerce.evaluation.dao.mapper.EvaluationDatasetMapper;
import edu.cqupt.devbrain.commerce.evaluation.dto.req.EvaluationDatasetReq;
import edu.cqupt.devbrain.framework.context.LoginUser;
import edu.cqupt.devbrain.framework.context.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class EvaluationDatasetServiceImplTest {

    private final EvaluationDatasetMapper datasetMapper = mock(EvaluationDatasetMapper.class);
    private final EvaluationDatasetServiceImpl service = new EvaluationDatasetServiceImpl(datasetMapper);

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void createDatasetFillsStatusAndAuditUser() {
        UserContext.set(new LoginUser("user-1", "admin", null, null, null, Set.of("admin"), Set.of()));

        service.create(new EvaluationDatasetReq("导购回归集", "核心场景", null));

        ArgumentCaptor<EvaluationDatasetDO> captor = ArgumentCaptor.forClass(EvaluationDatasetDO.class);
        verify(datasetMapper).insert(captor.capture());
        EvaluationDatasetDO saved = captor.getValue();
        assertNotNull(saved.getId());
        assertEquals("导购回归集", saved.getName());
        assertEquals("enabled", saved.getStatus());
        assertEquals("user-1", saved.getCreatedBy());
    }
}
