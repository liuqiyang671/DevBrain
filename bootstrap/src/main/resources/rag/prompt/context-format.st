--- section: kb-evidence ---
<documents>
{kbContext}
</documents>

--- section: mcp-evidence ---
<tool-data>
{mcpContext}
</tool-data>

--- section: single-question ---
<question>{question}</question>

--- section: multi-questions ---
<questions>
{questions}
</questions>

# kb-section
<kb-section intent-id="$intentId$" intent-name="$intentName$" collection="$collectionName$">
$chunks$
</kb-section>

# mcp-section
<mcp-section intent-id="$intentId$" intent-name="$intentName$" tool-id="$toolId$">
$toolResults$
</mcp-section>
