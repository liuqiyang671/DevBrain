package edu.cqupt.devbrain.rag.core.intent;

/**
 * 歧义引导决策。
 */
public class GuidanceDecision {

    private static final GuidanceDecision NONE = new GuidanceDecision(false, null);

    private final boolean prompt;

    private final String promptText;

    private GuidanceDecision(boolean prompt, String promptText) {
        this.prompt = prompt;
        this.promptText = promptText;
    }

    /** 无需引导的决策实例。 */
    public static GuidanceDecision none() {
        return NONE;
    }

    /** 创建需要引导用户澄清的决策实例。 */
    public static GuidanceDecision prompt(String promptText) {
        return new GuidanceDecision(true, promptText);
    }

    public boolean isPrompt() {
        return prompt;
    }

    public String getPrompt() {
        return promptText;
    }
}
