package edu.cqupt.devbrain;

import edu.cqupt.devbrain.commerce.guide.service.GuideWorkflowEngine;
import edu.cqupt.devbrain.commerce.guide.service.impl.AutonomousGuideAgentEngine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AiShoppingAgentApplicationTests {

    @Autowired
    private GuideWorkflowEngine guideWorkflowEngine;

    @Test
    void contextLoads() {
        assertThat(guideWorkflowEngine).isInstanceOf(AutonomousGuideAgentEngine.class);
    }
}
