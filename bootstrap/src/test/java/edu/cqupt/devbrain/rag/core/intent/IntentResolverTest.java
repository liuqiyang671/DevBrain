package edu.cqupt.devbrain.rag.core.intent;

import edu.cqupt.devbrain.rag.core.rewrite.RewriteResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntentResolverTest {

    private final StubIntentClassifier classifier = new StubIntentClassifier();
    private final IntentProperties properties = new IntentProperties();
    private final IntentResolver resolver = new IntentResolver(classifier, Runnable::run, properties);

    @Test
    void resolveShouldClassifySubQuestionsFilterByScoreAndLimitCount() {
        properties.setMinScore(0.35);
        properties.setMaxCount(3);
        classifier.results = List.of(
                score("kb-1", "KB", 0.95),
                score("mcp-1", "MCP", 0.82),
                score("sys-1", "SYSTEM", 0.60),
                score("low-1", "KB", 0.20)
        );

        List<SubQuestionIntent> result = resolver.resolve(new RewriteResult(
                "招聘和薪资",
                List.of("招聘流程是什么", "薪资怎么算")
        ));

        assertEquals(2, result.size());
        assertEquals("招聘流程是什么", result.get(0).subQuestion());
        assertEquals(3, result.get(0).nodeScores().size());
        assertEquals("kb-1", result.get(0).nodeScores().get(0).getNode().getId());
        assertEquals("mcp-1", result.get(0).nodeScores().get(1).getNode().getId());
        assertEquals("sys-1", result.get(0).nodeScores().get(2).getNode().getId());
    }

    @Test
    void mergeIntentGroupShouldSeparateMcpAndKbIntents() {
        List<SubQuestionIntent> subIntents = List.of(new SubQuestionIntent("q", List.of(
                score("kb-1", "KB", 0.9),
                score("mcp-1", "MCP", 0.8),
                score("sys-1", "SYSTEM", 0.7)
        )));

        IntentGroup group = resolver.mergeIntentGroup(subIntents);

        assertEquals(1, group.kbIntents().size());
        assertEquals("kb-1", group.kbIntents().get(0).getNode().getId());
        assertEquals(1, group.mcpIntents().size());
        assertEquals("mcp-1", group.mcpIntents().get(0).getNode().getId());
    }

    @Test
    void isSystemOnlyShouldReturnTrueOnlyWhenAllScoresAreSystem() {
        assertTrue(resolver.isSystemOnly(List.of(score("sys-1", "SYSTEM", 0.7))));
        assertFalse(resolver.isSystemOnly(List.of(score("sys-1", "SYSTEM", 0.7), score("kb-1", "KB", 0.6))));
        assertFalse(resolver.isSystemOnly(List.of()));
    }

    private NodeScore score(String id, String kind, double value) {
        IntentNode node = new IntentNode();
        node.setId(id);
        node.setName(id);
        node.setKind(kind);
        node.setCollectionName(kind.equals("KB") ? "collection-" + id : null);
        node.setMcpToolId(kind.equals("MCP") ? "tool-" + id : null);
        return new NodeScore(node, value);
    }

    private static final class StubIntentClassifier implements IntentClassifier {

        private List<NodeScore> results = List.of();

        @Override
        public List<NodeScore> classifyTargets(String question) {
            return results;
        }
    }
}
