package edu.cqupt.devbrain.commerce.guide.agent.policy;

import edu.cqupt.devbrain.commerce.guide.agent.GuideAgentProperties;
import edu.cqupt.devbrain.commerce.guide.agent.fallback.GuideFallbackProperties;
import edu.cqupt.devbrain.commerce.guide.clarification.GuideClarificationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GuideAgentPolicyApplicationYamlTest {

    @Test
    void applicationYamlDefinesVersionedGuideAgentPolicies() throws IOException {
        GuideAgentProperties properties = Binder.get(environmentFromApplicationYaml())
                .bind("commerce.guide.agent", GuideAgentProperties.class)
                .get();

        assertThat(properties.getPolicies())
                .extracting(GuideAgentPolicy::getPolicyId)
                .containsExactly(
                        "general-shopping-v1",
                        "broad-category-purchase-v1",
                        "compare-products-v1",
                        "after-sales-v1"
                );
        assertThat(properties.normalizedPolicies())
                .allSatisfy(policy -> {
                    assertThat(policy.getPromptLocation()).isEqualTo("classpath:prompts/guide/planner/default.md");
                    assertThat(policy.getPromptVersion()).isEqualTo("guide-agent-planner-default-v1");
                    assertThat(policy.getAllowedActions()).isNotEmpty();
                    assertThat(policy.getActionTransitions()).isNotEmpty();
                });
    }

    @Test
    void applicationYamlDefinesFallbackPolicyDefaults() throws IOException {
        GuideFallbackProperties properties = Binder.get(environmentFromApplicationYaml())
                .bind("commerce.guide.agent.fallback", GuideFallbackProperties.class)
                .get();

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getPolicyVersion()).isEqualTo("fallback-v1");
        assertThat(properties.getMaxAttempts()).isEqualTo(4);
        assertThat(properties.isLlmEnabled()).isFalse();
        assertThat(properties.normalizedPolicy().getRules())
                .extracting(rule -> rule.getFailureType().name())
                .contains("PLANNER_UNAVAILABLE", "EMPTY_CANDIDATES", "ANSWER_GENERATION_FAILED", "MAX_STEPS_REACHED");
    }

    @Test
    void applicationYamlDefinesClarificationPolicies() throws IOException {
        GuideClarificationProperties properties = Binder.get(environmentFromApplicationYaml())
                .bind("commerce.guide.clarification", GuideClarificationProperties.class)
                .get();

        assertThat(properties.isLlmEnabled()).isFalse();
        assertThat(properties.getPromptLocation()).isEqualTo("classpath:prompts/guide/clarification-strategy-system.md");
        assertThat(properties.getPolicies())
                .extracting(policy -> policy.getPolicyId())
                .contains("guide-clarification-phone-v1", "guide-clarification-laptop-v1");
        assertThat(properties.policyFor("phone").getRecommendedSlots())
                .contains("budget", "scenario", "brandPreference");
    }

    private StandardEnvironment environmentFromApplicationYaml() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        List<PropertySource<?>> sources = loader.load(
                "application-yaml",
                new ClassPathResource("application.yaml")
        );
        for (PropertySource<?> source : sources) {
            environment.getPropertySources().addFirst(source);
        }
        return environment;
    }
}
