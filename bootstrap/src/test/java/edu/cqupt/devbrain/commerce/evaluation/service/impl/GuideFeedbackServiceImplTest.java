package edu.cqupt.devbrain.commerce.evaluation.service.impl;

import edu.cqupt.devbrain.commerce.evaluation.dao.entity.GuideFeedbackDO;
import edu.cqupt.devbrain.commerce.evaluation.dao.mapper.GuideFeedbackMapper;
import edu.cqupt.devbrain.commerce.evaluation.dto.req.GuideFeedbackCreateReq;
import edu.cqupt.devbrain.commerce.evaluation.dto.req.GuideFeedbackReviewReq;
import edu.cqupt.devbrain.commerce.evaluation.dto.resp.GuideFeedbackResp;
import edu.cqupt.devbrain.framework.context.LoginUser;
import edu.cqupt.devbrain.framework.context.UserContext;
import edu.cqupt.devbrain.framework.exception.ClientException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GuideFeedbackServiceImplTest {

    private final GuideFeedbackMapper feedbackMapper = mock(GuideFeedbackMapper.class);
    private final GuideFeedbackServiceImpl service = new GuideFeedbackServiceImpl(feedbackMapper);

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void createStoresFeedbackTargetAndAgentRunBinding() {
        UserContext.set(new LoginUser("user-1", "admin", null, null, null, Set.of("admin"), Set.of()));
        GuideFeedbackCreateReq request = new GuideFeedbackCreateReq(
                "conversation-1",
                "message-1",
                "product-1",
                "weak_evidence",
                "证据太弱",
                "evidence",
                "evidence-1",
                "run-1",
                "step-1",
                "doc-1#chunk-1",
                0
        );

        GuideFeedbackResp response = service.create(request);

        ArgumentCaptor<GuideFeedbackDO> captor = ArgumentCaptor.forClass(GuideFeedbackDO.class);
        verify(feedbackMapper).insert(captor.capture());
        GuideFeedbackDO saved = captor.getValue();
        assertEquals("evidence", saved.getTargetType());
        assertEquals("evidence-1", saved.getTargetId());
        assertEquals("run-1", saved.getAgentRunId());
        assertEquals("step-1", saved.getStepId());
        assertEquals("doc-1#chunk-1", saved.getEvidenceId());
        assertEquals(0, saved.getReasonIndex());
        assertEquals("evidence", response.targetType());
    }

    @Test
    void createRejectsIllegalTargetType() {
        UserContext.set(new LoginUser("user-1", "admin", null, null, null, Set.of("admin"), Set.of()));

        ClientException exception = assertThrows(ClientException.class, () -> service.create(new GuideFeedbackCreateReq(
                "conversation-1",
                null,
                null,
                "wrong_product",
                null,
                "unknown_target",
                "x",
                null,
                null,
                null,
                null
        )));

        assertTrue(exception.getMessage().contains("反馈目标类型不合法"));
    }

    @Test
    void resolvedFeedbackGeneratesActionableImprovementSuggestion() {
        UserContext.set(new LoginUser("reviewer-1", "admin", null, null, null, Set.of("admin"), Set.of()));
        GuideFeedbackDO feedback = new GuideFeedbackDO();
        feedback.setId("feedback-1");
        feedback.setConversationId("conversation-1");
        feedback.setFeedbackType("bad_ranking");
        feedback.setReviewStatus("pending");
        when(feedbackMapper.selectById("feedback-1")).thenReturn(feedback);
        when(feedbackMapper.updateById(any(GuideFeedbackDO.class))).thenReturn(1);

        GuideFeedbackResp response = service.review("feedback-1", new GuideFeedbackReviewReq("resolved", "确认排序不佳"));

        ArgumentCaptor<GuideFeedbackDO> captor = ArgumentCaptor.forClass(GuideFeedbackDO.class);
        verify(feedbackMapper).updateById(captor.capture());
        assertTrue(captor.getValue().getImprovementSuggestion().contains("调整排序"));
        assertEquals(captor.getValue().getImprovementSuggestion(), response.improvementSuggestion());
    }
}
