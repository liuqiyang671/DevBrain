import type { GuideAgentTimelineItem } from '../../../types';
import { AgentStepItem } from './AgentStepItem';

interface AgentTimelinePanelProps {
  timeline: GuideAgentTimelineItem[];
  activeStepNo: number | null;
  canInspect: boolean;
}

export function AgentTimelinePanel({ timeline, activeStepNo, canInspect }: AgentTimelinePanelProps) {
  if (timeline.length === 0) {
    return <div className="guide-panel-empty">Agent 步骤会随本轮导购实时出现。</div>;
  }
  return (
    <div className="guide-agent-timeline">
      {timeline.map((step) => (
        <AgentStepItem
          key={step.id}
          step={step}
          active={activeStepNo === step.stepNo}
          canInspect={canInspect}
        />
      ))}
    </div>
  );
}
