import type { GuideAgentTimelineItem } from '../../../types';

interface ToolCallInspectorProps {
  step: GuideAgentTimelineItem;
  canInspect: boolean;
}

export function ToolCallInspector({ step, canInspect }: ToolCallInspectorProps) {
  if (!canInspect) {
    return null;
  }
  return (
    <details className="guide-tool-inspector">
      <summary>工具详情</summary>
      {step.thought && (
        <div>
          <strong>Thought</strong>
          <p>{step.thought}</p>
        </div>
      )}
      {step.arguments && (
        <div>
          <strong>Arguments</strong>
          <pre>{JSON.stringify(step.arguments, null, 2)}</pre>
        </div>
      )}
      {step.error && (
        <div>
          <strong>Error</strong>
          <p>{step.error}</p>
        </div>
      )}
    </details>
  );
}
