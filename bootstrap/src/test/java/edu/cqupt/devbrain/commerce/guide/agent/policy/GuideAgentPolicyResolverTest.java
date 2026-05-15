package edu.cqupt.devbrain.commerce.guide.agent.policy;

import edu.cqupt.devbrain.commerce.guide.agent.GuideAgentProperties;
import edu.cqupt.devbrain.commerce.guide.domain.GuideState;
import edu.cqupt.devbrain.commerce.guide.domain.GuideTurnInput;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GuideAgentPolicyResolverTest {

    @Test
    void resolvesAfterSalesPolicyFromUserMessageScene() {
        GuideAgentProperties properties = GuideAgentProperties.defaults();
        properties.setPolicies(List.of(
                policy("general-v1", "general_shopping", 0, 100),
                policy("after-sales-v1", "after_sales", 0, 100)
        ));
        GuideAgentPolicyResolver resolver = new GuideAgentPolicyResolver(properties);

        GuideAgentPolicy policy = resolver.resolve(
                GuideTurnInput.builder().userId("u1").userText("这台手机还能保修吗").build(),
                GuideState.builder().userText("这台手机还能保修吗").build(),
                null
        );

        assertEquals("after-sales-v1", policy.getPolicyId());
        assertEquals("after_sales", policy.getScene());
    }

    @Test
    void resolvesDifferentPolicyVersionsByUserBucket() {
        GuideAgentProperties properties = GuideAgentProperties.defaults();
        properties.setPolicies(List.of(
                policy("general-v1", "general_shopping", 0, 50),
                policy("general-v2", "general_shopping", 50, 100)
        ));
        GuideAgentPolicyResolver resolver = new GuideAgentPolicyResolver(properties);
        String lowBucketUser = userForBucketRange(0, 50);
        String highBucketUser = userForBucketRange(50, 100);

        GuideAgentPolicy low = resolver.resolve(
                GuideTurnInput.builder().userId(lowBucketUser).userText("推荐手机").build(),
                GuideState.builder().userText("推荐手机").build(),
                null
        );
        GuideAgentPolicy high = resolver.resolve(
                GuideTurnInput.builder().userId(highBucketUser).userText("推荐手机").build(),
                GuideState.builder().userText("推荐手机").build(),
                null
        );

        assertEquals("general-v1", low.getPolicyId());
        assertEquals("general-v2", high.getPolicyId());
    }

    @Test
    void resolvesBroadPurchaseSceneFromPolicyKeywordsWithoutJavaCategoryTerms() {
        GuideAgentProperties properties = GuideAgentProperties.defaults();
        GuideAgentPolicy broad = policy("broad-ring-v1", "broad_category_purchase", 0, 100);
        broad.setSceneKeywords(List.of("智能戒指", "购买", "推荐"));
        properties.setPolicies(List.of(
                policy("general-v1", "general_shopping", 0, 100),
                broad
        ));
        GuideAgentPolicyResolver resolver = new GuideAgentPolicyResolver(properties);

        GuideAgentPolicy policy = resolver.resolve(
                GuideTurnInput.builder().userId("u1").userText("推荐一个智能戒指").build(),
                GuideState.builder().userText("推荐一个智能戒指").build(),
                null
        );

        assertEquals("broad-ring-v1", policy.getPolicyId());
    }

    @Test
    void resolvesSpecificAfterSalesPolicyBeforeBroadCategoryKeyword() {
        GuideAgentProperties properties = GuideAgentProperties.defaults();
        GuideAgentPolicy broad = policy("broad-v1", "broad_category_purchase", 0, 100);
        broad.setSceneKeywords(List.of("手机", "推荐"));
        GuideAgentPolicy afterSales = policy("after-sales-v1", "after_sales", 0, 100);
        afterSales.setSceneKeywords(List.of("保修"));
        properties.setPolicies(List.of(
                policy("general-v1", "general_shopping", 0, 100),
                broad,
                afterSales
        ));
        GuideAgentPolicyResolver resolver = new GuideAgentPolicyResolver(properties);

        GuideAgentPolicy policy = resolver.resolve(
                GuideTurnInput.builder().userId("u1").userText("这台手机还能保修吗").build(),
                GuideState.builder().userText("这台手机还能保修吗").build(),
                null
        );

        assertEquals("after-sales-v1", policy.getPolicyId());
    }

    private static GuideAgentPolicy policy(String policyId, String scene, int bucketStart, int bucketEnd) {
        return GuideAgentPolicy.builder()
                .policyId(policyId)
                .version(policyId.substring(policyId.lastIndexOf('-') + 1))
                .scene(scene)
                .promptVersion("guide-agent-planner-default-v1")
                .promptLocation("classpath:prompts/guide/planner/default.md")
                .allowedActions(List.of("understand_intent", "search_products", "rank_products", "final_answer"))
                .maxSteps(4)
                .bucketStart(bucketStart)
                .bucketEnd(bucketEnd)
                .build();
    }

    private static String userForBucketRange(int startInclusive, int endExclusive) {
        for (int i = 0; i < 1_000; i++) {
            String userId = "user-" + i;
            int bucket = GuideAgentPolicyResolver.bucketOf(userId);
            if (bucket >= startInclusive && bucket < endExclusive) {
                return userId;
            }
        }
        throw new IllegalStateException("没有找到匹配测试桶的用户 ID");
    }
}
