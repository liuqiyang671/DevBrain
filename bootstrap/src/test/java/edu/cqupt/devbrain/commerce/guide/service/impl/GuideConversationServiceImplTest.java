package edu.cqupt.devbrain.commerce.guide.service.impl;

import edu.cqupt.devbrain.commerce.guide.dao.entity.GuideMessageDO;
import edu.cqupt.devbrain.commerce.guide.dao.entity.GuideSessionDO;
import edu.cqupt.devbrain.commerce.guide.dao.mapper.GuideMessageMapper;
import edu.cqupt.devbrain.commerce.guide.dao.mapper.GuideRecommendationMapper;
import edu.cqupt.devbrain.commerce.guide.dao.mapper.GuideSessionMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GuideConversationServiceImplTest {

    private final GuideSessionMapper sessionMapper = mock(GuideSessionMapper.class);
    private final GuideMessageMapper messageMapper = mock(GuideMessageMapper.class);
    private final GuideRecommendationMapper recommendationMapper = mock(GuideRecommendationMapper.class);
    private final GuideConversationServiceImpl service = new GuideConversationServiceImpl(
            sessionMapper,
            messageMapper,
            recommendationMapper
    );

    @Test
    void appendUserMessageCreatesSessionBeforeMessageForNewConversation() {
        when(sessionMapper.selectCount(any())).thenReturn(0L);
        when(messageMapper.selectCount(any())).thenReturn(0L);

        service.appendUserMessage("s1", "c1", "u1", "我想买手机", List.of("img1"), "client-1", "run1");

        ArgumentCaptor<GuideSessionDO> sessionCaptor = ArgumentCaptor.forClass(GuideSessionDO.class);
        ArgumentCaptor<GuideMessageDO> messageCaptor = ArgumentCaptor.forClass(GuideMessageDO.class);
        verify(sessionMapper).insert(sessionCaptor.capture());
        verify(messageMapper).insert(messageCaptor.capture());
        assertEquals("c1", sessionCaptor.getValue().getConversationId());
        assertEquals("s1", sessionCaptor.getValue().getId());
        assertEquals("user", messageCaptor.getValue().getRole());
        assertEquals("[\"img1\"]", messageCaptor.getValue().getImageRefsJson());
    }

    @Test
    void archiveSessionPersistsArchivedMetadata() {
        GuideSessionDO session = new GuideSessionDO();
        session.setId("s1");
        session.setConversationId("c1");
        session.setUserId("u1");
        when(sessionMapper.selectOne(any())).thenReturn(session);
        when(messageMapper.selectList(any())).thenReturn(List.of(
                message("user", "我想买一件衣服"),
                message("assistant", "推荐优先看尺码、材质和退换政策。")
        ));

        service.archiveSession("s1", "u1");

        ArgumentCaptor<GuideSessionDO> updateCaptor = ArgumentCaptor.forClass(GuideSessionDO.class);
        verify(sessionMapper).update(updateCaptor.capture(), any());
        GuideSessionDO update = updateCaptor.getValue();
        assertEquals(1, update.getArchived());
        assertEquals("我想买一件衣服；推荐优先看尺码、材质和退换政策。", update.getArchiveSummary());
    }

    @Test
    void detailIncludesArchivedMetadata() {
        Date archivedTime = new Date();
        GuideSessionDO session = new GuideSessionDO();
        session.setId("s1");
        session.setConversationId("c1");
        session.setUserId("u1");
        session.setArchived(1);
        session.setArchivedTime(archivedTime);
        session.setArchiveSummary("衣服导购归档摘要");
        when(sessionMapper.selectOne(any())).thenReturn(session);
        when(messageMapper.selectList(any())).thenReturn(List.of());
        when(recommendationMapper.selectList(any())).thenReturn(List.of());
        when(messageMapper.selectCount(any())).thenReturn(0L);

        var response = service.detail("s1", "u1");

        assertEquals(true, response.archived());
        assertEquals(archivedTime, response.archivedTime());
        assertEquals("衣服导购归档摘要", response.summary());
    }

    @Test
    void deleteSessionUsesMyBatisPlusLogicalDelete() {
        GuideSessionDO session = new GuideSessionDO();
        session.setId("s1");
        session.setConversationId("c1");
        session.setUserId("u1");
        when(sessionMapper.selectOne(any())).thenReturn(session);

        service.deleteSession("s1", "u1");

        verify(sessionMapper).deleteById(session);
    }

    private GuideMessageDO message(String role, String content) {
        GuideMessageDO message = new GuideMessageDO();
        message.setRole(role);
        message.setContent(content);
        return message;
    }
}
